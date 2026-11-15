package com.fotofetch.lambda.s3;

import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.model.ThumbnailJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class S3KeyParserTest {

    private final String bucket = "fotofetch-ai-photos-480836974477";
    private final String messageId = "msg-12345";

    @Test
    @DisplayName("Valid S3 original photo key parses correctly")
    void testValidS3Key() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String key = String.format("events/%s/originals/%s-photo.jpg", eventId, photoId);

        Optional<ThumbnailJob> jobOpt = S3KeyParser.parseOriginalKey(key, bucket, messageId);

        assertTrue(jobOpt.isPresent());
        ThumbnailJob job = jobOpt.get();
        assertEquals(eventId, job.getEventId());
        assertEquals(photoId, job.getPhotoId());
        assertEquals(bucket, job.getS3Bucket());
        assertEquals(key, job.getOriginalS3Key());
        assertEquals("photo.jpg", job.getFilename());
        assertEquals(String.format("events/%s/thumbnails/%s.jpg", eventId, photoId), job.getExpectedThumbnailS3Key());
    }

    @Test
    @DisplayName("Thumbnail key is ignored safely without throwing exceptions")
    void testThumbnailKeyIgnoredSafely() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String thumbnailKey = String.format("events/%s/thumbnails/%s.jpg", eventId, photoId);

        Optional<ThumbnailJob> jobOpt = S3KeyParser.parseOriginalKey(thumbnailKey, bucket, messageId);

        assertFalse(jobOpt.isPresent());
    }

    @Test
    @DisplayName("URL-encoded filename with pluses, spaces, and parentheses is decoded correctly")
    void testUrlEncodedFilenameDecoded() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        // S3 event URL encodes spaces as '+' or '%20', parentheses as '%28' and '%29'
        String rawKey = String.format("events/%s/originals/%s-wedding+shot%%20%%281%%29.jpg", eventId, photoId);

        Optional<ThumbnailJob> jobOpt = S3KeyParser.parseOriginalKey(rawKey, bucket, messageId);

        assertTrue(jobOpt.isPresent());
        ThumbnailJob job = jobOpt.get();
        assertEquals("wedding shot (1).jpg", job.getFilename());
        assertEquals(String.format("events/%s/originals/%s-wedding shot (1).jpg", eventId, photoId), job.getOriginalS3Key());
    }

    @Test
    @DisplayName("Invalid UUID format in original key throws PermanentProcessingException")
    void testInvalidUuidThrowsPermanentException() {
        String invalidKey = "events/not-a-uuid/originals/also-not-uuid-image.jpg";

        PermanentProcessingException ex = assertThrows(
                PermanentProcessingException.class,
                () -> S3KeyParser.parseOriginalKey(invalidKey, bucket, messageId)
        );
        assertTrue(ex.getMessage().contains("Invalid S3 original key format") || ex.getMessage().contains("Malformed UUID"));
    }

    @Test
    @DisplayName("Unrelated S3 object key outside events is ignored safely")
    void testUnrelatedKeyIgnored() {
        String unrelated = "public/avatars/user-profile.png";

        Optional<ThumbnailJob> jobOpt = S3KeyParser.parseOriginalKey(unrelated, bucket, messageId);

        assertFalse(jobOpt.isPresent());
    }

    @Test
    @DisplayName("Non-original key inside events is ignored safely")
    void testNonOriginalEventKeyIgnored() {
        UUID eventId = UUID.randomUUID();
        String bannerKey = String.format("events/%s/banner.png", eventId);

        Optional<ThumbnailJob> jobOpt = S3KeyParser.parseOriginalKey(bannerKey, bucket, messageId);

        assertFalse(jobOpt.isPresent());
    }
}
