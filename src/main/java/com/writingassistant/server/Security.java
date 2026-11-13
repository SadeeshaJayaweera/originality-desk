package com.writingassistant.server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

final class Security {
    static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DUMMY = password("invalid-password-for-timing-only");
    private Security() {}

    static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    static String password(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    static boolean verify(String password, String encoded) {
        String[] parts = (encoded == null ? DUMMY : encoded).split(":");
        int iterations = Integer.parseInt(parts[0]);
        if (iterations < ITERATIONS || iterations > 1_000_000) return false;
        byte[] actual = derive(password, Base64.getDecoder().decode(parts[1]), iterations);
        boolean equal = MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[2]));
        return encoded != null && equal;
    }
    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }

    /** Bounded in-memory fixed-window limiter. Proxy headers are intentionally not trusted. */
    static final class Limiter {
        private record Window(long minute, int count) {}
        private final Map<String, Window> windows = new LinkedHashMap<>();
        synchronized void take(String key, int max) {
            long minute = System.currentTimeMillis() / 60_000;
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
            Window old = windows.get(key);
            if (old == null && windows.size() >= 4096)
                throw ApiException.limit("Service rate limit reached");
            if (old != null && old.count() >= max) throw ApiException.limit("Too many requests; try again later");
            windows.put(key, new Window(minute, old == null ? 1 : old.count() + 1));
        }
    }
}
