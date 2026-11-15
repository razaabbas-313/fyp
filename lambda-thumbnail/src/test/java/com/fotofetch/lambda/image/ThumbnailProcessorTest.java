package com.fotofetch.lambda.image;

import com.fotofetch.lambda.config.LambdaConfig;
import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.exception.TransientProcessingException;
import com.fotofetch.lambda.model.ThumbnailJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThumbnailProcessorTest {

    private S3Client mockS3Client;
    private LambdaConfig config;
    private ThumbnailProcessor processor;
    private final String bucket = "fotofetch-ai-photos-480836974477";

    @BeforeEach
    void setUp() {
        mockS3Client = mock(S3Client.class);
        config = new LambdaConfig(
                bucket,
                "ap-south-1",
                "http://localhost:8080/callback",
                "secret",
                50_000_000L,
                15_000,
                30L * 1024L * 1024L
        );
        processor = new ThumbnailProcessor(mockS3Client, config);
    }

    private byte[] createTestJpegBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    private ResponseInputStream<GetObjectResponse> createMockS3Stream(byte[] bytes) {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength((long) bytes.length)
                .contentType("image/jpeg")
                .build();
        return new ResponseInputStream<>(response, AbortableInputStream.create(new ByteArrayInputStream(bytes)));
    }

    @Test
    @DisplayName("Valid JPEG original is resized to max 1024x1024, uploaded to S3, and dimensions reported")
    void testValidJpegResizedAndUploaded() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-photo.jpg", eventId, photoId);
        String expectedThumbKey = String.format("events/%s/thumbnails/%s.jpg", eventId, photoId);
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, bucket, originalKey, "photo.jpg", "msg-1");

        // Create 2000x1500 JPEG (4:3 aspect ratio)
        byte[] originalBytes = createTestJpegBytes(2000, 1500);
        when(mockS3Client.getObject(any(GetObjectRequest.class))).thenReturn(createMockS3Stream(originalBytes));

        ThumbnailResult result = processor.processThumbnail(job);

        assertNotNull(result);
        assertEquals(expectedThumbKey, result.getThumbnailKey());
        assertTrue(result.width() <= 1024);
        assertTrue(result.height() <= 1024);
        // 2000x1500 scaled to 1024 should be 1024x768
        assertEquals(1024, result.width());
        assertEquals(768, result.height());
        assertTrue(result.fileSize() > 0);

        // Verify S3 PutObject call
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client, times(1)).putObject(putCaptor.capture(), any(RequestBody.class));

        PutObjectRequest putRequest = putCaptor.getValue();
        assertEquals(bucket, putRequest.bucket());
        assertEquals(expectedThumbKey, putRequest.key());
        assertEquals("image/jpeg", putRequest.contentType());
    }

    @Test
    @DisplayName("Corrupt image data throws PermanentProcessingException")
    void testCorruptImageThrowsPermanentException() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-corrupt.jpg", eventId, photoId);
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, bucket, originalKey, "corrupt.jpg", "msg-1");

        byte[] corruptBytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        when(mockS3Client.getObject(any(GetObjectRequest.class))).thenReturn(createMockS3Stream(corruptBytes));

        PermanentProcessingException ex = assertThrows(
                PermanentProcessingException.class,
                () -> processor.processThumbnail(job)
        );
        assertTrue(ex.getMessage().contains("Unsupported") || ex.getMessage().contains("Corrupt") || ex.getMessage().contains("failed decoding"));
    }

    @Test
    @DisplayName("Unsupported file format (e.g. text file) throws PermanentProcessingException")
    void testUnsupportedFormatThrowsPermanentException() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-text.txt", eventId, photoId);
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, bucket, originalKey, "text.txt", "msg-1");

        byte[] textBytes = "This is a plain text file, not an image.".getBytes();
        when(mockS3Client.getObject(any(GetObjectRequest.class))).thenReturn(createMockS3Stream(textBytes));

        assertThrows(PermanentProcessingException.class, () -> processor.processThumbnail(job));
    }

    @Test
    @DisplayName("Missing S3 original object throws PermanentProcessingException")
    void testMissingS3ObjectThrowsPermanentException() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-missing.jpg", eventId, photoId);
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, bucket, originalKey, "missing.jpg", "msg-1");

        when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist.").build());

        PermanentProcessingException ex = assertThrows(
                PermanentProcessingException.class,
                () -> processor.processThumbnail(job)
        );
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("Transient S3 error (HTTP 500 / 503) throws TransientProcessingException")
    void testTransientS3ErrorThrowsTransientException() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-photo.jpg", eventId, photoId);
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, bucket, originalKey, "photo.jpg", "msg-1");

        S3Exception transientError = (S3Exception) S3Exception.builder()
                .statusCode(503)
                .message("Service Unavailable")
                .build();
        when(mockS3Client.getObject(any(GetObjectRequest.class))).thenThrow(transientError);

        TransientProcessingException ex = assertThrows(
                TransientProcessingException.class,
                () -> processor.processThumbnail(job)
        );
        assertTrue(ex.getMessage().contains("Transient S3 error"));
    }

    @Test
    @DisplayName("Decompression bomb protection rejects excessively large dimensions")
    void testDecompressionBombRejected() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-huge.jpg", eventId, photoId);
        ThumbnailJob job = new ThumbnailJob(photoId, eventId, bucket, originalKey, "huge.jpg", "msg-1");

        // Configure tight pixel threshold (e.g. 5,000 pixels)
        LambdaConfig strictConfig = new LambdaConfig(
                bucket,
                "ap-south-1",
                "http://localhost:8080/callback",
                "secret",
                5_000L, // Max 5,000 pixels
                15_000,
                30L * 1024L * 1024L
        );
        ThumbnailProcessor strictProcessor = new ThumbnailProcessor(mockS3Client, strictConfig);

        // Create 200x200 JPEG (40,000 pixels > 5,000 limit)
        byte[] originalBytes = createTestJpegBytes(200, 200);
        when(mockS3Client.getObject(any(GetObjectRequest.class))).thenReturn(createMockS3Stream(originalBytes));

        PermanentProcessingException ex = assertThrows(
                PermanentProcessingException.class,
                () -> strictProcessor.processThumbnail(job)
        );
        assertTrue(ex.getMessage().contains("Decompression bomb protection"));
    }
}
