package com.chanakya.shl.util;

import java.util.Base64;

public final class Base64UrlUtil {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private Base64UrlUtil() {
    }

    public static String encode(byte[] data) {
        return ENCODER.encodeToString(data);
    }

    public static String encode(String data) {
        return ENCODER.encodeToString(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static byte[] decode(String base64Url) {
        return DECODER.decode(base64Url);
    }

    public static String decodeToString(String base64Url) {
        return new String(DECODER.decode(base64Url), java.nio.charset.StandardCharsets.UTF_8);
    }
}
