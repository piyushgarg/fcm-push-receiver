/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.util;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DefaultOkHttpClient
{
	public static OkHttpClient create()
	{
		try
		{
			OkHttpClient.Builder builder = new OkHttpClient.Builder()
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
