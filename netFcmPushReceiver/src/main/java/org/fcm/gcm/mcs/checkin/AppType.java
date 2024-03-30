package org.fcm.gcm.mcs.checkin;

public enum AppType {
    CHROME_LINUX("org.chromium.linux"),
    CHROME_MACOS("com.chrome.macosx");

    private final String type;

    AppType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
