package org.fcm.gcm.mcs.util.ecdh;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.fcm.gcm.mcs.base64.Base64;

import java.math.BigInteger;
import java.security.*;
import java.util.Random;

import static org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;

public class Keys {
    private static final String CURVE = "prime256v1";
    private static final String ALGORITHM = "ECDH";
    private static final Random random = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();

    private static boolean cryptoFixed = false;

    static {
        fixCrypto();
    }

    private static synchronized void fixCrypto() {
        if (cryptoFixed)
            return;
        final Provider provider = Security.getProvider("BC");
        if (provider != null && !provider.getClass().equals(BouncyCastleProvider.class)) {
            // Android registers its own BC provider. As it might be outdated and might not include
            // all needed ciphers, we substitute it with a known BC bundled in the app.
            // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
            // of that it's possible to have another BC implementation loaded in VM.
            Security.removeProvider("BC");
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        } else {
            Security.addProvider(new BouncyCastleProvider());
        }
        cryptoFixed = true;
    }

    public static KeyPair generateKeyPair() throws InvalidAlgorithmParameterException, NoSuchProviderException, NoSuchAlgorithmException {
        ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec(CURVE);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER_NAME);
        keyPairGenerator.initialize(parameterSpec);

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Get the uncompressed encoding of the public key point. The resulting array should be 65 bytes length and start
     * with 0x04 followed by the x and y coordinates (32 bytes each).
     *
     * @param publicKey
     * @return
     */
    public static byte[] savePublicKey(ECPublicKey publicKey) {
        return publicKey.getQ().getEncoded(false);
    }

    public static byte[] savePrivateKey(ECPrivateKey privateKey) {
        return privateKey.getD().toByteArray();
    }

    public static String base64PublicKey(ECPublicKey publicKey) {
        return new String(Base64.getEncoder().encode(publicKey.getQ().getEncoded(false)));
    }

    public static String base64PrivateKey(ECPrivateKey privateKey) {
        return new String(Base64.getEncoder().encode(privateKey.getD().toByteArray()));
    }

    /**
     * Load the public key from a URL-safe base64 encoded string. Takes into account the different encodings, including
     * point compression.
     *
     * @param encodedPublicKey
     */
    public static ECPublicKey loadPublicKey(String encodedPublicKey) throws Exception {
        return loadPublicKey(Base64.getDecoder().decode(encodedPublicKey));
    }

    public static ECPublicKey loadPublicKey(byte[] decodedPublicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER_NAME);
        ECParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec(CURVE);
        ECCurve curve = parameterSpec.getCurve();
        ECPoint point = curve.decodePoint(decodedPublicKey);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, parameterSpec);

        return (ECPublicKey) keyFactory.generatePublic(pubSpec);
    }

    public static ECPublicKey loadPublicKey(ECPrivateKey privateKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER_NAME);
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(CURVE);
        ECPoint Q = ecSpec.getG().multiply(privateKey.getD());
        byte[] publicDerBytes = Q.getEncoded(false);
        ECPoint point = ecSpec.getCurve().decodePoint(publicDerBytes);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecSpec);

        return (ECPublicKey) keyFactory.generatePublic(pubSpec);
    }

    /**
     * Load the private key from a URL-safe base64 encoded string
     *
     * @param encodedPrivateKey
     * @return
     */
    public static ECPrivateKey loadPrivateKey(String encodedPrivateKey) throws Exception {
        byte[] decodedPrivateKey = Base64.getDecoder().decode(encodedPrivateKey);
        BigInteger s = BigIntegers.fromUnsignedByteArray(decodedPrivateKey);
        ECParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec(CURVE);
        ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(s, parameterSpec);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER_NAME);

        return (ECPrivateKey) keyFactory.generatePrivate(privateKeySpec);
    }

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++)
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);

        return sb.toString();
    }
}
