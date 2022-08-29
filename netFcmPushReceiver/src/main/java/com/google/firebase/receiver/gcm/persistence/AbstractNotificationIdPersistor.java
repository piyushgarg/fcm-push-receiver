/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.gcm.persistence;

import org.json.JSONArray;

import java.util.HashSet;

public abstract class AbstractNotificationIdPersistor extends HashSet<String>
{
	@Override
	public boolean add(String s)
	{
		boolean result = super.add(s);
		save();
		return result;
	}

	@Override
	public boolean remove(Object o)
	{
		boolean result = super.remove(o);
		save();
		return result;
	}

	public abstract void load();

	public abstract void save();

	protected boolean deserialize(String str)
	{
		if(str != null)
		{
			try
			{
				clear();

				JSONArray arr = new JSONArray(str);

				for (int i = 0; i < arr.length(); i++)
					add(arr.optString(i));

				return true;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		return false;
	}

	protected JSONArray serialize()
	{
		JSONArray arr = new JSONArray();

		for(String s : this)
			arr.put(s);

		return arr;
	}

}
