package com.ppaob.backend.adapters.in.rest.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error payload returned by REST exception handlers.
 *
 * @param timestamp ISO-8601 timestamp when response was created
 * @param status HTTP status code
 * @param error HTTP reason phrase
 * @param message human-readable error message
 * @param path request URI that produced the error
 * @param details optional structured diagnostic details
 */
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, Object> details
) {
    /**
     * Creates an error payload without additional details.
     *
     * @param status HTTP status code
     * @param error HTTP reason phrase
     * @param message human-readable error message
     * @param path request URI
     * @return new error payload timestamped at creation time
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now().toString(), status, error, message, path, null);
    }

    /**
     * Creates an error payload with optional structured details.
     *
     * @param status HTTP status code
     * @param error HTTP reason phrase
     * @param message human-readable error message
     * @param path request URI
     * @param details additional diagnostics to return to clients
     * @return new error payload timestamped at creation time
     */
    public static ErrorResponse of(int status, String error, String message, String path, Map<String, Object> details) {
        return new ErrorResponse(Instant.now().toString(), status, error, message, path, details);
    }
}
