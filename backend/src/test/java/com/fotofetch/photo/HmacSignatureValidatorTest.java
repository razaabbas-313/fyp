package com.fotofetch.photo;

import com.fotofetch.common.UnauthorizedException;
import com.fotofetch.photo.callback.HmacSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignatureValidatorTest {

    private HmacSignatureValidator validator;
    private final String testSecret = "my-super-secret-hmac-key-256-bits-test";

    @BeforeEach
    void setUp() {
        validator = new HmacSignatureValidator();
        ReflectionTestUtils.setField(validator, "callbackSecret", testSecret);
    }

    @Test
    @DisplayName("Valid signature with raw bytes matches successfully")
    void testValidSignature() {
        byte[] rawBody = "{\"photoId\":\"12345\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, testSecret);

        assertDoesNotThrow(() -> validator.validate(signature, timestamp, rawBody));
    }

    @Test
    @DisplayName("Valid signature with sha256= prefix matches successfully")
    void testValidSignatureWithPrefix() {
        byte[] rawBody = "{\"photoId\":\"12345\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = System.currentTimeMillis();
        String signature = "sha256=" + HmacSignatureValidator.calculateSignatureHex(timestamp, rawBody, testSecret);

        assertDoesNotThrow(() -> validator.validate(signature, timestamp, rawBody));
    }

    @Test
    @DisplayName("Tampered payload is rejected")
    void testTamperedPayloadRejected() {
        byte[] originalBody = "{\"photoId\":\"12345\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"photoId\":\"99999\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = System.currentTimeMillis();
        String signature = HmacSignatureValidator.calculateSignatureHex(timestamp, originalBody, testSecret);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> validator.validate(signature, timestamp, tamperedBody));
        assertTrue(ex.getMessage().contains("Invalid callback signature"));
    }

    @Test
    @DisplayName("Missing secret on server fails safely")
    void testMissingSecretFailsSafely() {
        ReflectionTestUtils.setField(validator, "callbackSecret", "");
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);
        long timestamp = System.currentTimeMillis();

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> validator.validate("sig", timestamp, rawBody));
        assertTrue(ex.getMessage().contains("not configured"));
    }

    @Test
    @DisplayName("Missing signature header throws UnauthorizedException")
    void testMissingSignature() {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);
        long timestamp = System.currentTimeMillis();

        assertThrows(UnauthorizedException.class, () -> validator.validate(null, timestamp, rawBody));
        assertThrows(UnauthorizedException.class, () -> validator.validate("   ", timestamp, rawBody));
    }

    @Test
    @DisplayName("Missing timestamp header throws UnauthorizedException")
    void testMissingTimestamp() {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);

        assertThrows(UnauthorizedException.class, () -> validator.validate("some_sig", null, rawBody));
    }

    @Test
    @DisplayName("Expired timestamp (> 5 minutes past) is rejected")
    void testExpiredTimestamp() {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);
        long oldTimestamp = System.currentTimeMillis() - (10 * 60 * 1000L); // 10 minutes in the past
        String signature = HmacSignatureValidator.calculateSignatureHex(oldTimestamp, rawBody, testSecret);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> validator.validate(signature, oldTimestamp, rawBody));
        assertTrue(ex.getMessage().contains("expired") || ex.getMessage().contains("outside"));
    }

    @Test
    @DisplayName("Future timestamp (> 5 minutes future) is rejected")
    void testFutureTimestamp() {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);
        long futureTimestamp = System.currentTimeMillis() + (10 * 60 * 1000L); // 10 minutes in the future
        String signature = HmacSignatureValidator.calculateSignatureHex(futureTimestamp, rawBody, testSecret);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> validator.validate(signature, futureTimestamp, rawBody));
        assertTrue(ex.getMessage().contains("expired") || ex.getMessage().contains("outside"));
    }
}
