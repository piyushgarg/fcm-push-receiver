/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.credentials;

import com.google.gms.checkin.Checkin;

import org.json.JSONObject;

public class CheckinCredentials
{
	private static final String SUBTYPE = "subtype";
	private static final String TOKEN = "token";
	private static final String ANDROID_ID = "aid";
	private static final String SECURITY_TOKEN = "sec_token";
	private static final String SERVER_KEY = "server_key";

	private String subtype;
	private String endpointToken;
	private String serverKey;
	private long androidId;
	private long securityToken;

	public CheckinCredentials(String subtype,
							  String endpointToken,
							  String serverKey,
							  Checkin.AndroidCheckinResponse checkinResponse)
	{
		this(subtype, endpointToken, serverKey, checkinResponse.getAndroidId(), checkinResponse.getSecurityToken());
	}

	private CheckinCredentials(String subtype,
							   String endpointToken,
							   String serverKey,
							   long androidId,
							   long securityToken)
	{
		this.subtype = subtype;
		this.endpointToken = endpointToken;
		this.serverKey = serverKey;
		this.androidId = androidId;
		this.securityToken = securityToken;
	}

	public String getSubtype()
	{
		return subtype;
	}

	public String getEndpointToken()
	{
		return endpointToken;
	}

	public String getServerKey()
	{
		return serverKey;
	}

	public long getAndroidId()
	{
		return androidId;
	}

	public long getSecurityToken()
	{
		return securityToken;
	}

	public String getFcmEndpoint()
	{
		return String.format("https://fcm.googleapis.com/fcm/send/%s", endpointToken);
	}

	public static CheckinCredentials from(JSONObject json)
	{
		if(json == null)
			return null;

		return new CheckinCredentials(
				json.optString(SUBTYPE),
				json.optString(TOKEN),
				json.optString(SERVER_KEY),
				json.optLong(ANDROID_ID),
				json.optLong(SECURITY_TOKEN)
		);
	}

	public JSONObject serialize()
	{
		JSONObject json = new JSONObject();
		json.putOpt(SUBTYPE, subtype);
		json.putOpt(TOKEN, endpointToken);
		json.putOpt(SERVER_KEY, serverKey);
		json.putOpt(ANDROID_ID, androidId);
		json.putOpt(SECURITY_TOKEN, securityToken);

		return json;
	}
}
