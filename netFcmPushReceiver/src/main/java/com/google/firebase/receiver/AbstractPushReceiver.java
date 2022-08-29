/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver;

import com.google.firebase.fcm.mcs.Mcs;
import com.google.firebase.receiver.checkin.CheckinClient;
import com.google.firebase.receiver.credentials.CheckinCredentials;
import com.google.firebase.receiver.credentials.FcmKeys;
import com.google.firebase.receiver.credentials.PushReceiverCredentials;
import com.google.firebase.receiver.credentials.Token;
import com.google.firebase.receiver.gcm.GcmClient;

import org.json.JSONObject;

public abstract class AbstractPushReceiver extends Thread implements PushReceiverCredentials.SaveCallback
{
	private final ReceiverConfig config;
	private CheckinClient checkinClient;
	private GcmClient gcmClient;

	public AbstractPushReceiver(ReceiverConfig config) throws Exception
	{
		this.config = config;

		PushReceiverCredentials credentials = config.getOrCreateCredentials();

		credentials.setSaveCallback(this);

		this.checkinClient = new CheckinClient(config);

		this.gcmClient = new GcmClient(credentials, "mtalk.google.com", 5228, config.getIdPersistor())
		{
			@Override
			protected void onNotificationReceived(Mcs.DataMessageStanza message, JSONObject json)
			{
				AbstractPushReceiver.this.onNotificationReceived(message, json);
			}
		};
	}

	@Override
	public void run()
	{
		super.run();

		try
		{
			FcmKeys fcmKeys = config.getOrCreateCredentials().getFcmKeys();

			CheckinCredentials checkinCredentials = checkinClient.loginOrRegister();

			onNewToken(new Token(checkinCredentials.getFcmEndpoint(), fcmKeys.getPublicKey(), fcmKeys.getAuthSecret()));

			Thread.sleep(2000);

			gcmClient.start();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public abstract void saveCredentials(PushReceiverCredentials credentials);

	protected abstract void onNotificationReceived(Mcs.DataMessageStanza message, JSONObject json);

	protected abstract void onNewToken(Token token);

	public void close() throws Exception
	{
		if(checkinClient != null)
			this.checkinClient = null;

		if(gcmClient != null)
		{
			gcmClient.close();
			this.gcmClient = null;
		}
	}
}
