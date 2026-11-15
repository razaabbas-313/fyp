package com.fotofetch.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fotofetch.lambda.callback.BackendCallbackClient;
import com.fotofetch.lambda.config.LambdaConfig;
import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.exception.TransientProcessingException;
import com.fotofetch.lambda.image.ThumbnailProcessor;
import com.fotofetch.lambda.image.ThumbnailResult;
import com.fotofetch.lambda.model.ThumbnailJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThumbnailHandlerTest {

    private S3Client mockS3Client;
    private ThumbnailProcessor mockProcessor;
    private BackendCallbackClient mockCallbackClient;
    private LambdaConfig config;
    private ThumbnailHandler handler;
    private Context mockContext;
    private LambdaLogger mockLogger;
    private final String bucket = "fotofetch-ai-photos-480836974477";

    @BeforeEach
    void setUp() {
        mockS3Client = mock(S3Client.class);
        mockProcessor = mock(ThumbnailProcessor.class);
        mockCallbackClient = mock(BackendCallbackClient.class);
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);

        config = new LambdaConfig(
                bucket,
                "ap-south-1",
                "http://localhost:8080/callback",
                "secret",
                50_000_000L,
                15_000,
                30L * 1024L * 1024L
        );

        handler = new ThumbnailHandler(mockS3Client, config, mockProcessor, mockCallbackClient, new ObjectMapper());
    }

    private SQSEvent createSqsEvent(List<SQSEvent.SQSMessage> messages) {
        SQSEvent event = new SQSEvent();
        event.setRecords(messages);
        return event;
    }

    private SQSEvent.SQSMessage createSqsMessage(String messageId, List<String> s3Keys) {
        SQSEvent.SQSMessage msg = new SQSEvent.SQSMessage();
        msg.setMessageId(messageId);

        StringBuilder sb = new StringBuilder("{\"Records\":[");
        for (int i = 0; i < s3Keys.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"eventName\":\"ObjectCreated:Put\",\"s3\":{\"bucket\":{\"name\":\"%s\"},\"object\":{\"key\":\"%s\"}}}",
                    bucket, s3Keys.get(i)));
        }
        sb.append("]}");

        msg.setBody(sb.toString());
        return msg;
    }

    @Test
    @DisplayName("1. Valid S3 event processes successfully, calls S3 upload and backend callback")
    void testValidS3Event() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-sunset.jpg", eventId, photoId);

        SQSEvent.SQSMessage message = createSqsMessage("msg-1", List.of(originalKey));
        SQSEvent event = createSqsEvent(List.of(message));

        ThumbnailResult mockResult = new ThumbnailResult("events/" + eventId + "/thumbnails/" + photoId + ".jpg", 1024, 768, 120000L);
        when(mockProcessor.processThumbnail(any(ThumbnailJob.class))).thenReturn(mockResult);

        SQSBatchResponse response = handler.handleRequest(event, mockContext);

        assertNotNull(response);
        assertTrue(response.getBatchItemFailures().isEmpty());

        verify(mockProcessor, times(1)).processThumbnail(any(ThumbnailJob.class));
        verify(mockCallbackClient, times(1)).sendSuccessCallback(any(ThumbnailJob.class), eq(mockResult));
    }

    @Test
    @DisplayName("2. Multiple S3 records in one SQS message are all processed")
    void testMultipleS3RecordsInOneMessage() {
        UUID eventId = UUID.randomUUID();
        UUID photo1 = UUID.randomUUID();
        UUID photo2 = UUID.randomUUID();
        String key1 = String.format("events/%s/originals/%s-photo1.jpg", eventId, photo1);
        String key2 = String.format("events/%s/originals/%s-photo2.jpg", eventId, photo2);

        SQSEvent.SQSMessage message = createSqsMessage("msg-multi", List.of(key1, key2));
        SQSEvent event = createSqsEvent(List.of(message));

        when(mockProcessor.processThumbnail(any(ThumbnailJob.class)))
                .thenReturn(new ThumbnailResult("thumb.jpg", 1024, 768, 100000L));

        SQSBatchResponse response = handler.handleRequest(event, mockContext);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(mockProcessor, times(2)).processThumbnail(any(ThumbnailJob.class));
        verify(mockCallbackClient, times(2)).sendSuccessCallback(any(ThumbnailJob.class), any());
    }

    @Test
    @DisplayName("3. Multiple SQS messages in one batch invocation are all processed")
    void testMultipleSqsMessagesInBatch() {
        UUID eventId = UUID.randomUUID();
        UUID photo1 = UUID.randomUUID();
        UUID photo2 = UUID.randomUUID();
        String key1 = String.format("events/%s/originals/%s-photo1.jpg", eventId, photo1);
        String key2 = String.format("events/%s/originals/%s-photo2.jpg", eventId, photo2);

        SQSEvent.SQSMessage msg1 = createSqsMessage("msg-1", List.of(key1));
        SQSEvent.SQSMessage msg2 = createSqsMessage("msg-2", List.of(key2));
        SQSEvent event = createSqsEvent(List.of(msg1, msg2));

        when(mockProcessor.processThumbnail(any(ThumbnailJob.class)))
                .thenReturn(new ThumbnailResult("thumb.jpg", 1024, 768, 100000L));

        SQSBatchResponse response = handler.handleRequest(event, mockContext);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(mockProcessor, times(2)).processThumbnail(any(ThumbnailJob.class));
        verify(mockCallbackClient, times(2)).sendSuccessCallback(any(ThumbnailJob.class), any());
    }

    @Test
    @DisplayName("4. SQS Partial Batch Failure: When message 2 fails with transient error, only message 2 is reported in batchItemFailures")
    void testSqsPartialBatchFailureReporting() {
        UUID eventId = UUID.randomUUID();
        UUID photo1 = UUID.randomUUID();
        UUID photo2 = UUID.randomUUID();
        String key1 = String.format("events/%s/originals/%s-photo1.jpg", eventId, photo1);
        String key2 = String.format("events/%s/originals/%s-photo2.jpg", eventId, photo2);

        SQSEvent.SQSMessage msg1 = createSqsMessage("msg-success-1", List.of(key1));
        SQSEvent.SQSMessage msg2 = createSqsMessage("msg-fail-2", List.of(key2));
        SQSEvent event = createSqsEvent(List.of(msg1, msg2));

        // Msg 1 succeeds
        ThumbnailResult result1 = new ThumbnailResult("thumb1.jpg", 1024, 768, 100000L);
        // Msg 2 fails with Transient error (e.g. S3 timeout)
        when(mockProcessor.processThumbnail(argThat(job -> job != null && photo1.equals(job.getPhotoId())))).thenReturn(result1);
        when(mockProcessor.processThumbnail(argThat(job -> job != null && photo2.equals(job.getPhotoId()))))
                .thenThrow(new TransientProcessingException("S3 503 Service Unavailable"));

        SQSBatchResponse response = handler.handleRequest(event, mockContext);

        assertNotNull(response);
        assertEquals(1, response.getBatchItemFailures().size(), "Exactly 1 failure must be reported to SQS");
        assertEquals("msg-fail-2", response.getBatchItemFailures().get(0).getItemIdentifier());

        // Msg 1 was acknowledged successfully, Msg 2 was flagged for retry
        verify(mockCallbackClient, times(1)).sendSuccessCallback(any(), any());
    }

    @Test
    @DisplayName("5. Callback failure (transient 500) results in SQS partial batch failure for message retry")
    void testCallbackTransientFailureCausesSqsRetry() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-photo.jpg", eventId, photoId);

        SQSEvent.SQSMessage message = createSqsMessage("msg-callback-fail", List.of(originalKey));
        SQSEvent event = createSqsEvent(List.of(message));

        ThumbnailResult result = new ThumbnailResult("thumb.jpg", 1024, 768, 100000L);
        when(mockProcessor.processThumbnail(any())).thenReturn(result);

        doThrow(new TransientProcessingException("Backend callback unavailable"))
                .when(mockCallbackClient).sendSuccessCallback(any(), any());

        SQSBatchResponse response = handler.handleRequest(event, mockContext);

        assertEquals(1, response.getBatchItemFailures().size());
        assertEquals("msg-callback-fail", response.getBatchItemFailures().get(0).getItemIdentifier());
    }

    @Test
    @DisplayName("6. Permanent failure (corrupt image): Sends FAILED callback and acknowledges SQS message")
    void testPermanentFailureSendsFailedCallbackAndAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-corrupt.jpg", eventId, photoId);

        SQSEvent.SQSMessage message = createSqsMessage("msg-corrupt", List.of(originalKey));
        SQSEvent event = createSqsEvent(List.of(message));

        when(mockProcessor.processThumbnail(any()))
                .thenThrow(new PermanentProcessingException("Corrupt image data: unexpected EOF"));

        SQSBatchResponse response = handler.handleRequest(event, mockContext);

        // Permanent failure MUST NOT be added to batchItemFailures so SQS deletes it
        assertTrue(response.getBatchItemFailures().isEmpty(), "Permanent failures must not trigger SQS retry");

        // Verify FAILED callback was sent to backend
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCallbackClient, times(1)).sendFailureCallback(any(ThumbnailJob.class), errorCaptor.capture());
        assertTrue(errorCaptor.getValue().contains("Corrupt image data"));
    }

    @Test
    @DisplayName("7. Duplicate S3 event: Idempotent execution generates thumbnail and sends callback cleanly")
    void testDuplicateS3EventIdempotent() {
        UUID eventId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String originalKey = String.format("events/%s/originals/%s-photo.jpg", eventId, photoId);

        SQSEvent.SQSMessage msg1 = createSqsMessage("msg-dup-1", List.of(originalKey));
        SQSEvent.SQSMessage msg2 = createSqsMessage("msg-dup-2", List.of(originalKey));
        SQSEvent event = createSqsEvent(List.of(msg1, msg2));

        ThumbnailResult result = new ThumbnailResult("thumb.jpg", 1024, 768, 100000L);
        when(mockProcessor.processThumbnail(any())).thenReturn(result);

        SQSBatchResponse response = handler.handleRequest(event, mockContext);

        assertTrue(response.getBatchItemFailures().isEmpty());
        // Both invocations executed deterministically without exceptions
        verify(mockProcessor, times(2)).processThumbnail(any());
        verify(mockCallbackClient, times(2)).sendSuccessCallback(any(), any());
    }
}
