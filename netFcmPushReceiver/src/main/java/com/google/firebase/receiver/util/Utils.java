/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.util;

public class Utils
{
	public static String escape(String str)
	{
		return str
				.replace("=", "")
				.replace("+", "-")
				.replace("/", "_");
	}
}
