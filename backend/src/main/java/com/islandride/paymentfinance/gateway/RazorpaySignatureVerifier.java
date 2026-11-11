package com.islandride.paymentfinance.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

public final class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RazorpaySignatureVerifier() {
    }

    public static boolean verifyCheckoutSignature(String orderId,
                                                  String paymentId,
                                                  String actualSignature,
                                                  String keySecret) {
        if (isBlank(orderId) || isBlank(paymentId) || isBlank(actualSignature) || isBlank(keySecret)) {
            return false;
        }
        String payload = orderId.trim() + "|" + paymentId.trim();
        return verifyHmacHex(payload, actualSignature.trim(), keySecret);
    }

    public static boolean verifyWebhookSignature(String rawPayload,
                                                 String actualSignature,
                                                 String webhookSecret) {
        if (rawPayload == null || isBlank(actualSignature) || isBlank(webhookSecret)) {
            return false;
        }
        return verifyHmacHex(rawPayload, actualSignature.trim(), webhookSecret);
    }

    public static String computeHmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(rawHmac);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256 signature", ex);
        }
    }

    private static boolean verifyHmacHex(String payload, String actualSignature, String secret) {
        String expectedHex = computeHmacHex(payload, secret);
        byte[] expectedBytes = expectedHex.getBytes(StandardCharsets.US_ASCII);
        byte[] actualBytes = actualSignature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

