package org.fcm.gcm.mcs.model;

public class FCMRegistration {

    private String appId;
    private String projectId;
    private String apiKey;
    private String vapidKey;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getVapidKey() {
        return vapidKey;
    }

    public void setVapidKey(String vapidKey) {
        this.vapidKey = vapidKey;
    }
}
