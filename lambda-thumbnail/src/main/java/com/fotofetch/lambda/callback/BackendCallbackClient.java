package com.fotofetch.lambda.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fotofetch.lambda.config.LambdaConfig;
import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.exception.TransientProcessingException;
import com.fotofetch.lambda.image.ThumbnailResult;
import com.fotofetch.lambda.model.CallbackPayload;
import com.fotofetch.lambda.model.ThumbnailJob;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

public class BackendCallbackClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LambdaConfig config;

    public BackendCallbackClient(LambdaConfig config) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper(),
                config
        );
    }

    public BackendCallbackClient(HttpClient httpClient, ObjectMapper objectMapper, LambdaConfig config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public void sendSuccessCallback(ThumbnailJob job, ThumbnailResult result) {
        long timestamp = System.currentTimeMillis();
        CallbackPayload payload = CallbackPayload.success(
                job.getPhotoId(),
                job.getEventId(),
                job.getOriginalS3Key(),
                result.getThumbnailKey(),
                result.width(),
                result.height(),
                result.fileSize(),
                timestamp
        );

        executeCallback(payload, timestamp);
    }

    public void sendFailureCallback(ThumbnailJob job, String errorMessage) {
        long timestamp = System.currentTimeMillis();
        CallbackPayload payload = CallbackPayload.failure(
                job.getPhotoId(),
                job.getEventId(),
                job.getOriginalS3Key(),
                errorMessage,
                timestamp
        );

        executeCallback(payload, timestamp);
    }

    public void executeCallback(CallbackPayload payload, long timestamp) {
        String callbackUrl = config.getBackendCallbackUrl();
        String secret = config.getCallbackSecret();

        byte[] rawJsonBytes;
        try {
            rawJsonBytes = objectMapper.writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new PermanentProcessingException("Failed to serialize callback payload: " + ex.getMessage(), ex);
        }

        // Generate HMAC-SHA256 signature over exact raw JSON bytes
        String signatureHex = computeHmacHex(timestamp, rawJsonBytes, secret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .header("Content-Type", "application/json")
                .header("X-FotoFetch-Signature", signatureHex)
                .header("X-FotoFetch-Timestamp", String.valueOf(timestamp))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(rawJsonBytes))
                .build();

        // Bounded retries for transient HTTP errors
        int maxAttempts = 3;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return; // Success!
                }

                // Capture, sanitize, and log response body for diagnostic visibility
                String rawBody = response.body();
                String sanitizedBody = sanitizeResponseBody(rawBody);
                System.err.printf("Backend callback returned HTTP %d on attempt %d/%d: %s%n",
                        status, attempt, maxAttempts, sanitizedBody);

                if (status == 401 || status == 403 || status == 429) {
                    // Transient authentication, authorization, or rate-limiting issues (e.g. clock drift, key sync delay, throttling)
                    // Allows SQS retries and eventual Dead Letter Queue routing for operational review
                    lastException = new RuntimeException("Backend returned transient HTTP " + status);
                } else if (status >= 400 && status < 500) {
                    // Permanent client errors (e.g. 400 Bad Request, 404 Not Found, 422 Unprocessable Entity)
                    // Avoid logging or propagating sensitive response body or secret details
                    throw new PermanentProcessingException("Backend callback rejected request with permanent HTTP " + status);
                } else {
                    // 5xx Server error: retry
                    lastException = new RuntimeException("Backend returned server error HTTP " + status);
                }
            } catch (PermanentProcessingException pEx) {
                throw pEx;
            } catch (IOException | InterruptedException ex) {
                lastException = ex;
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new TransientProcessingException("Callback request interrupted", ex);
                }
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TransientProcessingException("Callback retry backoff interrupted", ie);
                }
            }
        }

        // If all retries failed due to transient issues, report transient failure so SQS will retry
        throw new TransientProcessingException(
                "Backend callback unavailable or returned transient error after " + maxAttempts + " attempts: " +
                        (lastException != null ? lastException.getMessage() : "Unknown error"),
                lastException
        );
    }

    public static String computeHmacHex(long timestamp, byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    (secret != null ? secret : "").getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(keySpec);

            byte[] timestampBytes = String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8);
            byte[] separatorBytes = ".".getBytes(StandardCharsets.UTF_8);

            mac.update(timestampBytes);
            mac.update(separatorBytes);
            if (rawBody != null && rawBody.length > 0) {
                mac.update(rawBody);
            }

            byte[] hmacBytes = mac.doFinal();
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", ex);
        }
    }

    /**
     * Sanitizes and truncates a backend response body for safe diagnostic logging.
     * Strips control characters, normalizes whitespace, masks potential sensitive patterns,
     * and bounds length to a maximum of 250 characters.
     */
    public static String sanitizeResponseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "<empty>";
        }
        // Normalize newlines, carriage returns, tabs to spaces and strip non-ASCII / unprintable characters
        String sanitized = rawBody.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[^\\x20-\\x7E]", "")
                .trim();
        if (sanitized.isEmpty()) {
            return "<empty>";
        }
        // Mask any potential sensitive key-value pairs (e.g. token, secret, password, signature, authorization)
        sanitized = sanitized.replaceAll("(?i)(\"(?:token|secret|password|signature|authorization|key)\"\\s*:\\s*\")[^\"]+(\")", "$1***REDACTED***$2");
        if (sanitized.length() > 250) {
            return sanitized.substring(0, 250);
        }
        return sanitized;
    }
}
