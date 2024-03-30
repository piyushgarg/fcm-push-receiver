package org.fcm.gcm.mcs.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class JsonService {

    private static final JsonFactory jsonFactory = new JsonFactory();
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(jsonFactory);


    private static String path = ".config";
    private static final String file = "application.json";

    public static Application loadJson(File dir) throws IOException {
        if (dir != null) path = dir.getPath();
        if (!isConfigFileExists()) return null;
        return OBJECT_MAPPER.readValue(getConfigFile(), Application.class);
    }

    public static void saveJson(Application application) throws IOException {
        OBJECT_MAPPER.writeValue(getConfigFile(), application);

    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File getConfigFile() {
        File dir = new File(path);
        dir.mkdirs();
        return new File(dir, file);
    }

    private static boolean isConfigFileExists() {
        return getConfigFile().exists();
    }


    public static void main(String[] args) throws Exception {


        // FileNotificationIdPersistor strings = new FileNotificationIdPersistor(new File(".creds", "notids.json"));

        JsonService jsonService = new JsonService();
        Application application = new Application();

//        if (jsonService.isConfigFileExists()) {
//        application = jsonService.loadJson();
//        } else {
        application.setKeys(FcmKeys.generateKeys());

        jsonService.saveJson(application);

        Checkin checkin = new Checkin();
        checkin.setAndroidId(123);
        checkin.setGcmToken("gcmtoken");
        checkin.setSubtype("subtype");
        checkin.setServerKey("serverkey");
        checkin.setSecurityToken(45678);
        application.setGcm(checkin);

        jsonService.saveJson(application);

//        }
        System.out.println("done");


    }

}
