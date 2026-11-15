# AWS SQS DLQ Monitoring and Recovery Guide for FotoFetch AI

## 1. Verified AWS Infrastructure Inventory

Based on production configuration and project repository documentation:

| Resource | Name / Identifier | Region | Exact URL / ARN |
| :--- | :--- | :--- | :--- |
| **AWS Account ID** | `480836974477` | `ap-south-1` | N/A |
| **S3 Storage Bucket** | `fotofetch-ai-photos-480836974477` | `ap-south-1` | `arn:aws:s3:::fotofetch-ai-photos-480836974477` |
| **Primary SQS Queue** | `fotofetch-thumbnail-jobs` | `ap-south-1` | **URL:** `https://sqs.ap-south-1.amazonaws.com/480836974477/fotofetch-thumbnail-jobs`<br>**ARN:** `arn:aws:sqs:ap-south-1:480836974477:fotofetch-thumbnail-jobs` |
| **Dead-Letter Queue (DLQ)** | `fotofetch-thumbnail-processing-dlq` | `ap-south-1` | **URL:** `https://sqs.ap-south-1.amazonaws.com/480836974477/fotofetch-thumbnail-processing-dlq`<br>**ARN:** `arn:aws:sqs:ap-south-1:480836974477:fotofetch-thumbnail-processing-dlq` |
| **Lambda Thumbnail Generator** | `fotofetch-thumbnail-generator` | `ap-south-1` | `arn:aws:lambda:ap-south-1:480836974477:function:fotofetch-thumbnail-generator` |

---

## 2. CloudWatch Alarm Configuration: ApproximateNumberOfMessagesVisible >= 1

### Alarm Specification

To detect failed messages that have exhausted their 3 retries in `fotofetch-thumbnail-jobs` and moved to the DLQ:

- **Alarm Name:** `FotoFetch-Thumbnail-DLQ-Alarm`
- **Metric Name:** `ApproximateNumberOfMessagesVisible`
- **Namespace:** `AWS/SQS`
- **Dimensions:**
  - `QueueName`: `fotofetch-thumbnail-processing-dlq`
- **Statistic:** `Maximum`
- **Period:** `60` seconds (1 minute)
- **Evaluation Periods:** `1`
- **Threshold:** `>= 1.0`
- **Comparison Operator:** `GreaterThanOrEqualToThreshold`
- **Treat Missing Data:** `notBreaching`

### AWS CLI Creation Command

```bash
aws cloudwatch put-metric-alarm \
  --region ap-south-1 \
  --alarm-name "FotoFetch-Thumbnail-DLQ-Alarm" \
  --alarm-description "Alarm when any messages arrive in the FotoFetch thumbnail dead-letter queue (fotofetch-thumbnail-processing-dlq)" \
  --namespace "AWS/SQS" \
  --metric-name "ApproximateNumberOfMessagesVisible" \
  --dimensions Name=QueueName,Value=fotofetch-thumbnail-processing-dlq \
  --period 60 \
  --evaluation-periods 1 \
  --threshold 1.0 \
  --comparison-operator GreaterThanOrEqualToThreshold \
  --statistic Maximum \
  --treat-missing-data notBreaching \
  --alarm-actions arn:aws:sns:ap-south-1:480836974477:fotofetch-ops-alerts
```

*(Replace the `--alarm-actions` ARN with your operational SNS notification topic).*

### Why Photos Must NOT Be Automatically Marked FAILED on Alarm
An alarm is an aggregate metric across the dead-letter queue. It indicates that *one or more* messages failed all SQS retries, but:
1. The alarm does not communicate which individual photo ID failed.
2. The cause may be a transient backend network timeout, an expired shared secret, or an infrastructure issue that has since resolved.
3. Automatically updating photos to `FAILED` in bulk upon alarm trigger would cause false failure marks for healthy in-flight jobs.

---

## 3. Safe DLQ Message Recovery Procedure

When messages arrive in the DLQ:

### Step 1: Inspect Message Content and Identify Cause
In the AWS Console (SQS -> `fotofetch-thumbnail-processing-dlq` -> **Send and receive messages** -> **Poll for messages**) or via AWS CLI:

```bash
aws sqs receive-message \
  --region ap-south-1 \
  --queue-url https://sqs.ap-south-1.amazonaws.com/480836974477/fotofetch-thumbnail-processing-dlq \
  --attribute-names All \
  --message-attribute-names All \
  --max-number-of-messages 1
```

The message body contains the exact S3 notification JSON payload:
- `s3.bucket.name`: `fotofetch-ai-photos-480836974477`
- `s3.object.key`: `events/{eventId}/originals/{photoId}-{filename}`

Inspect CloudWatch Logs for `/aws/lambda/fotofetch-thumbnail-generator` to identify why the job failed (e.g. backend callback unreachable, temporary AWS throttling, or out-of-memory).

### Step 2: Redrive Messages to the Main Queue
Once the underlying issue (e.g., network connectivity, secret synchronization, or capacity) is resolved:

#### Option A: AWS Console (Recommended)
1. Go to AWS SQS Console in `ap-south-1`.
2. Select `fotofetch-thumbnail-processing-dlq`.
3. Click **Start DLQ redrive**.
4. Select **Redrive to source queue** (`fotofetch-thumbnail-jobs`).
5. Click **Redrive**.

#### Option B: AWS CLI
```bash
aws sqs start-message-move-task \
  --region ap-south-1 \
  --source-arn arn:aws:sqs:ap-south-1:480836974477:fotofetch-thumbnail-processing-dlq \
  --destination-arn arn:aws:sqs:ap-south-1:480836974477:fotofetch-thumbnail-jobs
```

---

## 4. Preservation of Photo ID, S3 Key, and Duplicate Prevention

Redriving messages is **strictly safe and idempotent**:

1. **Preservation of S3 Key and Photo ID:**
   The SQS message body retains the exact original S3 event notification JSON. When moved back to the main queue, Lambda's `S3KeyParser.parseOriginalKey` extracts the exact original `eventId`, `photoId`, and `originalS3Key`.
2. **Idempotent S3 PutObject:**
   Lambda generates the thumbnail and writes it to `events/{eventId}/thumbnails/{photoId}.jpg`. S3 PUT is inherently idempotent.
3. **Replay Protection on Callback:**
   Every callback generated by Lambda includes a fresh, unique `callbackId` (UUID string).
4. **Duplicate Rekognition Prevention:**
   - If the photo was already marked `COMPLETED` on the backend, `ThumbnailCallbackService` acknowledges `ALREADY_COMPLETED` without re-running Rekognition.
   - If Rekognition was already completed (`indexingStatus == COMPLETED`), `canScheduleIndexing` is false.
   - Even if concurrent redrive occurs, `claimPhotoForIndexing` CAS update ensures Rekognition `indexPhotoFaces` can only ever be claimed and executed once.

---

## 5. Remaining Operational Limitation

- **Manual Redrive Required:** Until an automated DLQ consumer Lambda or automated redrive workflow is provisioned in AWS, photos with messages in the DLQ remain in `thumbnailStatus: PENDING` in the database.
- **Monitoring Dependency:** Operational teams must respond to the `FotoFetch-Thumbnail-DLQ-Alarm` to inspect and trigger the redrive task.
