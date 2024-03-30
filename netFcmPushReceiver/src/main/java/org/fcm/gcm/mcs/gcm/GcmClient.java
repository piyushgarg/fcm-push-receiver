/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package org.fcm.gcm.mcs.gcm;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.MessageLite;
import mcs_proto.Mcs;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.fcm.gcm.mcs.McsConstants;
import org.fcm.gcm.mcs.McsInputStream;
import org.fcm.gcm.mcs.McsOutputStream;
import org.fcm.gcm.mcs.McsUtils;
import org.fcm.gcm.mcs.base64.Base64;
import org.fcm.gcm.mcs.model.FcmKeys;
import org.fcm.gcm.mcs.gcm.persistence.AbstractNotificationIdPersistor;
import org.fcm.gcm.mcs.model.Application;
import org.fcm.gcm.mcs.model.Checkin;
import org.fcm.gcm.mcs.util.ecdh.Keys;
import org.fcm.gcm.mcs.util.ece.HttpEce;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.fcm.gcm.mcs.checkin.CheckinClient.CHROME_VERSION;
import static org.fcm.gcm.mcs.gcm.mcs.McsConstants.MCS_HEARTBEAT_ACK_TAG;
import static org.fcm.gcm.mcs.gcm.mcs.McsConstants.MCS_HEARTBEAT_PING_TAG;

public abstract class GcmClient implements Runnable {
    private static final int MAX_LOGIN_RETRY_COUNT = 5;
    private static final int RECONNECT_TIMEOUT = 15;
    public static final byte kMCSVersion = 41;
    SSLSocket socket;
    long lastHeartbeatPingElapsedRealtime = -1;
    private final Application applicationConfig;
    private final String addr;
    private final int port;
    private final AbstractNotificationIdPersistor idPersistor;

    private int reconnectCount = 0;
    private int loginRetryCount = 0;
    McsInputStream inputStream;
    McsOutputStream outputStream;
    private Mcs.LoginResponse loginResponse;

    public GcmClient(Application applicationConfig, String addr, int port, AbstractNotificationIdPersistor idPersistor) {
        this.applicationConfig = applicationConfig;
        this.addr = addr;
        this.port = port;
        this.idPersistor = idPersistor;
        idPersistor.load();
    }

    public void start() {
        System.out.println("Connecting to GCM...");
        this.socket = null;
        this.outputStream = null;
        this.inputStream = null;
        this.loginResponse = null;
        Executors.newSingleThreadExecutor().execute(this);
    }

    @Override
    public void run() {
        if (applicationConfig == null)
            throw new IllegalStateException("CheckinCredentials not set.");

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.socket = (SSLSocket) factory.createSocket(addr, port);
            socket.setKeepAlive(true);

            inputStream = new McsInputStream(socket.getInputStream());
            outputStream = new McsOutputStream(socket.getOutputStream());

            Checkin checkin = applicationConfig.getGcm();
            sendLogin(checkin.getAndroidId(), checkin.getSecurityToken());

            McsInputStream.Message message;
            lastHeartbeatPingElapsedRealtime = System.currentTimeMillis();

            while (socket != null && !socket.isClosed() && (message = inputStream.read()) != null) {
                MessageLite messageLite = message.protobuf();
                System.out.println("tag:" + message.getTag() + "-Received messageLite: " + messageLite);
                onMessage(message.getTag(), messageLite);
            }
        } catch (Exception e) {
            e.printStackTrace();
            restart();
        }
    }

    void restart() {
        System.out.println("restarting = ");
        IOUtils.closeQuietly(socket, inputStream);
        reconnectCount++;
        int reconnectTimeout = RECONNECT_TIMEOUT;
        if (reconnectTimeout > 300) reconnectTimeout = 5;
        try {
            System.out.println("Reconnecting socket in: " + reconnectTimeout + " seconds");
            Thread.sleep(reconnectTimeout * 1000);
            start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void onMessage(int tag, MessageLite message) throws Exception {
        if (tag == McsConstants.MCS_LOGIN_RESPONSE_TAG) {
            onLoginResponse((Mcs.LoginResponse) message);
        } else if (tag == McsConstants.MCS_DATA_MESSAGE_STANZA_TAG) {
            onDataMessage((Mcs.DataMessageStanza) message);
        } else if (tag == McsConstants.MCS_CLOSE_TAG) {
            System.out.println("Connection closed by remote party.");
        } else if (tag == McsConstants.MCS_IQ_STANZA_TAG) {
            // ignore
        } else if (tag == McsConstants.MCS_HEARTBEAT_PING_TAG) {
            handleHeartbeatPing((Mcs.HeartbeatPing) message);
        } else if (tag == McsConstants.MCS_HEARTBEAT_ACK_TAG) {
            handleHeartAck((Mcs.HeartbeatAck) message);
        } else {
            System.out.println("Unknown message: " + message);
        }
    }

    private void handleHeartAck(Mcs.HeartbeatAck ack) {
        lastHeartbeatPingElapsedRealtime = System.currentTimeMillis();
    }

    private void handleHeartbeatPing(Mcs.HeartbeatPing ping) throws IOException {
        Mcs.HeartbeatAck.Builder ack = Mcs.HeartbeatAck.newBuilder()
                .setStatus(ping.getStatus())
                .setLastStreamIdReceived(ping.getStreamId());
        if (inputStream.newStreamIdAvailable()) {
            ack.setLastStreamIdReceived(inputStream.getStreamId());
        }
        send(MCS_HEARTBEAT_ACK_TAG, ack.build());

    }

    private void onLoginResponse(Mcs.LoginResponse message) throws Exception {
        if (message.hasError()) {
            System.out.println("An error occurred while logging in: " + message.getError().getMessage());

            if (loginRetryCount < MAX_LOGIN_RETRY_COUNT) {
                loginRetryCount++;
                IOUtils.closeQuietly(socket, inputStream);

                System.out.println("Reconnecting after failed login...");
                Thread.sleep(loginRetryCount * 2000);
                restart();
            }
        }
        this.loginResponse = message;
        System.out.println("We are in...");
        new HeartBeatScheduler(socket, this);

    }

    private void onDataMessage(Mcs.DataMessageStanza message) throws Exception {
        String persistentId = message.getPersistentId();
        if (idPersistor.contains(persistentId))
            return;
        try {
            String json = decryptMessage(message);
            if (json == null)
                return;
            idPersistor.add(persistentId);
            onNotificationReceived(message, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void onNotificationReceived(Mcs.DataMessageStanza message, String json);

    private String decryptMessage(Mcs.DataMessageStanza message) {
        try {
            Map<String, String> cryptoKeyData = McsUtils.getAppData(message, "crypto-key");
            Map<String, String> encryptionData = McsUtils.getAppData(message, "encryption");

            String dh = cryptoKeyData.get("dh");
            String salt = encryptionData.get("salt");

            FcmKeys fcmKeys = applicationConfig.getKeys();

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
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void sendLogin(long authId, long authToken) throws IOException {
        String hexAndroidId = Long.toHexString(authId);

        String authIdStr = Long.toString(authId);
        String authTokenStr = Long.toString(authToken);

        Mcs.LoginRequest request = Mcs.LoginRequest.newBuilder()
                .setAdaptiveHeartbeat(false)
                .setAuthService(Mcs.LoginRequest.AuthService.ANDROID_ID)
                .setAuthToken(authTokenStr)
                .setId("chrome-" + CHROME_VERSION)
                .setDomain("mcs.android.com")
                .setDeviceId("android-" + hexAndroidId)
                .setNetworkType(1)
                .setResource(authIdStr)
                .setUser(authIdStr)
                .setUseRmq2(true)
                .addSetting(Mcs.Setting.newBuilder().setName("new_vc").setValue("1").build())
                .addAllReceivedPersistentId(idPersistor)
                .build();

        outputStream.write(McsConstants.MCS_LOGIN_REQUEST_TAG, request);
    }

    void send(int tag, GeneratedMessage message) throws IOException {
        outputStream.write(tag, message);
    }

}

class HeartBeatScheduler extends Thread {
    private Socket socket;
    private GcmClient gcmClient;
    public HeartBeatScheduler(Socket socket, GcmClient gcmClient) {
        this.socket = socket;
        this.gcmClient = gcmClient;
        this.start();
    }

    @Override
    public void run() {
        while (!socket.isClosed()) {
            try {
                if ((System.currentTimeMillis() - gcmClient.lastHeartbeatPingElapsedRealtime) > (3 * 60 * 1000)) {
                    IOUtils.close(gcmClient.socket, gcmClient.inputStream);
                    break;
                } else {
                    Mcs.HeartbeatAck.Builder ping = Mcs.HeartbeatAck.newBuilder();
                    if (gcmClient.inputStream != null && gcmClient.inputStream.newStreamIdAvailable())
                        ping.setLastStreamIdReceived(gcmClient.inputStream.getStreamId());
                    gcmClient.send(MCS_HEARTBEAT_PING_TAG, ping.build());
                }
                Thread.sleep(Duration.ofMinutes(1).toMillis());
            } catch (Exception e) {
                e.printStackTrace();
                // heartbeat schedule could not write the heart beat
                // and needs to be restarted
                gcmClient.restart();
                throw new RuntimeException(e);
            }
        }
        System.out.println("+ heartbeat scheduler done = ");
    }
}