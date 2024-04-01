package org.fcm.gcm.mcs.model;

import java.util.ArrayList;
import java.util.List;

public class Application {
    private FcmKeys keys;
    private Checkin gcm;
    private List<Sender> senders = new ArrayList<>();
    private FirebaseConfig fcm;
    public Application() {
    }

    public Application(FcmKeys keys, Checkin checkin, List<Sender> senders) {
        this.keys = keys;
        this.gcm = checkin;
        this.senders = senders;
    }

    public FcmKeys getKeys() {
        return keys;
    }

    public void setKeys(FcmKeys keys) {
        this.keys = keys;
    }

    public Checkin getGcm() {
        return gcm;
    }

    public void setGcm(Checkin gcm) {
        this.gcm = gcm;
    }

    public List<Sender> getSenders() {
        return senders;
    }

    public void setSenders(List<Sender> senders) {
        this.senders = senders;
    }

    public FirebaseConfig getFcm() {
        return fcm;
    }

    public void setFcm(FirebaseConfig fcm) {
        this.fcm = fcm;
    }
}
