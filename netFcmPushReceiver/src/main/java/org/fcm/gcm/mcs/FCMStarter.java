package org.fcm.gcm.mcs;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import mcs_proto.Mcs;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fcm.gcm.mcs.checkin.CheckinClient;
import org.fcm.gcm.mcs.gcm.NotificationCallback;
import org.fcm.gcm.mcs.model.FcmKeys;
import org.fcm.gcm.mcs.gcm.GcmClient;
import org.fcm.gcm.mcs.gcm.persistence.FileNotificationIdPersistor;
import org.fcm.gcm.mcs.model.Application;
import org.fcm.gcm.mcs.model.JsonService;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class FCMStarter {

    private static String DEFAULT_VAPID_KEY = "BDOU99-h67HcA6JeFXHbSNMu7e2yNNu3RzoMj8TM4W88jITfq7ZmPvIM1Iv-4_l2LxQcYwhqby2xGpWwzjfAnG4";

    private GcmClient gcmClient;
    private CheckinClient checkinClient;
    private NotificationCallback notificationCallback;
    private File configDirectory;

    public FCMStarter() {
        Security.addProvider(new BouncyCastleProvider());

    }

    public static void main(String[] args) throws IOException {
        new FCMStarter().bringUpFCMReceiver();
    }

    public void setNotificationCallback(NotificationCallback notificationCallback) {
        this.notificationCallback = notificationCallback;
    }

    public void setConfigDirectory(File configDirectory) {
        this.configDirectory = configDirectory;
    }

    public void bringUpFCMReceiver() throws IOException {
        if (configDirectory == null) configDirectory = new File(".config");
        else configDirectory = new File(configDirectory, ".fcm");
        configDirectory.mkdirs();
        File notificationConfigFile = new File(configDirectory, "notids.json");
        Application application = JsonService.loadJson(configDirectory);

        try {
            if (application == null) {
                application = new Application();
                application.setKeys(FcmKeys.generateKeys());
            }
            Map<String, String> map = new HashMap<>();
            map.put("serverKey", DEFAULT_VAPID_KEY);
            checkinClient = new CheckinClient(application);
            checkinClient.performCheckin(map);
            Thread.sleep(5000);
            gcmClient = new GcmClient(application
                    , "mtalk.google.com"
                    , 5228
                    , new FileNotificationIdPersistor(notificationConfigFile)) {
                @Override
                protected void onNotificationReceived(Mcs.DataMessageStanza message, String json) {
                    System.out.println("Received notification at: " + new Date(message.getSent()) + ", from: " + message.getFrom());
                    System.out.println(json);
                    new Thread(() -> {
                        if (notificationCallback != null)
                            notificationCallback.actionOnNotification(json);
                        else new NotificationCallback() {
                            @Override
                            public void actionOnNotification(String json1) {
                                sendToEventHost(json1);
                            }
                        };
                        System.out.println("notification done = ");
                    }).start();
                }
            };
            gcmClient.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public String register(String senderId, String name, boolean newToken) throws IOException, NoSuchAlgorithmException {
        Map<String, String> map = new HashMap<>();
        map.put("senderId", senderId);
        map.put("name", name);
        map.put("newToken", String.valueOf(newToken));
        map.put("serverKey", DEFAULT_VAPID_KEY);
        return checkinClient.performNewTokenRegistration(map);
    }

    public static boolean sendToEventHost(String json) {
        if (json != null && json.contains("147354672683")) {
            WebTarget target = CheckinClient.getStandardClient().target("http://localhost:1818");
            try (Response response = target
                    .request()
                    .header(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_TYPE)
                    .post(Entity.entity(json, APPLICATION_JSON))) {
                if (response.getStatus() == 200) {
                    String string = response.readEntity(String.class);
                    System.out.println("string = " + string);
                    return true;
                }
            }
        }

        return false;
    }

}
