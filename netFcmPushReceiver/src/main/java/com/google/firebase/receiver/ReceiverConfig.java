/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver;

import com.google.firebase.receiver.checkin.AppType;
import com.google.firebase.receiver.credentials.PushReceiverCredentials;
import com.google.firebase.receiver.gcm.mcs.McsUtils;
import com.google.firebase.receiver.gcm.persistence.AbstractNotificationIdPersistor;

public class ReceiverConfig
{
	private PushReceiverCredentials credentials;
	private String appType;
	private String subscribeUrl;
	private String serverKey;
	private AbstractNotificationIdPersistor idPersistor;

	public ReceiverConfig(ReceiverConfig config, PushReceiverCredentials credentials)
	{
		this(credentials, config.getAppType(), config.getSubscribeUrl(), config.getServerKey(), config.getIdPersistor());
	}

	public ReceiverConfig(String appType, String subscribeUrl, String serverKey, AbstractNotificationIdPersistor idPersistor)
	{
		this(null, appType, subscribeUrl, serverKey, idPersistor);
	}

	public ReceiverConfig(AppType appType, String subscribeUrl, String serverKey, AbstractNotificationIdPersistor idPersistor)
	{
		this(null, appType.getType(), subscribeUrl, serverKey, idPersistor);
	}

	public ReceiverConfig(PushReceiverCredentials credentials,
						  String appType,
						  String subscribeUrl,
						  String serverKey,
						  AbstractNotificationIdPersistor idPersistor)
	{
		this.appType = appType;
		this.credentials = credentials;
		this.subscribeUrl = subscribeUrl;
		this.serverKey = McsUtils.crippleUrlBase64(serverKey);

		this.idPersistor = idPersistor;
	}

	public PushReceiverCredentials getCredentials()
	{
		return credentials;
	}

	public PushReceiverCredentials getOrCreateCredentials() throws Exception
	{
		if(credentials == null)
			credentials = new PushReceiverCredentials(true);

		return credentials;
	}

	public String getAppType()
	{
		return appType;
	}

	public String getSubscribeUrl()
	{
		return subscribeUrl;
	}

	/**
	 * ServerKey is needed only for registration, when we already have valid tokens, it is not needed.
	 * @return
	 */
	public String getServerKey()
	{
		return serverKey;
	}

	public AbstractNotificationIdPersistor getIdPersistor()
	{
		return idPersistor;
	}
}
