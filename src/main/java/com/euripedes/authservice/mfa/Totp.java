package com.euripedes.authservice.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** TOTP RFC 6238 (HMAC-SHA1, 6 dígitos), passo configurável (padrão 300s). */
public final class Totp {
    private Totp() {}

    public static String generate(byte[] secret, long counter) {
        try {
            byte[] msg = new byte[8];
            for (int i = 7; i >= 0; i--) {
                msg[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int o = h[h.length - 1] & 0x0F;
            int code = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16)
                     | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            return String.format("%06d", code % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("Falha TOTP", e);
        }
    }

    public static boolean verify(byte[] secret, String code, long stepSeconds, int window) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long now = System.currentTimeMillis() / 1000L / stepSeconds;
        for (long c = now - window; c <= now + window; c++) {
            if (generate(secret, c).equals(code)) return true;
        }
        return false;
    }
}
