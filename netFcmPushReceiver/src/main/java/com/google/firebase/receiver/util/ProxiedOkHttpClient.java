/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProxiedOkHttpClient
{
	public static OkHttpClient create()
	{
		try
		{
			final TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager()
					{
						@Override
						public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException
						{
						}

						@Override
						public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException
						{
						}

						@Override
						public java.security.cert.X509Certificate[] getAcceptedIssuers()
						{
							return new java.security.cert.X509Certificate[]{};
						}
					}
			};

			// Install the all-trusting trust manager
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
			builder.hostnameVerifier(new HostnameVerifier()
			{
				@Override
				public boolean verify(String hostname, SSLSession session)
				{
					return true;
				}
			});

			builder = new OkHttpClient.Builder()
					.addNetworkInterceptor(new Interceptor()
					{
						@Override
						public Response intercept(Chain chain) throws IOException
						{
							Request request = chain.request()
									.newBuilder()
									.removeHeader("Accept-Encoding")
									.removeHeader("User-Agent")
									.build();
							return chain.proceed(request);
						}
					})
					.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.1.32", 8888)))
					.sslSocketFactory(sslContext.getSocketFactory(),
							new X509TrustManager()
							{
								@Override
								public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException
								{
								}

								@Override
								public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException
								{
								}

								@Override
								public java.security.cert.X509Certificate[] getAcceptedIssuers()
								{
									return new java.security.cert.X509Certificate[]{};
								}
							});

			return builder.build();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}
}
