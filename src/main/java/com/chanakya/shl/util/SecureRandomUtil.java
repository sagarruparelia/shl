package com.chanakya.shl.util;

import java.security.SecureRandom;

public final class SecureRandomUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureRandomUtil() {
    }

    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static String generateBase64UrlRandom(int byteLength) {
        return Base64UrlUtil.encode(generateRandomBytes(byteLength));
    }
}
