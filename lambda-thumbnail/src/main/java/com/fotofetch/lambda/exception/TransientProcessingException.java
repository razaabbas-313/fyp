package com.fotofetch.lambda.exception;

/**
 * Indicates a temporary failure (e.g. S3 503 slowdown, temporary network timeout,
 * or backend callback endpoint temporarily unavailable).
 * SQS partial batch failure reporting should return this message ID so SQS retries the message.
 */
public class TransientProcessingException extends RuntimeException {

    public TransientProcessingException(String message) {
        super(message);
    }

    public TransientProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
