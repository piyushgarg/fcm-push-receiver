/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package com.google.firebase.receiver.gcm.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileNotificationIdPersistor extends AbstractNotificationIdPersistor
{
	private final Path file;

	public FileNotificationIdPersistor(File file)
	{
		this.file = file.toPath();
	}

	@Override
	public void load()
	{
		try
		{
			byte[] bytes = Files.readAllBytes(file);

			deserialize(new String(bytes));
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void save()
	{
		try
		{
			Files.write(file, serialize().toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
