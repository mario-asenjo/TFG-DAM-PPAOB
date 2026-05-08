package com.ppaob.backend.adapters.in.rest;

import com.ppaob.backend.adapters.in.rest.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
/**
 * Maps server-side exceptions to stable HTTP error payloads.
 */
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Converts argument validation and contract errors to HTTP 400.
     *
     * @param ex source exception
     * @param request request metadata used to populate error path
     * @return standardized error response with `BAD_REQUEST` status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    /**
     * Converts authentication failures to HTTP 401.
     *
     * @param ex source exception
     * @param request request metadata used to populate error path
     * @return standardized error response with `UNAUTHORIZED` status
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(SecurityException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    /**
     * Converts authorization denials to HTTP 403.
     *
     * @param ex source exception
     * @param request request metadata used to populate error path
     * @return standardized error response with `FORBIDDEN` status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Insufficient permissions", request, null);
    }

    /**
     * Converts bean validation failures to HTTP 400 and includes field-level details.
     *
     * @param ex source exception with binding errors
     * @param request request metadata used to populate error path
     * @return standardized error response including `fieldErrors` detail list
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.toList());

        String message = fieldErrors.isEmpty() ? "Validation failed" : fieldErrors.getFirst();
        return error(HttpStatus.BAD_REQUEST, message, request, Map.of("fieldErrors", fieldErrors));
    }

    /**
     * Converts request parameter type mismatches to HTTP 400.
     *
     * @param ex source exception with parameter metadata
     * @param request request metadata used to populate error path
     * @return standardized error response with a parameter-specific message
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '%s'".formatted(ex.getName());
        return error(HttpStatus.BAD_REQUEST, message, request, null);
    }

    /**
     * Converts missing required request parameters to HTTP 400.
     *
     * @param ex source exception with missing parameter name
     * @param request request metadata used to populate error path
     * @return standardized error response with missing parameter message
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Missing required parameter '%s'".formatted(ex.getParameterName());
        return error(HttpStatus.BAD_REQUEST, message, request, null);
    }

    /**
     * Converts malformed JSON or unreadable bodies to HTTP 400.
     *
     * @param ex source exception
     * @param request request metadata used to populate error path
     * @return standardized error response with malformed-body message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", request, null);
    }

    /**
     * Converts multipart size-limit violations to HTTP 413.
     *
     * @param ex source exception
     * @param request request metadata used to populate error path
     * @return standardized error response with `PAYLOAD_TOO_LARGE` status
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds maximum allowed size", request, null);
    }

    /**
     * Converts uncaught exceptions to HTTP 500 and logs stack trace server-side.
     *
     * @param ex source exception
     * @param request request metadata used to populate error path
     * @return standardized error response with `INTERNAL_SERVER_ERROR` status
     * @implNote Side effect: emits an `ERROR` log entry with the full exception.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, null);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        String safeMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        String path = request != null ? request.getRequestURI() : "unknown";
        ErrorResponse body = ErrorResponse.of(status.value(), status.getReasonPhrase(), safeMessage, path, details);
        return ResponseEntity.status(status).body(body);
    }
}
