/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.credentials;

import com.google.firebase.receiver.base64.Base64;
import com.google.firebase.receiver.util.ecdh.Keys;

import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.json.JSONObject;

import java.security.KeyPair;

public class FcmKeys
{
	private static final String PUBLIC_KEY = "pub";
	private static final String PRIVATE_KEY = "pk";
	private static final String AUTH_SECRET = "secret";

	private ECPublicKey publicKey;
	private ECPrivateKey privateKey;
	private String authSecret;

	public FcmKeys(String publicKey, String privateKey, String authSecret) throws Exception
	{
		this(Keys.loadPublicKey(publicKey), Keys.loadPrivateKey(privateKey), authSecret);
	}

	public FcmKeys(ECPublicKey publicKey, ECPrivateKey privateKey, String authSecret)
	{
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.authSecret = authSecret;
	}

	public ECPrivateKey getPrivateKey()
	{
		return privateKey;
	}

	public ECPublicKey getPublicKey()
	{
		return publicKey;
	}

	public String getAuthSecret()
	{
		return authSecret;
	}

	public static FcmKeys from(JSONObject json) throws Exception
	{
		if(json == null)
			return null;

		return new FcmKeys(json.optString(PUBLIC_KEY),
				json.optString(PRIVATE_KEY),
				json.optString(AUTH_SECRET));
	}

	public JSONObject serialize()
	{
		JSONObject json = new JSONObject();
		json.putOpt(PUBLIC_KEY, Keys.base64PublicKey(publicKey));
		json.putOpt(PRIVATE_KEY, Keys.base64PrivateKey(privateKey));
		json.putOpt(AUTH_SECRET, authSecret);

		return json;
	}

	public static FcmKeys generateKeys() throws Exception
	{
		KeyPair keyPair = Keys.generateKeyPair();
		String authSecret = new String(Base64.getEncoder().encode(Keys.generateRandomString(16).getBytes("UTF-8")));

		return new FcmKeys((ECPublicKey) keyPair.getPublic(),
				(ECPrivateKey)keyPair.getPrivate(),
				authSecret);
	}
}
