package com.fotofetch.lambda.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fotofetch.lambda.config.LambdaConfig;
import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.exception.TransientProcessingException;
import com.fotofetch.lambda.image.ThumbnailResult;
import com.fotofetch.lambda.model.CallbackPayload;
import com.fotofetch.lambda.model.ThumbnailJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BackendCallbackClientTest {

    private HttpClient mockHttpClient;
    private HttpResponse<String> mockHttpResponse;
    private LambdaConfig config;
    private BackendCallbackClient client;
    private final String secret = "test-super-secret-hmac-key-256-bits";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockHttpResponse = (HttpResponse<String>) mock(HttpResponse.class);
        config = new LambdaConfig(
                "fotofetch-ai-photos-480836974477",
                "ap-south-1",
                "https://fotofetch-backend.onrender.com/api/internal/photos/thumbnail-callback",
                secret,
                50_000_000L,
                15_000,
                30L * 1024L * 1024L
        );
        client = new BackendCallbackClient(mockHttpClient, new ObjectMapper(), config);
    }

    @Test
    @DisplayName("HMAC-SHA256 signature is calculated correctly over timestamp + '.' + rawBody")
    void testHmacCalculation() throws Exception {
        long timestamp = 1731658800000L;
        byte[] rawBody = "{\"photoId\":\"12345\"}".getBytes(StandardCharsets.UTF_8);

        String computedHex = BackendCallbackClient.computeHmacHex(timestamp, rawBody, secret);

        // Compute independently to verify
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8));
        mac.update(".".getBytes(StandardCharsets.UTF_8));
        mac.update(rawBody);
        String expectedHex = HexFormat.of().formatHex(mac.doFinal());

        assertEquals(expectedHex, computedHex);
    }

    @Test
    @DisplayName("Success callback sends POST with required headers and succeeds on HTTP 200")
    void testSuccessCallback() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + eventId + "/originals/" + photoId + "-pic.jpg";
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, "test-bucket", originalKey, "pic.jpg", "msg-1");
        ThumbnailResult result = new ThumbnailResult("events/" + eventId + "/thumbnails/" + photoId + ".jpg", 1024, 768, 150000L);

        when(mockHttpResponse.statusCode()).thenReturn(200);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        assertDoesNotThrow(() -> client.sendSuccessCallback(job, result));

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient, times(1)).send(requestCaptor.capture(), any());

        HttpRequest sentRequest = requestCaptor.getValue();
        assertEquals("POST", sentRequest.method());
        assertEquals(config.getBackendCallbackUrl(), sentRequest.uri().toString());
        assertTrue(sentRequest.headers().firstValue("X-FotoFetch-Signature").isPresent());
        assertTrue(sentRequest.headers().firstValue("X-FotoFetch-Timestamp").isPresent());
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    @DisplayName("Backend returning HTTP 400 throws PermanentProcessingException without retries and without leaking body")
    void testBackend400ThrowsPermanentException() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + eventId + "/originals/" + photoId + "-pic.jpg";
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, "test-bucket", originalKey, "pic.jpg", "msg-1");
        ThumbnailResult result = new ThumbnailResult("events/" + eventId + "/thumbnails/" + photoId + ".jpg", 1024, 768, 150000L);

        when(mockHttpResponse.statusCode()).thenReturn(400);
        when(mockHttpResponse.body()).thenReturn("{\"message\":\"Bad Request with sensitive token xyz\"}");
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        PermanentProcessingException ex = assertThrows(
                PermanentProcessingException.class,
                () -> client.sendSuccessCallback(job, result)
        );
        assertTrue(ex.getMessage().contains("HTTP 400"));
        assertFalse(ex.getMessage().contains("sensitive token xyz"));
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Backend returning HTTP 401 throws TransientProcessingException after retries (allowing DLQ routing)")
    void testBackend401ThrowsTransientException() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + eventId + "/originals/" + photoId + "-pic.jpg";
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, "test-bucket", originalKey, "pic.jpg", "msg-1");
        ThumbnailResult result = new ThumbnailResult("events/" + eventId + "/thumbnails/" + photoId + ".jpg", 1024, 768, 150000L);

        when(mockHttpResponse.statusCode()).thenReturn(401);
        when(mockHttpResponse.body()).thenReturn("{\"error\":\"Unauthorized secret-leak-check\"}");
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        TransientProcessingException ex = assertThrows(
                TransientProcessingException.class,
                () -> client.sendSuccessCallback(job, result)
        );
        assertTrue(ex.getMessage().contains("HTTP 401"));
        assertFalse(ex.getMessage().contains("secret-leak-check"));
        verify(mockHttpClient, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Backend returning HTTP 403 throws TransientProcessingException after retries")
    void testBackend403ThrowsTransientException() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + eventId + "/originals/" + photoId + "-pic.jpg";
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, "test-bucket", originalKey, "pic.jpg", "msg-1");
        ThumbnailResult result = new ThumbnailResult("events/" + eventId + "/thumbnails/" + photoId + ".jpg", 1024, 768, 150000L);

        when(mockHttpResponse.statusCode()).thenReturn(403);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        TransientProcessingException ex = assertThrows(
                TransientProcessingException.class,
                () -> client.sendSuccessCallback(job, result)
        );
        assertTrue(ex.getMessage().contains("HTTP 403"));
        verify(mockHttpClient, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Backend returning HTTP 429 throws TransientProcessingException after retries")
    void testBackend429ThrowsTransientException() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + eventId + "/originals/" + photoId + "-pic.jpg";
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, "test-bucket", originalKey, "pic.jpg", "msg-1");
        ThumbnailResult result = new ThumbnailResult("events/" + eventId + "/thumbnails/" + photoId + ".jpg", 1024, 768, 150000L);

        when(mockHttpResponse.statusCode()).thenReturn(429);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        TransientProcessingException ex = assertThrows(
                TransientProcessingException.class,
                () -> client.sendSuccessCallback(job, result)
        );
        assertTrue(ex.getMessage().contains("HTTP 429"));
        verify(mockHttpClient, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Backend returning HTTP 500 throws TransientProcessingException after retries")
    void testBackend500ThrowsTransientException() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + eventId + "/originals/" + photoId + "-pic.jpg";
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, "test-bucket", originalKey, "pic.jpg", "msg-1");
        ThumbnailResult result = new ThumbnailResult("events/" + eventId + "/thumbnails/" + photoId + ".jpg", 1024, 768, 150000L);

        when(mockHttpResponse.statusCode()).thenReturn(500);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        TransientProcessingException ex = assertThrows(
                TransientProcessingException.class,
                () -> client.sendSuccessCallback(job, result)
        );
        assertTrue(ex.getMessage().contains("HTTP 500"));
        verify(mockHttpClient, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("Network IOException throws TransientProcessingException")
    void testNetworkFailureThrowsTransientException() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = "events/" + eventId + "/originals/" + photoId + "-pic.jpg";
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, "test-bucket", originalKey, "pic.jpg", "msg-1");

        when(mockHttpClient.send(any(HttpRequest.class), any())).thenThrow(new IOException("Connection refused"));

        TransientProcessingException ex = assertThrows(
                TransientProcessingException.class,
                () -> client.sendFailureCallback(job, "Some error")
        );
        assertTrue(ex.getMessage().contains("unavailable") || ex.getMessage().contains("Connection refused"));
    }

    @Test
    @DisplayName("CallbackPayload includes unique callbackId for replay protection")
    void testCallbackPayloadIncludesCallbackId() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        CallbackPayload successPayload = CallbackPayload.success(photoId, eventId, "orig.jpg", "thumb.jpg", 1024, 768, 1000L, System.currentTimeMillis());
        assertNotNull(successPayload.getCallbackId());
        assertFalse(successPayload.getCallbackId().isBlank());

        CallbackPayload failurePayload = CallbackPayload.failure(photoId, eventId, "orig.jpg", "Error", System.currentTimeMillis());
        assertNotNull(failurePayload.getCallbackId());
        assertFalse(failurePayload.getCallbackId().isBlank());
        assertNotEquals(successPayload.getCallbackId(), failurePayload.getCallbackId());
    }

    @Test
    @DisplayName("sanitizeResponseBody handles null, empty, and whitespace strings safely")
    void testSanitizeResponseBodyNullAndBlank() {
        assertEquals("<empty>", BackendCallbackClient.sanitizeResponseBody(null));
        assertEquals("<empty>", BackendCallbackClient.sanitizeResponseBody(""));
        assertEquals("<empty>", BackendCallbackClient.sanitizeResponseBody("   \t\r\n  "));
    }

    @Test
    @DisplayName("sanitizeResponseBody strips newlines, tabs, and unprintable characters")
    void testSanitizeResponseBodyStripsControlCharacters() {
        String input = "Line1\r\nLine2\tLine3\u0000\u0007End";
        String sanitized = BackendCallbackClient.sanitizeResponseBody(input);
        assertEquals("Line1 Line2 Line3End", sanitized);
        assertFalse(sanitized.contains("\n"));
        assertFalse(sanitized.contains("\r"));
    }

    @Test
    @DisplayName("sanitizeResponseBody truncates strings to a maximum of 250 characters")
    void testSanitizeResponseBodyTruncation() {
        String longInput = "A".repeat(400);
        String sanitized = BackendCallbackClient.sanitizeResponseBody(longInput);
        assertEquals(250, sanitized.length());
        assertEquals("A".repeat(250), sanitized);
    }

    @Test
    @DisplayName("sanitizeResponseBody masks sensitive keys (token, secret, password, etc.)")
    void testSanitizeResponseBodyRedactsSensitiveKeys() {
        String input = "{\"token\":\"super_secret_jwt\",\"secret\":\"api_key_123\",\"message\":\"normal text\"}";
        String sanitized = BackendCallbackClient.sanitizeResponseBody(input);
        assertTrue(sanitized.contains("\"token\":\"***REDACTED***\""));
        assertTrue(sanitized.contains("\"secret\":\"***REDACTED***\""));
        assertTrue(sanitized.contains("\"message\":\"normal text\""));
        assertFalse(sanitized.contains("super_secret_jwt"));
        assertFalse(sanitized.contains("api_key_123"));
    }
}
