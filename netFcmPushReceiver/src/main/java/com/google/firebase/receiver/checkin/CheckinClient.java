/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.checkin;

import com.google.firebase.receiver.ReceiverConfig;
import com.google.firebase.receiver.credentials.CheckinCredentials;
import com.google.firebase.receiver.credentials.PushReceiverCredentials;
import com.google.firebase.receiver.util.DefaultOkHttpClient;
import com.google.firebase.receiver.util.ecdh.Keys;
import com.google.gms.checkin.AndroidCheckin;
import com.google.gms.checkin.Checkin;

import java.io.IOException;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CheckinClient
{
	private static final String REGISTER_URL = "https://android.clients.google.com/c2dm/register3";
	private static final String CHECKIN_URL = "https://android.clients.google.com/checkin";

	private final OkHttpClient httpClient = DefaultOkHttpClient.create();// ProxiedOkHttpClient.create();

	private final ReceiverConfig config;

	public CheckinClient(ReceiverConfig config)
	{
		this.config = config;
	}

	public CheckinCredentials loginOrRegister() throws IOException
	{
		PushReceiverCredentials credentials = config.getCredentials();

		CheckinCredentials checkinCredentials = credentials.getCheckinCredentials();

		if (checkinCredentials == null)
		{
			String subtype = String.format("wp:%s#%s", config.getSubscribeUrl(), UUID.randomUUID());

			Checkin.AndroidCheckinResponse checkinResponse = checkin(0, 0);

			String gmsToken = gmsRegister(checkinResponse);

			String endpointToken = webpushRegister(subtype, checkinResponse);

			checkinCredentials = new CheckinCredentials(subtype, endpointToken, config.getServerKey(), checkinResponse);

			credentials.setCheckinCredentials(checkinCredentials);
		}

		return checkinCredentials;
	}

	private String gmsRegister(Checkin.AndroidCheckinResponse checkinResponse) throws IOException
	{
		long androidId = checkinResponse.getAndroidId();

		Request request = new Request.Builder()
			.url(REGISTER_URL)
			.post(new FormBody.Builder()
					.add("app", "com.google.android.gms")
					.add("device", String.valueOf(androidId))
					.add("sender", "745476177629")
					.build())
			.header("Authorization", "AidLogin " + androidId + ":" + checkinResponse.getSecurityToken())
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Connection", "close")
			.build();

		Response result = httpClient.newCall(request).execute();

		String response = result.body().string();

		if (response.contains("Error"))
			throw new IllegalStateException("Failed to login with error: " + response);

		String[] split = response.split("=");

		if (split.length <= 1)
			throw new IllegalStateException("Response is missing fields.");

		return split[1];
	}

	private String webpushRegister(String subtype, Checkin.AndroidCheckinResponse checkinResponse) throws IOException
	{
		long androidId = checkinResponse.getAndroidId();

		String appId = Keys.generateRandomString(11);
		// + posilaji UA  user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.90 Safari/537.36

		//  app=com.chrome.macosx&
		// X-subtype=wp:https://push-caaedaa-6319.pushails.com/%23181B27EE-8807-4156-8203-B0709DE6C-V2&
		// device=5058868683385853457&
		// scope=GCM&
		// X-scope=GCM&
		// gmsv=77&
		// appid=eUZlsOGLAr8&sender=BMCGksCJpuGxANeG0m0sJi3NMmEV3JbebW_HcHKRNcQpQqsRb1QsoIQXv_ngM_xKeOgP4JVMEsx26hGcZuiCn4Y
		Request request = new Request.Builder()
				.url(REGISTER_URL)
				.post(new FormBody.Builder()
						.add("app", config.getAppType())
						.addEncoded("X-subtype", subtype)
						.add("scope", "GCM")
						.add("X-scope", "GCM")
						.add("gmsv", String.valueOf(77))
						.add("device", String.valueOf(androidId))
						.add("appid", appId)
						.add("sender", config.getServerKey())
						.build())
				.header("Authorization", "AidLogin " + androidId + ":" + checkinResponse.getSecurityToken())
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Connection", "close")
				.build();

		Response result = httpClient.newCall(request).execute();

		String response = result.body().string();

		if (response.contains("Error"))
			throw new IllegalStateException("Failed to login with error: " + response);

		String[] split = response.split("=");

		if (split.length <= 1)
			throw new IllegalStateException("Response is missing fields.");

		return split[1];
	}

	public Checkin.AndroidCheckinResponse checkin(long androidId, long securityToken) throws IOException
	{
		Checkin.AndroidCheckinRequest.Builder builder = Checkin.AndroidCheckinRequest
				.newBuilder()
				.setUserSerialNumber(0)
				.setCheckin(AndroidCheckin.AndroidCheckinProto
						.newBuilder()
						.setType(AndroidCheckin.DeviceType.DEVICE_CHROME_BROWSER)
						.setChromeBuild(AndroidCheckin.ChromeBuildProto.newBuilder()
								.setPlatform(AndroidCheckin.ChromeBuildProto.Platform.PLATFORM_MAC)
								.setChromeVersion("63.0.3234.0")
								.setChannel(AndroidCheckin.ChromeBuildProto.Channel.CHANNEL_STABLE)
								.build()))
				.setVersion(3);

		if(androidId > 0)
			builder.setId(androidId);

		if(securityToken > 0)
			builder.setSecurityToken(securityToken);

		Checkin.AndroidCheckinRequest checkinRequest = builder.build();

		Request request = new Request.Builder()
				.url(CHECKIN_URL)
				.method("POST", RequestBody.create(MediaType.parse("application/x-protobuf"), checkinRequest.toByteArray()))
				.header("Connection", "close")
				.build();

		Response result = httpClient
				.newCall(request)
				.execute();

		if (result.isSuccessful())
		{
			Checkin.AndroidCheckinResponse checkin = Checkin.AndroidCheckinResponse.parseFrom(result.body().bytes());
			return checkin;
		}

		throw new IllegalStateException("Failed to parse checkin response");
	}
}
