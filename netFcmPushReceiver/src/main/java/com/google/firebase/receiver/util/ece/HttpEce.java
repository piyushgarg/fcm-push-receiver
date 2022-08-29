package com.google.firebase.receiver.util.ece;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * An implementation of HTTP ECE (Encrypted Content Encoding) as described in
 * https://tools.ietf.org/html/draft-ietf-httpbis-encryption-encoding-02
 */
public class HttpEce {

	public static class Params {
		public byte[] salt;
		public byte[] key; // Either use key or keyId (in conjunction with saveKey())
		public String keyId;
		public PublicKey dh;
		public byte[] authSecret;
		public int padSize = 2;
		public int pad = 0;
		public int recordSize = 4096;
	}

	private static final String AES = "AES";
	private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
	private static final int AUTH_TAG_LENGTH_BYTES = 16;
	private static final int AUTH_TAG_LENGTH_BITS = AUTH_TAG_LENGTH_BYTES * 8;
	private static final int KEY_LENGTH_BYTES = 16;
	private static final int NONCE_LENGTH_BYTES = 12;
	private static final int SHA_256_LENGTH_BYTES = 32;

	private Map<String, KeyPair> keyPairs = new HashMap<>();
	private Map<String, byte[]> keys = new HashMap<>();
	private Map<String, byte[]> labels = new HashMap<>();
	private boolean useLegacyEncryptionMethods = false;

	public HttpEce() {}

	public HttpEce(boolean useLegacyEncryptionMethods) {
		this.useLegacyEncryptionMethods = useLegacyEncryptionMethods;
	}

	public void saveKey(String keyId, byte[] key) {
		keys.put(keyId, key);
	}

	// Use with DH
	public void saveKey(String keyPairId, KeyPair keyPair, String label) {
		keyPairs.put(keyPairId, keyPair);
		labels.put(keyPairId, concat(label.getBytes(), new byte[1]));
	}

	public byte[] encrypt(byte[] buffer, Params params) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, IOException {
		if (params.recordSize <= params.padSize) {
			throw new IllegalArgumentException("The recordSize parameter has to be greater than " + params.padSize);
		}
		KeyAndNonce keyAndNonce = deriveKeyAndNonce(params, Mode.ENCRYPT);
		SecretKeySpec secretKey = new SecretKeySpec(keyAndNonce.key, AES);
		int start = 0;
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		int pad = params.pad;

		// Note the <= here ensures that we write out a padding-only block at the end
		// of a buffer.
		for (int i = 0; start <= buffer.length; ++i) {
			// Pad so that at least one data byte is in a block.
			int recordPad = Math.min((1 << (params.padSize * 8)) - 1, // maximum padding
					Math.min(params.recordSize - params.padSize - 1, pad));
			pad -= recordPad;

			int end = Math.min(start + params.recordSize - params.padSize - recordPad, buffer.length);
			byte[] record = Arrays.copyOfRange(buffer, start, end);
			byte[] encrypted = encryptRecord(secretKey, keyAndNonce.nonce, i, record, recordPad, params.padSize);
			result.write(encrypted);
			start += params.recordSize - params.padSize - recordPad;
		}
		if (pad != 0) {
			throw new IllegalArgumentException("Unable to pad by requested amount, " + pad + " remaining");
		}
		return result.toByteArray();
	}

	private byte[] encryptRecord(SecretKeySpec key, byte[] nonceBase, int counter, byte[] buffer, int pad, int padSize) throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException
	{
		byte[] nonce = generateNonce(nonceBase, counter);
		byte[] padding = new byte[pad + padSize];
		writeLongBE(padding, pad, 0, padSize);

		//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || useLegacyEncryptionMethods) {
		//	return LegacyAESGCM.encrypt(key, nonce, concat(padding, buffer));
		//}

		Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING, BouncyCastleProvider.PROVIDER_NAME);
		cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, nonce));
		byte[] encryptedPadding = cipher.update(padding);
		byte[] encryptedBuffer = cipher.update(buffer);
		return concat(encryptedPadding, encryptedBuffer, cipher.doFinal());
	}

	public byte[] decrypt(byte[] buffer, Params params) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, IOException
	{
		if (params.recordSize <= params.padSize) {
			throw new IllegalArgumentException("The recordSize parameter has to be greater than " + params.padSize);
		}
		KeyAndNonce keyAndNonce = deriveKeyAndNonce(params, Mode.DECRYPT);
		SecretKeySpec secretKey = new SecretKeySpec(keyAndNonce.key, AES);
		int start = 0;
		ByteArrayOutputStream result = new ByteArrayOutputStream();

		for (int i = 0; start < buffer.length; ++i) {
			int end = start + params.recordSize + AUTH_TAG_LENGTH_BYTES;
			if (end == buffer.length) {
				throw new IllegalArgumentException("Truncated payload");
			}
			end = Math.min(end, buffer.length);
			if (end - start <= AUTH_TAG_LENGTH_BYTES) {
				throw new IllegalArgumentException("Invalid block: too small at " + i);
			}

			byte[] record = Arrays.copyOfRange(buffer, start, end);
			result.write(decryptRecord(secretKey, keyAndNonce.nonce, i, record, params.padSize));

			start = end;
		}
		return result.toByteArray();
	}

	private byte[] decryptRecord(SecretKeySpec key, byte[] nonceBase, int counter, byte[] buffer, int padSize) throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
		byte[] nonce = generateNonce(nonceBase, counter);

		final byte[] data;
		if (useLegacyEncryptionMethods) {
			data = com.google.firebase.receiver.util.ece.LegacyAESGCM.decrypt(key, nonce, buffer);
		} else {
			Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING, BouncyCastleProvider.PROVIDER_NAME);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, nonce));
			data = concat(cipher.update(buffer), cipher.doFinal());
		}

		int pad = (int) readLongBE(data, 0, padSize); // This cast is OK because the padding is 65537 max
		if (pad + padSize > data.length) {
			throw new IllegalArgumentException("padding exceeds block size");
		}
		for (int i = padSize; i < padSize + pad; i++) {
			if (data[i] != 0) {
				throw new IllegalArgumentException("invalid padding");
			}
		}
		return Arrays.copyOfRange(data, padSize + pad, data.length);
	}

	private KeyAndNonce deriveKeyAndNonce(Params params, Mode mode) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
		SecretAndContext sc = null;
		if (params.key != null) {
			sc = new SecretAndContext(params.key);
		} else if (params.dh != null) {
			sc = generateSecretAndContext(params.keyId, params.dh, mode);
		} else if (params.keyId != null) {
			sc = new SecretAndContext(keys.get(params.keyId));
		}

		if (sc == null || sc.secret == null) {
			throw new IllegalStateException("Unable to determine the secret");
		}
		if (params.authSecret != null) {
			sc.expandSecret(params.authSecret);
		}

		final byte[] keyInfo, nonceInfo;
		if (params.padSize == 2) {
			keyInfo = buildInfo("aesgcm", sc.context);
			nonceInfo = buildInfo("nonce", sc.context);
		} else if (params.padSize == 1) {
			keyInfo = "Content-Encoding: aesgcm128".getBytes();
			nonceInfo = "Content-Encoding: nonce".getBytes();
		} else {
			throw new IllegalArgumentException("Unable to set context for padSize " + params.padSize);
		}

		byte[] hkdfKey = hkdfExpand(sc.secret, params.salt, keyInfo, KEY_LENGTH_BYTES);
		byte[] hkdfNonce = hkdfExpand(sc.secret, params.salt, nonceInfo, NONCE_LENGTH_BYTES);

		return new KeyAndNonce(hkdfKey, hkdfNonce);
	}

	private SecretAndContext generateSecretAndContext(String keyId, PublicKey otherPublicKey, Mode mode) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException {
		KeyPair ownKeyPair = keyPairs.get(keyId);

		KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
		keyAgreement.init(ownKeyPair.getPrivate());
		keyAgreement.doPhase(otherPublicKey, true);
		byte[] secret = keyAgreement.generateSecret();

		final PublicKey senderPubKey, receiverPubKey;
		if (mode == Mode.ENCRYPT) {
			senderPubKey = ownKeyPair.getPublic();
			receiverPubKey = otherPublicKey;
		} else {
			senderPubKey = otherPublicKey;
			receiverPubKey = ownKeyPair.getPublic();
		}
		byte[] context = concat(labels.get(keyId), lengthPrefix(receiverPubKey), lengthPrefix(senderPubKey));

		return new SecretAndContext(secret, context);
	}

	private static byte[] hkdfExpand(byte[] ikm, byte[] salt, byte[] info, int length) {
		HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
		hkdf.init(new HKDFParameters(ikm, salt, info));

		byte[] okm = new byte[length];
		hkdf.generateBytes(okm, 0, length);

		return okm;
	}

	private static byte[] generateNonce(byte[] base, int counter) {
		byte[] nonce = base.clone();
		long m = readLongBE(nonce, base.length - 6, 6);
		long x = ((m ^ counter) & 0xffffff) +
				((((m / 0x1000000) ^ (counter / 0x1000000)) & 0xffffff) * 0x1000000);
		writeLongBE(nonce, x, base.length - 6, 6);
		return nonce;
	}

	private static byte[] buildInfo(String type, byte[] context) {
		ByteBuffer buffer = ByteBuffer.allocate(19 + type.length() + context.length);
		buffer.put("Content-Encoding: ".getBytes(), 0, 18);
		buffer.put(type.getBytes(), 0, type.length());
		buffer.put(new byte[1], 0, 1);
		buffer.put(context, 0, context.length);
		return buffer.array();
	}

	private static byte[] lengthPrefix(Key key) {
		byte[] buffer = ((ECPublicKey) key).getQ().getEncoded(false);
		byte[] b = concat(new byte[2], buffer);
		writeLongBE(b, buffer.length, 0, 2);
		return b;
	}

	private static byte[] concat(byte[]... arrays) {
		int combinedLength = 0;
		for (byte[] array : arrays) {
			if (array != null) {
				combinedLength += array.length;
			}
		}
		int lastPos = 0;
		byte[] combined = new byte[combinedLength];

		for (byte[] array : arrays) {
			if (array == null) {
				continue;
			}
			System.arraycopy(array, 0, combined, lastPos, array.length);
			lastPos += array.length;
		}

		return combined;
	}

	private static long readLongBE(byte[] buffer, int offset, int len) {
		long result = 0;
		for (int i = 0; i < len; i++) {
			result <<= 8;
			result |= (buffer[i + offset] & 0xFF);
		}
		return result;
	}

	private static void writeLongBE(byte[] buffer, long value, int offset, int len) {
		for (int i = len - 1; i >= 0; i--) {
			buffer[i + offset] = (byte) (value & 0xFF);
			value >>= 8;
		}
	}

	private enum Mode {
		ENCRYPT, DECRYPT
	}

	private static class SecretAndContext {
		private byte[] secret;
		private byte[] context;

		public SecretAndContext(byte[] secret, byte[] context) {
			this.secret = secret;
			this.context = context;
		}

		public SecretAndContext(byte[] secret) {
			this(secret, new byte[0]);
		}

		public void expandSecret(byte[] authSecret) {
			this.secret = hkdfExpand(this.secret, authSecret, buildInfo("auth", new byte[0]), SHA_256_LENGTH_BYTES);
		}
	}

	private static class KeyAndNonce {
		private byte[] key;
		private byte[] nonce;

		public KeyAndNonce(byte[] key, byte[] nonce) {
			this.key = key;
			this.nonce = nonce;
		}
	}
}