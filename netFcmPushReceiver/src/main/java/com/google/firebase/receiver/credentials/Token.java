/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.credentials;

import com.google.firebase.receiver.util.ecdh.Keys;

import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.json.JSONObject;

public class Token
{
	private String endpoint;
	private String p256dh;
	private String authSecret;

	public Token(String endpoint, ECPublicKey publicKey, String authSecret)
	{
		this.endpoint = endpoint;
		this.p256dh = Keys.base64PublicKey(publicKey);
		this.authSecret = authSecret;
	}

	public String getEndpoint()
	{
		return endpoint;
	}

	public String getP256dh()
	{
		return p256dh;
	}

	public String getAuthSecret()
	{
		return authSecret;
	}

	@Override
	public String toString()
	{
		return "Token{" +
				"endpoint='" + endpoint + '\'' +
				", p256dh='" + p256dh + '\'' +
				", authSecret='" + authSecret + '\'' +
				'}';
	}

	public JSONObject serialize()
	{
		JSONObject json = new JSONObject();

		json.putOpt("endpoint", endpoint);
		json.putOpt("p256dh", p256dh);
		json.putOpt("auth", authSecret);

		return json;
	}
}
