package org.fcm.gcm.mcs.model;

/**
 * The app ID is the package name of the app.
 * <p>- The ECE auth secret and ECE public key must be generated beforehand with the `createFcmECDH` and
 * `generateFcmAuthSecret` functions. The auth secret and ECDH keys must be stored.</>
 * <p>-The Firebase API key, Firebase app ID and Firebase project ID can be found in the Firebase console.</p>
 * <p>-The VAPID key can be found in the Firebase console.</p>
 * <p>
 * Returns the ACG ID, ACG security token and FCM token, which must be stored.
 */
public class FirebaseConfig {

    /**
     * The app ID is the package name of the app.
     */
    private String appId;
    /**
     *
     */
    private String projectId;
    private String apiKey;
    /**
     * The VAPID key can be found in the Firebase console.
     */
    private String vapidKey;
    private String token;
    private String endpoint;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
