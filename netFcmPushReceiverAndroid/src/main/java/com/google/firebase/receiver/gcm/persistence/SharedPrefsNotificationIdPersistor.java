/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.gcm.persistence;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SharedPrefsNotificationIdPersistor extends AbstractNotificationIdPersistor
{
	private final SharedPreferences sharedPrefs;
	private final String prefsKey;

	public SharedPrefsNotificationIdPersistor(Context ctx, String prefsKey)
	{
		this.sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		this.prefsKey = prefsKey;
	}

	@Override
	public void load()
	{
		deserialize(sharedPrefs.getString(prefsKey, null));
	}

	@Override
	public void save()
	{
		try
		{
			sharedPrefs.edit()
					.putString(prefsKey, serialize().toString())
					.apply();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
