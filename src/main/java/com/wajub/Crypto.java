package com.wajub;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class Crypto {

    private Crypto() {}

    static String hmacSha256(String key, String data) {
        return hmacSha256(key, data.getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256(String key, byte[]... parts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            for (byte[] part : parts) {
                mac.update(part);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    static boolean timingSafeEqual(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
