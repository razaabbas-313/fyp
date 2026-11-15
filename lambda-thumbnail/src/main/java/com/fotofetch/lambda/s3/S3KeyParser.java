package com.fotofetch.lambda.s3;

import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.model.ThumbnailJob;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3KeyParser {

    // Matches: events/{eventId}/originals/{photoId}-{filename}
    private static final Pattern ORIGINAL_PHOTO_PATTERN = Pattern.compile(
            "^events/([0-9a-fA-F\\-]{36})/originals/([0-9a-fA-F\\-]{36})-(.+)$"
    );

    /**
     * Decodes URL-encoded S3 keys correctly, replacing '+' with spaces before decoding.
     */
    public static String decodeS3Key(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        try {
            // S3 event notifications encode spaces as '+' or '%20'
            return URLDecoder.decode(rawKey.replace("+", " "), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return rawKey;
        }
    }

    /**
     * Parses an S3 key and returns a ThumbnailJob if the key matches the original photo pattern.
     * Safely returns Optional.empty() if the key is a thumbnail or unrelated object.
     * Throws PermanentProcessingException if the key is under originals but contains invalid UUIDs.
     */
    public static Optional<ThumbnailJob> parseOriginalKey(String rawKey, String bucket, String messageId) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }

        String decodedKey = decodeS3Key(rawKey.trim());

        // 1. Explicitly ignore thumbnails or non-event keys safely
        if (isThumbnailKey(decodedKey) || !decodedKey.startsWith("events/")) {
            return Optional.empty();
        }

        // 2. Check if under /originals/
        if (!decodedKey.contains("/originals/")) {
            return Optional.empty();
        }

        // 3. Match against expected pattern: events/{eventId}/originals/{photoId}-{filename}
        Matcher matcher = ORIGINAL_PHOTO_PATTERN.matcher(decodedKey);
        if (!matcher.matches()) {
            // It is under events/.../originals/ but has a malformed UUID or structure
            throw new PermanentProcessingException("Invalid S3 original key format or malformed UUID: " + decodedKey);
        }

        String eventIdStr = matcher.group(1);
        String photoIdStr = matcher.group(2);
        String filename = matcher.group(3);

        UUID eventId;
        UUID photoId;
        try {
            eventId = UUID.fromString(eventIdStr);
            photoId = UUID.fromString(photoIdStr);
        } catch (IllegalArgumentException ex) {
            throw new PermanentProcessingException("Malformed UUID in S3 key: " + decodedKey, ex);
        }

        return Optional.of(new ThumbnailJob(photoId, eventId, bucket, decodedKey, filename, messageId));
    }

    public static boolean isThumbnailKey(String key) {
        return key != null && (key.contains("/thumbnails/") || key.startsWith("thumbnails/"));
    }
}
