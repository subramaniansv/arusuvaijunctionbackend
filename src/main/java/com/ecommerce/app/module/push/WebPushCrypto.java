package com.ecommerce.app.module.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Base64;

/**
 * Pure-JDK implementation of the Web Push payload encryption (RFC 8291,
 * "aes128gcm" content encoding) plus the VAPID JWT signer (RFC 8292).
 *
 * No third-party crypto library is required — the JDK ships secp256r1 (P-256),
 * ECDH, HKDF-via-HMAC-SHA256, and AES-128-GCM, which is everything Web Push
 * needs. This keeps the fat-jar small and avoids BouncyCastle version drift.
 *
 * References:
 *   RFC 8291  Message Encryption for Web Push
 *   RFC 8188  Encrypted Content-Encoding (aes128gcm)
 *   RFC 8292  VAPID
 */
public final class WebPushCrypto {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private WebPushCrypto() {}

    // ---------------------------------------------------------------------
    // Result of encrypting a payload for one subscription.
    // ---------------------------------------------------------------------
    public static final class Encrypted {
        public final byte[] body;        // the aes128gcm message (goes in HTTP body)
        Encrypted(byte[] body) { this.body = body; }
    }

    /**
     * Encrypt {@code plaintext} for the subscription identified by its
     * p256dh (client public key) and auth secret, both base64url strings.
     */
    public static Encrypted encrypt(String p256dhB64, String authB64, byte[] plaintext) throws Exception {
        byte[] uaPublic = B64D.decode(p256dhB64);   // 65 bytes, client public key
        byte[] authSecret = B64D.decode(authB64);   // 16 bytes

        // 1. Our ephemeral EC key pair (the "as" / application server key).
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair asKeyPair = kpg.generateKeyPair();
        byte[] asPublic = encodeUncompressed((ECPublicKey) asKeyPair.getPublic());

        // 2. ECDH shared secret.
        PublicKey uaPubKey = decodeUncompressed(uaPublic);
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(asKeyPair.getPrivate());
        ka.doPhase(uaPubKey, true);
        byte[] ecdhSecret = ka.generateSecret();

        // 3. Random 16-byte salt.
        byte[] salt = new byte[16];
        SecureRandom.getInstanceStrong().nextBytes(salt);

        // 4. HKDF to derive the PRK, then CEK (16B) and nonce (12B).
        //    key_info  = "WebPush: info\0" || ua_public || as_public
        //    (RFC 8291 §3.3)
        byte[] keyInfo = concat(
                "WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

        byte[] cekInfo   = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
        byte[] nonceInfo = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
        byte[] cek   = hkdf(salt, ikm, cekInfo, 16);
        byte[] nonce = hkdf(salt, ikm, nonceInfo, 12);

        // 5. aes128gcm record: content is plaintext || 0x02 (last-record delimiter).
        byte[] padded = concat(plaintext, new byte[]{0x02});
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"),
                new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        // 6. aes128gcm header (RFC 8188 §2.1):
        //    salt(16) || rs(4, big-endian) || idlen(1) || keyid(idlen) || ciphertext
        //    keyid for Web Push IS the as_public key (65 bytes).
        int rs = 4096;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(salt);
        out.write(ByteBuffer.allocate(4).putInt(rs).array());
        out.write(asPublic.length & 0xff);
        out.write(asPublic);
        out.write(ciphertext);

        return new Encrypted(out.toByteArray());
    }

    // ---------------------------------------------------------------------
    // VAPID: a signed ES256 JWT proving we own the VAPID key pair.
    // ---------------------------------------------------------------------
    /**
     * Build the "Authorization: vapid t=<jwt>, k=<publicKey>" header value.
     *
     * @param audience  the push service origin, e.g. https://fcm.googleapis.com
     * @param subject   contact mailto: or https URL (RFC 8292 recommends a mailto)
     * @param publicKeyB64   VAPID public key (base64url, 65 bytes)
     * @param privateKeyB64  VAPID private key (base64url, 32-byte raw scalar)
     */
    public static String vapidAuthHeader(String audience, String subject,
                                         String publicKeyB64, String privateKeyB64) throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + 12 * 60 * 60; // 12h — must be <= 24h

        String header = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";
        String payload = "{\"aud\":\"" + audience + "\",\"exp\":" + exp
                + ",\"sub\":\"" + subject + "\"}";

        String signingInput = b64(header.getBytes(StandardCharsets.UTF_8))
                + "." + b64(payload.getBytes(StandardCharsets.UTF_8));

        ECPrivateKey priv = loadPrivateKey(B64D.decode(privateKeyB64));
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(priv);
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        byte[] derSig = sig.sign();
        byte[] jose = derToJose(derSig, 64); // ES256 -> 64-byte R||S

        String jwt = signingInput + "." + b64(jose);
        return "vapid t=" + jwt + ", k=" + publicKeyB64;
    }

    // ---------------------------------------------------------------------
    // HKDF (RFC 5869) using HMAC-SHA256. Single-block expand is enough here
    // (all outputs are <= 32 bytes).
    // ---------------------------------------------------------------------
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        // extract
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        // expand (T(1) only)
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 0x01);
        byte[] t = mac.doFinal();
        byte[] okm = new byte[length];
        System.arraycopy(t, 0, okm, 0, length);
        return okm;
    }

    // ---------------------------------------------------------------------
    // EC key encoding helpers
    // ---------------------------------------------------------------------
    private static byte[] encodeUncompressed(ECPublicKey key) {
        byte[] x = toFixed(key.getW().getAffineX(), 32);
        byte[] y = toFixed(key.getW().getAffineY(), 32);
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(x, 0, out, 1, 32);
        System.arraycopy(y, 0, out, 33, 32);
        return out;
    }

    private static ECPublicKey decodeUncompressed(byte[] w) throws Exception {
        if (w.length != 65 || w[0] != 0x04) throw new IllegalArgumentException("bad EC point");
        BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(w, 1, 33));
        BigInteger y = new BigInteger(1, java.util.Arrays.copyOfRange(w, 33, 65));
        ECParameterSpec params = p256Params();
        KeyFactory kf = KeyFactory.getInstance("EC");
        return (ECPublicKey) kf.generatePublic(
                new ECPublicKeySpec(new ECPoint(x, y), params));
    }

    private static ECPrivateKey loadPrivateKey(byte[] scalar) throws Exception {
        BigInteger s = new BigInteger(1, scalar);
        ECParameterSpec params = p256Params();
        KeyFactory kf = KeyFactory.getInstance("EC");
        return (ECPrivateKey) kf.generatePrivate(new ECPrivateKeySpec(s, params));
    }

    private static ECParameterSpec p256Params() throws Exception {
        AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
        ap.init(new ECGenParameterSpec("secp256r1"));
        return ap.getParameterSpec(ECParameterSpec.class);
    }

    // ---------------------------------------------------------------------
    // ECDSA DER -> raw R||S (JOSE) conversion for ES256.
    // ---------------------------------------------------------------------
    private static byte[] derToJose(byte[] der, int outLen) {
        int half = outLen / 2;
        // DER: 0x30 totalLen 0x02 rLen R 0x02 sLen S
        int idx = 2; // skip 0x30, total-len (short form; always <128 for P-256)
        // R
        if (der[idx] != 0x02) throw new IllegalStateException("bad DER");
        idx++;
        int rLen = der[idx++] & 0xff;
        byte[] r = java.util.Arrays.copyOfRange(der, idx, idx + rLen);
        idx += rLen;
        if (der[idx] != 0x02) throw new IllegalStateException("bad DER");
        idx++;
        int sLen = der[idx++] & 0xff;
        byte[] s = java.util.Arrays.copyOfRange(der, idx, idx + sLen);

        byte[] out = new byte[outLen];
        copyRightAligned(stripLeadingZeros(r), out, 0, half);
        copyRightAligned(stripLeadingZeros(s), out, half, half);
        return out;
    }

    private static byte[] stripLeadingZeros(byte[] in) {
        int i = 0;
        while (i < in.length - 1 && in[i] == 0) i++;
        return java.util.Arrays.copyOfRange(in, i, in.length);
    }

    private static void copyRightAligned(byte[] src, byte[] dst, int dstOff, int len) {
        int copy = Math.min(src.length, len);
        System.arraycopy(src, src.length - copy, dst, dstOff + (len - copy), copy);
    }

    private static byte[] toFixed(BigInteger v, int len) {
        byte[] b = v.toByteArray();
        if (b.length == len) return b;
        byte[] out = new byte[len];
        if (b.length > len) System.arraycopy(b, b.length - len, out, 0, len);
        else System.arraycopy(b, 0, out, len - b.length, b.length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, o, p.length); o += p.length; }
        return out;
    }

    private static String b64(byte[] b) { return B64.encodeToString(b); }
}
