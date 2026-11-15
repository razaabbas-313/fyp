package com.fotofetch.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fotofetch.lambda.callback.BackendCallbackClient;
import com.fotofetch.lambda.config.LambdaConfig;
import com.fotofetch.lambda.exception.PermanentProcessingException;
import com.fotofetch.lambda.exception.TransientProcessingException;
import com.fotofetch.lambda.image.ThumbnailProcessor;
import com.fotofetch.lambda.image.ThumbnailResult;
import com.fotofetch.lambda.model.ThumbnailJob;
import com.fotofetch.lambda.s3.S3KeyParser;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ThumbnailHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final S3Client s3Client;
    private final LambdaConfig config;
    private final ThumbnailProcessor thumbnailProcessor;
    private final BackendCallbackClient callbackClient;
    private final ObjectMapper objectMapper;

    /**
     * Default constructor invoked by the AWS Lambda runtime.
     */
    public ThumbnailHandler() {
        this(new LambdaConfig());
    }

    public ThumbnailHandler(LambdaConfig config) {
        this(
                S3Client.builder()
                        .region(Region.of(config.getAwsRegion()))
                        .httpClientBuilder(UrlConnectionHttpClient.builder())
                        .build(),
                config
        );
    }

    public ThumbnailHandler(S3Client s3Client, LambdaConfig config) {
        this(
                s3Client,
                config,
                new ThumbnailProcessor(s3Client, config),
                new BackendCallbackClient(config),
                new ObjectMapper()
        );
    }

    public ThumbnailHandler(S3Client s3Client,
                            LambdaConfig config,
                            ThumbnailProcessor thumbnailProcessor,
                            BackendCallbackClient callbackClient,
                            ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.config = config;
        this.thumbnailProcessor = thumbnailProcessor;
        this.callbackClient = callbackClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent sqsEvent, Context context) {
        LambdaLogger logger = context != null ? context.getLogger() : null;
        log(logger, "Processing SQS batch containing " + (sqsEvent.getRecords() != null ? sqsEvent.getRecords().size() : 0) + " messages");

        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();

        if (sqsEvent.getRecords() == null || sqsEvent.getRecords().isEmpty()) {
            return SQSBatchResponse.builder().withBatchItemFailures(batchItemFailures).build();
        }

        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            String messageId = message.getMessageId();
            try {
                processMessage(message, logger);
            } catch (PermanentProcessingException pEx) {
                log(logger, String.format("[PERMANENT FAILURE] messageId: %s, error: %s", messageId, pEx.getMessage()));
                // Acknowledge permanent failures so SQS does not retry indefinitely
            } catch (TransientProcessingException tEx) {
                log(logger, String.format("[TRANSIENT FAILURE] messageId: %s, error: %s. Flagging for SQS retry.", messageId, tEx.getMessage()));
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(messageId));
            } catch (Exception ex) {
                log(logger, String.format("[UNEXPECTED TRANSIENT FAILURE] messageId: %s, error: %s. Flagging for SQS retry.", messageId, ex.getMessage()));
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(messageId));
            }
        }

        log(logger, "Batch processing complete. Total failures reported to SQS: " + batchItemFailures.size());
        return SQSBatchResponse.builder().withBatchItemFailures(batchItemFailures).build();
    }

    private void processMessage(SQSEvent.SQSMessage message, LambdaLogger logger) {
        String body = message.getBody();
        if (body == null || body.isBlank()) {
            return;
        }

        List<ThumbnailJob> jobs = parseJobsFromSqsBody(body, message.getMessageId(), logger);
        if (jobs.isEmpty()) {
            log(logger, "No actionable original photo S3 keys found in message " + message.getMessageId() + ". Skipped.");
            return;
        }

        for (ThumbnailJob job : jobs) {
            processSingleJob(job, logger);
        }
    }

    private void processSingleJob(ThumbnailJob job, LambdaLogger logger) {
        log(logger, String.format("Processing start: photoId=%s, eventId=%s, key=%s, bucket=%s",
                job.getPhotoId(), job.getEventId(), job.getOriginalS3Key(), job.getS3Bucket()));

        long startTime = System.currentTimeMillis();

        try {
            ThumbnailResult result = thumbnailProcessor.processThumbnail(job);
            long durationMs = System.currentTimeMillis() - startTime;

            log(logger, String.format("Processing success: photoId=%s, thumbnailKey=%s, dimensions=%dx%d, size=%d bytes, duration=%dms",
                    job.getPhotoId(), result.getThumbnailKey(), result.width(), result.height(), result.fileSize(), durationMs));

            // Notify Spring Boot backend with COMPLETED status
            callbackClient.sendSuccessCallback(job, result);
            log(logger, "Callback sent successfully for photoId: " + job.getPhotoId());

        } catch (PermanentProcessingException pEx) {
            long durationMs = System.currentTimeMillis() - startTime;
            log(logger, String.format("Permanent processing error for photoId=%s after %dms: %s",
                    job.getPhotoId(), durationMs, pEx.getMessage()));

            // Send FAILED callback to backend so photo row records failure
            try {
                callbackClient.sendFailureCallback(job, pEx.getMessage());
                log(logger, "Failure callback sent for photoId: " + job.getPhotoId());
            } catch (TransientProcessingException cbEx) {
                log(logger, "Failed to deliver failure callback to backend; triggering SQS retry for photoId: " + job.getPhotoId());
                throw cbEx;
            }

            // Propagate permanent exception to indicate message is finished
            throw pEx;
        }
    }

    public List<ThumbnailJob> parseJobsFromSqsBody(String body, String messageId, LambdaLogger logger) {
        List<ThumbnailJob> jobs = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);

            // Handle SNS-wrapped messages if present
            if (root.has("Message") && root.get("Message").isTextual()) {
                String innerMessage = root.get("Message").asText();
                root = objectMapper.readTree(innerMessage);
            }

            JsonNode recordsNode = root.path("Records");
            if (recordsNode.isArray()) {
                for (JsonNode record : recordsNode) {
                    String eventName = record.path("eventName").asText("");
                    // Process ObjectCreated events (or test events)
                    if (!eventName.isBlank() && !eventName.startsWith("ObjectCreated:")) {
                        log(logger, "Ignoring non-ObjectCreated S3 event: " + eventName);
                        continue;
                    }

                    JsonNode s3Node = record.path("s3");
                    String bucket = s3Node.path("bucket").path("name").asText(config.getS3BucketName());
                    String rawKey = s3Node.path("object").path("key").asText("");

                    Optional<ThumbnailJob> jobOpt = S3KeyParser.parseOriginalKey(rawKey, bucket, messageId);
                    jobOpt.ifPresent(jobs::add);
                }
            }
        } catch (PermanentProcessingException pEx) {
            throw pEx;
        } catch (Exception ex) {
            log(logger, "Malformed S3/SQS event payload: " + ex.getMessage());
            throw new PermanentProcessingException("Failed to parse SQS message body as S3 event: " + ex.getMessage(), ex);
        }

        return jobs;
    }

    private void log(LambdaLogger logger, String message) {
        if (logger != null) {
            logger.log(message + "\n");
        } else {
            System.out.println(message);
        }
    }
}
