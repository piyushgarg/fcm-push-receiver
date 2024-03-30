package org.fcm.gcm.mcs;

import com.google.protobuf.MessageLite;
import mcs_proto.Mcs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class McsInputStream implements Closeable {
    public class Message {
        private final int tag;
        private final byte[] data;

        public Message(int tag, byte[] data) {
            this.tag = tag;
            this.data = data;
        }

        public int getTag() {
            return tag;
        }

        public byte[] getData() {
            return data;
        }

        @Override
        public String toString() {
            return "Message{" +
                    "tag=" + tag +
                    ", bytes=" + Arrays.toString(data) +
                    '}';
        }

        public MessageLite protobuf() throws IOException {
            return switch (tag) {
                case McsConstants.MCS_HEARTBEAT_PING_TAG
                        -> Mcs.HeartbeatPing.parseFrom(data);
                case McsConstants.MCS_HEARTBEAT_ACK_TAG
                        -> Mcs.HeartbeatAck.parseFrom(data);
                case McsConstants.MCS_LOGIN_REQUEST_TAG
                        -> Mcs.LoginRequest.parseFrom(data);
                case McsConstants.MCS_LOGIN_RESPONSE_TAG
                        -> Mcs.LoginResponse.parseFrom(data);
                case McsConstants.MCS_CLOSE_TAG
                        -> Mcs.Close.parseFrom(data);
                case McsConstants.MCS_IQ_STANZA_TAG
                        -> Mcs.IqStanza.parseFrom(data);
                case McsConstants.MCS_DATA_MESSAGE_STANZA_TAG
                        -> Mcs.DataMessageStanza.parseFrom(data);
                case McsConstants.kStreamErrorStanzaTag
                        -> Mcs.StreamErrorStanza.parseFrom(data);
                default -> null;
            };
        }
    }

    private final InputStream is;

    private boolean initialized;
    private int version = -1;
    private int lastStreamIdReported = -1;
    private int streamId = 0;

    private boolean closed = false;

    public McsInputStream(InputStream is) {
        this(is, false);
    }

    public McsInputStream(InputStream is, boolean initialized) {
        this.is = is;
        this.initialized = initialized;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
        }
    }

    public int getStreamId() {
        lastStreamIdReported = streamId;
        return streamId;
    }

    public boolean newStreamIdAvailable() {
        return lastStreamIdReported != streamId;
    }

    public int getVersion() {
        ensureVersionRead();
        return version;
    }

    private synchronized void ensureVersionRead() {
        if (!initialized) {
            try {
                version = is.read();
                System.out.println("Reading from MCS version: " + version);
                initialized = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized Message read() throws IOException {
        ensureVersionRead();
        int mcsTag = is.read();
        int mcsSize = readVarInt();
        if (mcsTag < 0 || mcsSize < 0)
            return null;

        byte[] bytes = new byte[mcsSize];
        int len = 0, read = 0;
        while (len < mcsSize && read >= 0) {
            len += (read = is.read(bytes, len, mcsSize - len)) < 0 ? 0 : read;
        }
        streamId++;
        return new Message(mcsTag, bytes);
    }

    private int readVarInt() throws IOException {
        int res = 0, s = -7, read;
        do {
            res |= ((read = is.read()) & 0x7F) << (s += 7);
        }
        while (read >= 0 && (read & 0x80) == 0x80 && s < 32);
        if (read < 0) return -1;
        return res;
    }
}