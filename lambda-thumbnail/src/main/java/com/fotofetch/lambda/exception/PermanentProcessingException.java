package com.fotofetch.lambda.exception;

/**
 * Indicates an unrecoverable failure (e.g. corrupt image, unsupported format,
 * decompression bomb, or invalid key format).
 * Such failures should notify the backend with a FAILED status and then be acknowledged
 * by SQS so they do not trigger infinite retry loops.
 */
public class PermanentProcessingException extends RuntimeException {

    public PermanentProcessingException(String message) {
        super(message);
    }

    public PermanentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
