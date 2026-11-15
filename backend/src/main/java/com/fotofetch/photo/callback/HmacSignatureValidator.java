package com.fotofetch.photo.callback;

import com.fotofetch.common.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Slf4j
public class HmacSignatureValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_AGE_MS = 5 * 60 * 1000L; // 5 minutes

    @Value("${fotofetch.lambda.callback-secret:}")
    private String callbackSecret;

    public void validate(String signature, Long timestamp, byte[] rawBody) {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            log.error("Rejecting callback: fotofetch.lambda.callback-secret is not configured.");
            throw new UnauthorizedException("Lambda callback authentication is not configured on this server");
        }

        if (signature == null || signature.isBlank()) {
            throw new UnauthorizedException("Missing callback signature header (X-FotoFetch-Signature)");
        }

        if (timestamp == null) {
            throw new UnauthorizedException("Missing callback timestamp header (X-FotoFetch-Timestamp)");
        }

        long now = System.currentTimeMillis();
        long ageMs = now - timestamp;
        if (Math.abs(ageMs) > MAX_AGE_MS) {
            log.warn("Callback timestamp is outside the 5-minute validity window. timestamp: {}, now: {}, diff: {}ms",
                    timestamp, now, ageMs);
            throw new UnauthorizedException("Callback timestamp expired or outside acceptable window");
        }

        byte[] expectedHmacBytes = computeHmac(timestamp, rawBody, callbackSecret);
        String expectedHex = HexFormat.of().formatHex(expectedHmacBytes);

        String cleanReceived = signature.trim();
        if (cleanReceived.toLowerCase().startsWith("sha256=")) {
            cleanReceived = cleanReceived.substring(7);
        }

        byte[] expectedHexBytes = expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] receivedHexBytes = cleanReceived.toLowerCase().getBytes(StandardCharsets.UTF_8);

        // Constant-time signature comparison to prevent timing side-channel attacks
        boolean matches = MessageDigest.isEqual(expectedHexBytes, receivedHexBytes);
        if (!matches) {
            // Note: Never log the secret or the received signature value!
            log.warn("HMAC signature verification failed for incoming thumbnail callback");
            throw new UnauthorizedException("Invalid callback signature");
        }
    }

    public static byte[] computeHmac(long timestamp, byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            // Construct exact message: timestamp + "." + rawBody
            byte[] timestampBytes = String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8);
            byte[] separatorBytes = ".".getBytes(StandardCharsets.UTF_8);

            mac.update(timestampBytes);
            mac.update(separatorBytes);
            if (rawBody != null && rawBody.length > 0) {
                mac.update(rawBody);
            }

            return mac.doFinal();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compute HMAC signature: " + ex.getMessage(), ex);
        }
    }

    public static String calculateSignatureHex(long timestamp, byte[] rawBody, String secret) {
        byte[] hmacBytes = computeHmac(timestamp, rawBody, secret);
        return HexFormat.of().formatHex(hmacBytes);
    }
}
