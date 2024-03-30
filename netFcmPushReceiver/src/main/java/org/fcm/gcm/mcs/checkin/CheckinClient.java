package org.fcm.gcm.mcs.checkin;

import checkin_proto.AndroidCheckin;
import checkin_proto.Checkin;
import com.fasterxml.jackson.core.util.JacksonFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.fcm.gcm.mcs.base64.Base64;
import org.fcm.gcm.mcs.model.Application;
import org.fcm.gcm.mcs.model.JsonService;
import org.fcm.gcm.mcs.model.Sender;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.helidon.connector.HelidonConnectorProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.logging.LoggingFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static org.fcm.gcm.mcs.model.JsonService.OBJECT_MAPPER;
import static org.glassfish.jersey.client.ClientProperties.*;
import static org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS;
import static org.glassfish.jersey.logging.LoggingFeature.Verbosity.PAYLOAD_ANY;

public class CheckinClient {

    public static final String FCM_SUBSCRIBE = "https://fcm.googleapis.com/fcm/connect/subscribe";
    public static final String FCM_ENDPOINT = "https://fcm.googleapis.com/fcm/send";
    private static final String REGISTER_URL = "https://android.clients.google.com/c2dm/register3";
    private static final String CHECKIN_URL = "https://android.clients.google.com/checkin";
    public static final String CHROME_VERSION = "122.0.6261.128";//"63.0.3234.0";
    public static final String AUTHORIZED_ENTITY = "authorized_entity";
    public static final String ENDPOINT = "endpoint";
    public static final String ENCRYPTION_KEY = "encryption_key";
    public static final String ENCRYPTION_AUTH = "encryption_auth";
    public static final String APP = "app";
    public static final String DEVICE = "device";
    public static final String X_SUBTYPE = "X-subtype";
    public static final String SENDER = "sender";
    public static final String AID_LOGIN = "AidLogin ";
    public static final String COLON = ":";


    private Application applicationConfig;

    public CheckinClient() {
    }

    public CheckinClient(Application applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    public Checkin.AndroidCheckinResponse checkin(long androidId, long securityToken) throws IOException {
        Checkin.AndroidCheckinRequest.Builder builder = Checkin.AndroidCheckinRequest
                .newBuilder()
                .setUserSerialNumber(0)
                .setCheckin(AndroidCheckin.AndroidCheckinProto
                        .newBuilder()
                        .setType(AndroidCheckin.DeviceType.DEVICE_CHROME_BROWSER)
                        .setChromeBuild(AndroidCheckin.ChromeBuildProto.newBuilder()
                                .setPlatform(AndroidCheckin.ChromeBuildProto.Platform.PLATFORM_MAC)
                                .setChromeVersion(CHROME_VERSION)
                                .setChannel(AndroidCheckin.ChromeBuildProto.Channel.CHANNEL_STABLE)
                                .build()))
                .setVersion(3);
        if (androidId > 0) builder.setId(androidId);
        if (securityToken > 0) builder.setSecurityToken(securityToken);
        Checkin.AndroidCheckinRequest checkinRequest = builder.build();

        Client client = getStandardClient();
        WebTarget target = client.target(CHECKIN_URL);
        MediaType mediaType = new MediaType("application", "x-protobuf");
        try (Response response = target
                .request()
                .header(CONTENT_TYPE, mediaType)
                .post(Entity.entity(checkinRequest.toByteArray(), mediaType))) {
            if (response.getStatus() == 200) {
                return Checkin
                        .AndroidCheckinResponse
                        .parseFrom(response.readEntity(InputStream.class));
            }
        }
        throw new IllegalStateException("Failed to parse checkin response");
    }

    public void performCheckin(Map<String, String> config) throws IOException, InterruptedException {
        org.fcm.gcm.mcs.model.Checkin checkin = applicationConfig.getGcm();
        if (checkin == null) {
            checkin = new org.fcm.gcm.mcs.model.Checkin();
            applicationConfig.setGcm(checkin);
            Checkin.AndroidCheckinResponse checkinResponse = checkin(checkin.getAndroidId()
                    , checkin.getSecurityToken());
            checkin.setAndroidId(checkinResponse.getAndroidId());
            checkin.setSecurityToken(checkinResponse.getSecurityToken());
            Thread.sleep(2000);
            checkin.setServerKey(config.get("serverKey"));
            checkin.setSubtype(String.format("wp:%s#%s", "receiver.push.com", UUID.randomUUID()));

            String gcmToken = gcmRegister();
            applicationConfig.getGcm().setGcmToken(gcmToken);
            Thread.sleep(2000);

            JsonService.saveJson(applicationConfig);
        } else {
            Checkin.AndroidCheckinResponse checkinResponse = checkin(checkin.getAndroidId()
                    , checkin.getSecurityToken());
            if (checkin.getAndroidId() != checkinResponse.getAndroidId())
                System.out.println("this checkin response is not the same");
            if (checkin.getSecurityToken() != checkinResponse.getSecurityToken())
                System.out.println("this checkin security token is not the same");
            /*checkin.setAndroidId(checkinResponse.getAndroidId());
            checkin.setSecurityToken(checkinResponse.getSecurityToken());
            JsonService.saveJson(applicationConfig);*/

        }
    }

    public String performTokenRegistration(Map<String, String> config) throws IOException {
        boolean isSenderExist = false;
        String fcmToken = null;
        String senderId = config.get("senderId");
        for (Sender sender : applicationConfig.getSenders())
            if (senderId.equals(sender.getSenderId())) {
                isSenderExist = true;
                fcmToken = sender.getFcmToken();
                break;
            }

        boolean needNewToken = Boolean.parseBoolean(config.get("newtoken"));
        if (!isSenderExist || needNewToken) {
            Map<String, String> jsonObject = registerFCM(senderId,
                    applicationConfig.getGcm().getGcmToken());
            fcmToken = jsonObject.get("token");
            applicationConfig.getSenders().add(new Sender(senderId, fcmToken, config.get("name")));
            JsonService.saveJson(applicationConfig);
        }

        return fcmToken;
    }

    private String gcmRegister() throws IOException {
        long androidId = applicationConfig.getGcm().getAndroidId();
        long securityToken = applicationConfig.getGcm().getSecurityToken();
        WebTarget target = getStandardClient().target(REGISTER_URL);
        Form form = new Form();
        form.param(APP, String.valueOf(AppType.CHROME_LINUX));
        form.param(DEVICE, String.valueOf(androidId));
        form.param(X_SUBTYPE, String.valueOf(applicationConfig.getGcm().getSubtype()));
        form.param(SENDER, applicationConfig.getGcm().getServerKey());
        try (Response response = target
                .request()
                .header(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_TYPE)
                .header(AUTHORIZATION, AID_LOGIN + androidId + COLON + securityToken)
                .post(Entity.entity(form, APPLICATION_FORM_URLENCODED_TYPE))) {
            if (response.getStatus() == 200) {
                String responseString = response.readEntity(String.class);
                System.out.println("responseString = " + responseString);
                String[] split = responseString.split("=");
                if (split.length <= 1)
                    throw new IllegalStateException("Response is missing fields.");
                return split[1];
            }
        }
        throw new IllegalStateException("Failed to login with error.");
    }

    private Map registerFCM(String senderId, String gcmtoken) throws IOException {
        WebTarget target = getStandardClient().target(FCM_SUBSCRIBE);
        Form form = new Form();
        form.param(AUTHORIZED_ENTITY, senderId);
        form.param(ENDPOINT, FCM_ENDPOINT + "/" + gcmtoken);
        form.param(ENCRYPTION_KEY, applicationConfig.getKeys().getPublicKeyString());
        form.param(ENCRYPTION_AUTH, applicationConfig.getKeys().getAuthSecret());
        try (Response response = target
                .request()
                .header(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_TYPE)
                .post(Entity.entity(form, APPLICATION_FORM_URLENCODED_TYPE))) {
            if (response.getStatus() == 200) {
                HashMap hashMap = response.readEntity(HashMap.class);
                System.out.println("hashMap = " + hashMap);
                return hashMap;
            }
        }
        throw new IllegalStateException("Failed to login with error.");
    }

    private static final String FCM_REGISTRATION_URL = "https://fcmregistrations.googleapis.com/v1/projects/${projectId}/registrations";
    private static final String FCM_INSTALLATION_URL = "https://firebaseinstallations.googleapis.com/v1/projects/${projectId}/installations";

    private Map getFirebaseInstallation(Application applicationConfig) throws NoSuchAlgorithmException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("appId", applicationConfig.getFcm().getAppId());
        root.put("authVersion", "FIS_v2");
        root.put("fid", generateFirebaseFid());
        root.put("sdkVersion", "w:0.6.4");

        ObjectNode heartBeat = OBJECT_MAPPER.createObjectNode();
        heartBeat.putArray("heartbeats");
        heartBeat.put("version", "2");

        WebTarget target = getStandardClient().target(FCM_INSTALLATION_URL.replace("${projectId}", applicationConfig.getFcm().getProjectId()));
        try (Response response = target
                .request()
                .header(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_TYPE)
                .header("x-goog-api-key", applicationConfig.getFcm().getApiKey())
                .header("x-firebase-client", Base64.getUrlEncoder().encodeToString(heartBeat.toString().getBytes()))
                .post(Entity.entity(root.toString(), APPLICATION_JSON))) {
            if (response.getStatus() == 200) {
                HashMap hashMap = response.readEntity(HashMap.class);
                System.out.println("Map = " + hashMap);
                return hashMap;
            }
        }
        throw new IllegalStateException("Failed to call getFirebaseInstallation.");


    }

    private String generateFirebaseFid() throws NoSuchAlgorithmException {
        String replace = FCM_INSTALLATION_URL.replace("${projectId}", "test");
        System.out.println("replace = " + replace);
        Random random = new Random();
        byte[] bytes = new byte[17];
//        random.nextBytes(bytes);
        SecureRandom.getInstanceStrong().nextBytes(bytes);
        bytes[0] = (byte) (0b01110000 + (bytes[0] % 0b00010000));
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        ObjectNode heartBeat = OBJECT_MAPPER.createObjectNode();
        heartBeat.putArray("heartbeats");
        heartBeat.put("version", "2");
        System.out.println("heartBeat = " + heartBeat);

        String string = Base64.getUrlEncoder().encodeToString(heartBeat.toString().getBytes());
        System.out.println("string = " + string);
        CheckinClient client = new CheckinClient();
        System.out.println("client.generateFirebaseFid() = " + client.generateFirebaseFid());
    }


    private Map registerFCMv1(Application config) throws IOException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode webNode = OBJECT_MAPPER.createObjectNode();
        webNode.put("endpoint", FCM_ENDPOINT + "/" + config.getGcm().getGcmToken());
        webNode.put("applicationPubKey", applicationConfig.getFcm().getVapidKey());
        webNode.put("auth", Base64.getUrlEncoder().encodeToString(config.getKeys().getAuthSecret().getBytes()));
        webNode.put("p256dh", Base64.getUrlEncoder().encodeToString(config.getKeys().getPublicKeyString().getBytes()));
        root.put("web", webNode);
        String value = root.toString();
        WebTarget target = getStandardClient().target(FCM_REGISTRATION_URL.replace("${projectId}", config.getFcm().getProjectId()));
        try (Response response = target
                .request()
                .header(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_TYPE)
                .header("x-goog-api-key", config.getFcm().getApiKey())
                .header("x-goog-firebase-installations-auth", "firebase token")
                .post(Entity.entity(value, APPLICATION_JSON))) {
            if (response.getStatus() == 200) {
                HashMap hashMap = response.readEntity(HashMap.class);
                System.out.println("Map = " + hashMap);
                return hashMap;
            }
        }
        throw new IllegalStateException("Failed to registerFCMv1.");
    }


    public static Client newClient() {
        return newClient(getStandardClientConfiguration());
    }

    public static Client getStandardClient() {
        if (standardClient == null) {
            standardClient = newClient(getStandardClientConfiguration());
        }
        return standardClient;
    }


    public static Client newClient(Configuration configuration) {
        final ClientBuilder builder = ClientBuilder.newBuilder()
                .withConfig(configuration)
                .hostnameVerifier((hostname, sslSession) -> true);
        return builder.build();
    }

    public static Configuration getStandardClientConfiguration() {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(FINEST);
        LOGGER.addHandler(new StdoutConsoleHandler());
        // TODO: If there are ever any standard proxy settings, this would be a good place to put them.
        return new ClientConfig()
                .connectorProvider(new HelidonConnectorProvider())
                .register(new LoggingFeature(LOGGER, INFO,
                        PAYLOAD_ANY, 9090))
                .register(JacksonFeature.class)
                .register(new JacksonJaxbJsonProvider(OBJECT_MAPPER,
                        DEFAULT_ANNOTATIONS))
                .property(CONNECT_TIMEOUT, 30000)
                .property(READ_TIMEOUT, 120000)
                .property(OUTBOUND_CONTENT_LENGTH_BUFFER, Integer.valueOf(-1))
                .property(REQUEST_ENTITY_PROCESSING, "CHUNKED");
    }

    private static Client standardClient;
    private static Logger LOGGER = Logger.getLogger(CheckinClient.class.getName());


}

class StdoutConsoleHandler extends ConsoleHandler {
    protected void setOutputStream(OutputStream out) throws SecurityException {
        super.setOutputStream(System.out);
    }
}