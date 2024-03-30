package org.fcm.gcm.mcs.model;

public class Checkin {
    private String subtype;
    private long securityToken;
    private String gcmToken;
    private String serverKey;
    private long androidId;

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public long getSecurityToken() {
        return securityToken;
    }

    public void setSecurityToken(long securityToken) {
        this.securityToken = securityToken;
    }

    public String getGcmToken() {
        return gcmToken;
    }

    public void setGcmToken(String gcmToken) {
        this.gcmToken = gcmToken;
    }

    public String getServerKey() {
        return serverKey;
    }

    public void setServerKey(String serverKey) {
        this.serverKey = serverKey;
    }

    public long getAndroidId() {
        return androidId;
    }

    public void setAndroidId(long androidId) {
        this.androidId = androidId;
    }

}
