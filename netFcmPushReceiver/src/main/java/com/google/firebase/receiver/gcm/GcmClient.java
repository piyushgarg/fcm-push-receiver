/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.gcm;

import com.google.firebase.fcm.mcs.Mcs;
import com.google.firebase.receiver.base64.Base64;
import com.google.firebase.receiver.credentials.CheckinCredentials;
import com.google.firebase.receiver.credentials.FcmKeys;
import com.google.firebase.receiver.credentials.PushReceiverCredentials;
import com.google.firebase.receiver.gcm.mcs.McsInputStream;
import com.google.firebase.receiver.gcm.mcs.McsOutputStream;
import com.google.firebase.receiver.gcm.mcs.McsProtoTag;
import com.google.firebase.receiver.gcm.mcs.McsUtils;
import com.google.firebase.receiver.gcm.persistence.AbstractNotificationIdPersistor;
import com.google.firebase.receiver.util.ecdh.Keys;
import com.google.firebase.receiver.util.ece.HttpEce;
import com.google.protobuf.MessageLite;

import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketException;
import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static com.google.firebase.receiver.gcm.mcs.McsProtoTag.kLoginRequestTag;


public abstract class GcmClient implements Runnable
{
	private static final int MAX_LOGIN_RETRY_COUNT = 2;
	private static final int RECONNECT_TIMEOUT = 15;

	public static final byte kMCSVersion = 41;

	private SSLSocket socket;

	private final PushReceiverCredentials credentials;
	private final String addr;
	private final int port;

	private final AbstractNotificationIdPersistor idPersistor;

	private int reconnectCount = 0;
	private int loginRetryCount = 0;
	private boolean isClosed = false;

	public GcmClient(PushReceiverCredentials credentials, String addr, int port, AbstractNotificationIdPersistor idPersistor)
	{
		this.credentials = credentials;
		this.addr = addr;
		this.port = port;
		this.idPersistor = idPersistor;
		idPersistor.load();
	}

	public void start()
	{
		//System.out.println("Connecting to GCM...");

		this.socket = null;

		Executors.newSingleThreadExecutor().execute(this);
	}

	@Override
	public void run()
	{
		if (credentials == null)
			throw new IllegalStateException("CheckinCredentials not set.");

		try
		{
			SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
			this.socket = (SSLSocket) factory.createSocket(addr, port);
			socket.setKeepAlive(true);

			McsInputStream is = new McsInputStream(socket.getInputStream());
			McsOutputStream os = new McsOutputStream(socket.getOutputStream());

			CheckinCredentials checkinCredentials = credentials.getCheckinCredentials();

			sendLogin(os, checkinCredentials.getAndroidId(), checkinCredentials.getSecurityToken());

			McsInputStream.Message m;

			while (socket != null && !socket.isClosed() && (m = is.read()) != null)
			{
				MessageLite message = m.protobuf();

				//System.out.println("Received message: " + message);

				onMessage(m.getTag(), message);
			}
		}
		catch (SocketException e)
		{
			e.printStackTrace();

			if(!isClosed)
			{
				reconnectCount++;
				int reconnectTimeout = RECONNECT_TIMEOUT * reconnectCount;

				try
				{
					System.out.println("Reconnecting socket in: " + reconnectTimeout + " seconds");

					Thread.sleep(reconnectTimeout * 1000);
					start();
				}
				catch (InterruptedException e1)
				{
					e1.printStackTrace();
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private void onMessage(int tag, MessageLite message) throws Exception
	{
		if(tag == McsProtoTag.kLoginResponseTag)
		{
			onLoginMessage((Mcs.LoginResponse)message);
		}
		else if(tag == McsProtoTag.kDataMessageStanzaTag)
		{
			onDataMessage((Mcs.DataMessageStanza)message);
		}
		else if(tag == McsProtoTag.kCloseTag)
		{
			System.out.println("Connection closed by remote party.");
		}
		else if(tag == McsProtoTag.kIqStanzaTag)
		{
			// ignore
		}
		else
		{
			//System.out.println("Unknown message: " + message);
		}
	}

	private void onLoginMessage(Mcs.LoginResponse message) throws Exception
	{
		if(message.hasError())
		{
			System.out.println("An error occured while loggin in: " + message.getError().getMessage());

			if(loginRetryCount < MAX_LOGIN_RETRY_COUNT)
			{
				loginRetryCount++;
				close();

				System.out.println("Reconnecting after failed login...");
				Thread.sleep(loginRetryCount * 2000);

				start();
			}

			return;
		}

		//System.out.println("We are logged in...");
	}

	private void onDataMessage(Mcs.DataMessageStanza message) throws Exception
	{
		String persistentId = message.getPersistentId();

		if(idPersistor.contains(persistentId))
			return;

		String json = decryptMessage(message);

		if(json == null)
			return;

		idPersistor.add(persistentId);

		onNotificationReceived(message, new JSONObject(json));
	}

	protected abstract void onNotificationReceived(Mcs.DataMessageStanza message, JSONObject json);

	private String decryptMessage(Mcs.DataMessageStanza message)
	{
		try
		{
			Map<String, String> cryptoKeyData = McsUtils.getAppData(message, "crypto-key");
			Map<String, String> encryptionData = McsUtils.getAppData(message, "encryption");

			String dh = cryptoKeyData.get("dh");
			String salt = encryptionData.get("salt");

			FcmKeys fcmKeys = credentials.getFcmKeys();

			ECPublicKey publicKey = Keys.loadPublicKey(fcmKeys.getPrivateKey()); // derive public key from private
			KeyPair keyPair = new KeyPair(publicKey, fcmKeys.getPrivateKey());

			String keyId = "1";

			HttpEce.Params params = new HttpEce.Params();

			params.keyId = keyId;
			params.dh = Keys.loadPublicKey(Base64.getUrlDecoder().decode(dh));
			params.salt = Base64.getUrlDecoder().decode(salt);
			params.authSecret = Base64.getUrlDecoder().decode(fcmKeys.getAuthSecret());

			HttpEce httpEece = new HttpEce(false);
			httpEece.saveKey(keyId, keyPair, "P-256");

			byte[] decrypted = httpEece.decrypt(message.getRawData().toByteArray(), params);

			return new String(decrypted);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	private void sendLogin(McsOutputStream os, long authId, long authToken) throws IOException
	{
		String hexAndroidId = Long.toHexString(authId);

		String authIdStr = Long.toString(authId);
		String authTokenStr = Long.toString(authToken);

		Mcs.LoginRequest request = Mcs.LoginRequest.newBuilder()
				.setAdaptiveHeartbeat(false)
				.setAuthService(Mcs.LoginRequest.AuthService.ANDROID_ID)
				.setAuthToken(authTokenStr)
				.setId("chrome-63.0.3234.0")
				.setDomain("mcs.android.com")
				.setDeviceId("android-" + hexAndroidId)
				.setNetworkType(1)
				.setResource(authIdStr)
				.setUser(authIdStr)
				.setUseRmq2(true)
				.addSetting(Mcs.Setting.newBuilder().setName("new_vc").setValue("1").build())
				.addAllReceivedPersistentId(idPersistor)
				.build();

		os.write(request, kLoginRequestTag);
	}

	public synchronized void close() throws IOException
	{
		this.isClosed = true;

		if(socket != null)
			socket.close();
	}
}