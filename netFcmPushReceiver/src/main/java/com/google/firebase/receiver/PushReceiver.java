/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver;

import com.google.firebase.receiver.credentials.PushReceiverCredentials;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public abstract class PushReceiver extends AbstractPushReceiver
{
	private final File credsFile;

	public PushReceiver(File credsFile, ReceiverConfig config) throws Exception
	{
		super(new ReceiverConfig(config, loadCredentials(credsFile)));

		this.credsFile = credsFile;
	}

	private static PushReceiverCredentials loadCredentials(File credsFile)
	{
		try
		{
			String contents = FileUtils.readFileToString(credsFile, "UTF-8");

			return PushReceiverCredentials.from(contents);
		}
		catch (Exception e)
		{
			// noop
		}

		return null;
	}

	@Override
	public void saveCredentials(PushReceiverCredentials credentials)
	{
		try
		{
			FileUtils.write(credsFile, credentials.serialize().toString(), "UTF-8");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
