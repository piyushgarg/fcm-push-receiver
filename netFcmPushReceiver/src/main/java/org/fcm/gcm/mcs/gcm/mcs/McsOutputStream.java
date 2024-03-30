package org.fcm.gcm.mcs.gcm.mcs;

import com.google.protobuf.GeneratedMessage;
import org.fcm.gcm.mcs.gcm.GcmClient;

import java.io.IOException;
import java.io.OutputStream;

public class McsOutputStream {
    private final OutputStream os;
    private boolean initialized;
    private int version = GcmClient.kMCSVersion;

    public McsOutputStream(OutputStream os) {
        this(os, false);
    }

    public McsOutputStream(OutputStream os, boolean initialized) {
        this.os = os;
        this.initialized = initialized;
    }

    public synchronized void write(GeneratedMessage message, int tag) throws IOException {
        if (!initialized) {
            os.write(version);
            initialized = true;
        }

        os.write(tag);
        writeVarint(os, message.getSerializedSize());
        os.write(message.toByteArray());
        os.flush();
    }

    private void writeVarint(OutputStream os, int value) throws IOException {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                os.write(value);
                return;
            } else {
                os.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }
}