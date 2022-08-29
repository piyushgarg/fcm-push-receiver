/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.credentials;

import org.json.JSONObject;

public class PushReceiverCredentials
{
	public interface SaveCallback
	{
		void saveCredentials(PushReceiverCredentials credentials);
	}

	private static final String CHECKIN_CREDENTIALS = "checkin";
	private static final String FCM_KEYS = "fcm";

	private CheckinCredentials checkinCredentials;
	private FcmKeys fcmKeys;
	private SaveCallback saveCallback;

	public PushReceiverCredentials(boolean generateKeys) throws Exception
	{
		if(generateKeys)
			setFcmKeys(FcmKeys.generateKeys());
	}

	public static PushReceiverCredentials from(String json) throws Exception
	{
		if(json != null && json.length() > 0)
			return from(new JSONObject(json));

		return null;
	}

	public static PushReceiverCredentials from(JSONObject json) throws Exception
	{
		if(json == null)
			return null;

		PushReceiverCredentials credentials = new PushReceiverCredentials(false);

		credentials.setCheckinCredentials(
				CheckinCredentials.from(json.optJSONObject(CHECKIN_CREDENTIALS)));

		credentials.setFcmKeys(
				FcmKeys.from(json.optJSONObject(FCM_KEYS)));

		return credentials;
	}

	public CheckinCredentials getCheckinCredentials()
	{
		return checkinCredentials;
	}

	public FcmKeys getFcmKeys()
	{
		return fcmKeys;
	}

	public void setCheckinCredentials(CheckinCredentials checkinCredentials)
	{
		this.checkinCredentials = checkinCredentials;
		save();
	}

	public void setFcmKeys(FcmKeys fcmKeys)
	{
		this.fcmKeys = fcmKeys;
		save();
	}

	public void setSaveCallback(SaveCallback saveCallback)
	{
		this.saveCallback = saveCallback;
	}

	private void save()
	{
		if(saveCallback != null)
			saveCallback.saveCredentials(this);
	}

	public JSONObject serialize()
	{
		JSONObject json = new JSONObject();

		if(checkinCredentials != null)
		json.putOpt(CHECKIN_CREDENTIALS, checkinCredentials.serialize());

		if(fcmKeys != null)
			json.putOpt(FCM_KEYS, fcmKeys.serialize());

		return json;
	}
}
