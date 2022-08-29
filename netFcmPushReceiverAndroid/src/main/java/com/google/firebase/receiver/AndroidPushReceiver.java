/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver;

import android.content.Context;
import android.preference.PreferenceManager;

import com.google.firebase.receiver.credentials.PushReceiverCredentials;

import org.json.JSONObject;

public abstract class AndroidPushReceiver extends AbstractPushReceiver
{
	public AndroidPushReceiver(ReceiverConfig receiverConfig, PushReceiverCredentials pushReceiverCredentials) throws Exception
	{
		super(new ReceiverConfig(receiverConfig, pushReceiverCredentials));
	}
}
