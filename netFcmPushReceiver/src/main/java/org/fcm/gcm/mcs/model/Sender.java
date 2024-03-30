package org.fcm.gcm.mcs.model;

public class Sender {
    private String senderId;
    private String fcmToken;
    private String name;

    public Sender() {
    }

    public Sender(String senderId, String fcmToken, String name) {
        this.senderId = senderId;
        this.fcmToken = fcmToken;
        this.name = name;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
