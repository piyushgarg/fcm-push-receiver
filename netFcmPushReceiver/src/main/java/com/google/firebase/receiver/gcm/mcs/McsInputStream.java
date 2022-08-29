package com.google.firebase.receiver.gcm.mcs;

import com.google.firebase.fcm.mcs.Mcs;
import com.google.protobuf.MessageLite;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class McsInputStream implements Closeable
{
	public class Message
	{
		private final int tag;
		private final byte[] data;

		public Message(int tag, byte[] data)
		{
			this.tag = tag;
			this.data = data;
		}

		public int getTag()
		{
			return tag;
		}

		public byte[] getData()
		{
			return data;
		}

		@Override
		public String toString()
		{
			return "Message{" +
					"tag=" + tag +
					", bytes=" + Arrays.toString(data) +
					'}';
		}

		public MessageLite protobuf() throws IOException
		{
			switch (tag)
			{
				case McsProtoTag.kHeartbeatPingTag:
					return Mcs.HeartbeatPing.parseFrom(data);

				case McsProtoTag.kHeartbeatAckTag:
					return Mcs.HeartbeatAck.parseFrom(data);

				case McsProtoTag.kLoginRequestTag:
					return Mcs.LoginRequest.parseFrom(data);

				case McsProtoTag.kLoginResponseTag:
					return Mcs.LoginResponse.parseFrom(data);

				case McsProtoTag.kCloseTag:
					return Mcs.Close.parseFrom(data);

				case McsProtoTag.kIqStanzaTag:
					return Mcs.IqStanza.parseFrom(data);

				case McsProtoTag.kDataMessageStanzaTag:
					return Mcs.DataMessageStanza.parseFrom(data);

				case McsProtoTag.kStreamErrorStanzaTag:
					return Mcs.StreamErrorStanza.parseFrom(data);

				default:
					return null;
			}
		}
	}

	private final InputStream is;

	private boolean initialized;
	private int version = -1;
	private int lastStreamIdReported = -1;
	private int streamId = 0;
	private long lastMsgTime = 0;

	private boolean closed = false;

	public McsInputStream(InputStream is)
	{
		this(is, false);
	}

	public McsInputStream(InputStream is, boolean initialized)
	{
		this.is = is;
		this.initialized = initialized;
	}

	@Override
	public void close()
	{
		if (!closed)
		{
			closed = true;
		}
	}

	public int getStreamId()
	{
		lastStreamIdReported = streamId;
		return streamId;
	}

	public boolean newStreamIdAvailable()
	{
		return lastStreamIdReported != streamId;
	}

	public int getVersion()
	{
		ensureVersionRead();
		return version;
	}

	private synchronized void ensureVersionRead()
	{
		if (!initialized)
		{
			try
			{
				version = is.read();
				//System.out.println("Reading from MCS version: " + version);
				initialized = true;
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public synchronized Message read() throws IOException
	{
		ensureVersionRead();
		int mcsTag = is.read();
		int mcsSize = readVarint();
		if (mcsTag < 0 || mcsSize < 0)
			return null;

		byte[] bytes = new byte[mcsSize];
		int len = 0, read = 0;
		while (len < mcsSize && read >= 0)
		{
			len += (read = is.read(bytes, len, mcsSize - len)) < 0 ? 0 : read;
		}

		return new Message(mcsTag, bytes);
	}

	private int readVarint() throws IOException
	{
		int res = 0, s = -7, read;
		do
		{
			res |= ((read = is.read()) & 0x7F) << (s += 7);
		}
		while (read >= 0 && (read & 0x80) == 0x80 && s < 32);
		if (read < 0) return -1;
		return res;
	}
}
