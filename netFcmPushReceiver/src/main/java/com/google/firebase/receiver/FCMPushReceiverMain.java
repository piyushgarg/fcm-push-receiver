/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver;

import com.google.firebase.fcm.mcs.Mcs;
import com.google.firebase.receiver.checkin.AppType;
import com.google.firebase.receiver.credentials.Token;
import com.google.firebase.receiver.gcm.persistence.FileNotificationIdPersistor;

import org.json.JSONObject;

import java.io.File;
import java.util.Date;

public class FCMPushReceiverMain
{
	public static void main(String[] args) throws Exception
	{
		File credsFolder = new File("./.creds");
		credsFolder.mkdir();

		File credsFile = new File(credsFolder, "creds.json");
		File notificationIds = new File(credsFolder, "notids.json");

		ReceiverConfig config = new ReceiverConfig(AppType.CHROME_LINUX,
					"", // replace with host url
					"", // replace with server key
					new FileNotificationIdPersistor(notificationIds));

		PushReceiver pushReceiver = new PushReceiver(credsFile, config)
		{
			@Override
			protected void onNewToken(Token token)
			{
				System.out.println("Firebase token: " + token);

				JSONObject json = token.serialize();

				System.out.println("var mcsInfo = " + json.toString(4));

				System.out.printf(
						"token.setEndpoint(\"%s\");\n" +
						"token.setP256dh(\"%s\");\n" +
						"token.setAuth(\"%s\");\n", token.getEndpoint(), token.getP256dh(), token.getAuthSecret());
			}

			@Override
			protected void onNotificationReceived(Mcs.DataMessageStanza message, JSONObject json)
			{
				System.out.println("Received notification date: " + new Date(message.getSent()) + ", from: " + message.getFrom());
				System.out.println(json.toString(4));
			}
		};

		pushReceiver.start();
	}
}
