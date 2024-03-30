/***********************************************************************
 * Copyright (c) 2019 Milan Jaitner                                    *
 * Distributed under the MIT software license, see the accompanying    *
 * file COPYING or https://www.opensource.org/licenses/mit-license.php.*
 ***********************************************************************/

package org.fcm.gcm.mcs.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.fcm.gcm.mcs.base64.Base64;
import org.fcm.gcm.mcs.util.ecdh.Keys;

import java.security.KeyPair;

public class FcmKeys {
    private static final String PUBLIC_KEY = "pub";
    private static final String PRIVATE_KEY = "pk";
    private static final String AUTH_SECRET = "secret";

    @JsonProperty("privateKey")
    private String privateKeyStr;
    @JsonProperty("publicKey")
    private String publicKeyString;
    public String getPrivateKeyStr() {
        if (privateKey != null && privateKeyStr == null) {
            return Keys.base64PrivateKey(privateKey);
        }
        return privateKeyStr;
    }

    public void setPrivateKeyStr(String privateKeyStr) throws Exception {
        this.privateKeyStr = privateKeyStr;
        if (privateKeyStr != null) {
            this.privateKey = Keys.loadPrivateKey(privateKeyStr);
        }
    }

    public String getPublicKeyString() {
        if (publicKey != null && publicKeyString == null) {
            return Keys.base64PublicKey(publicKey);
        }
        return publicKeyString;
    }

    public void setPublicKeyString(String publicKeyString) throws Exception {
        this.publicKeyString = publicKeyString;
        if (publicKeyString != null) {
            this.publicKey = Keys.loadPublicKey(publicKeyString);
        }
    }

    @JsonIgnore
    private ECPublicKey publicKey;
    @JsonIgnore
    private ECPrivateKey privateKey;
    private String authSecret;

    public FcmKeys() {
    }

    public FcmKeys(String publicKey, String privateKey, String authSecret) throws Exception {
        this(Keys.loadPublicKey(publicKey), Keys.loadPrivateKey(privateKey), authSecret);
    }

    public FcmKeys(ECPublicKey publicKey, ECPrivateKey privateKey, String authSecret) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.authSecret = authSecret;
    }

    public ECPrivateKey getPrivateKey() {
        return privateKey;
    }

    public ECPublicKey getPublicKey() {
        return publicKey;
    }

    public String getAuthSecret() {
        return authSecret;
    }

    public static FcmKeys generateKeys() throws Exception {
        KeyPair keyPair = Keys.generateKeyPair();
        String authSecret = new String(Base64.getEncoder().encode(Keys.generateRandomString(16).getBytes("UTF-8")));

        return new FcmKeys((ECPublicKey) keyPair.getPublic(),
                (ECPrivateKey) keyPair.getPrivate(),
                authSecret);
    }
}
