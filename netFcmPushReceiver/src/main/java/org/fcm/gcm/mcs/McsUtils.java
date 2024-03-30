package org.fcm.gcm.mcs;


import mcs_proto.Mcs;

import java.util.HashMap;

public class McsUtils {
    public static String crippleUrlBase64(String encoded) {
        return encoded.replace("=", "");
    }

    public static HashMap<String, String> getAppData(Mcs.DataMessageStanza message, String key) {
        HashMap<String, String> map = new HashMap<>();

        String data = getAppDataString(message, key);

        if (data != null) {
            if (data.startsWith("; "))
                data = data.substring(2);

            String[] splits = data.split(";");

            for (String s : splits) {
                String[] arr = s.split("=");

                if (arr.length >= 2) {
                    String k = arr[0];
                    String v = arr[1];
                    map.put(k, v);
                }
            }
        }

        return map;
    }

    public static String getAppDataString(Mcs.DataMessageStanza message, String key) {
        if (message == null || key == null)
            return null;

        for (int i = 0; i < message.getAppDataCount(); i++) {
            Mcs.AppData appData = message.getAppData(i);

            if (key.equals(appData.getKey()))
                return appData.getValue();
        }

        return null;
    }
}