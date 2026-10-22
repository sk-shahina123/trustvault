package com.trustvault;

import java.security.Principal;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import com.trustvault.AuditService.EventType;

@RestControllerAdvice
public class DocumentErrorHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentErrorHandler.class);

    private final AuditService auditService;

    public DocumentErrorHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(
            ResponseStatusException exception) {

        if (exception.getStatusCode().is5xxServerError()) {
            logger.error("Document request failed", exception);
        }

        String message = exception.getReason() == null
                ? "The request could not be completed."
                : exception.getReason();

        return response(
                exception.getStatusCode().value(), message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(
            HttpServletRequest request) {

        recordFrameworkFailure(request, "UPLOAD_TOO_LARGE");

        return response(
                413,
                "Maximum file size is 5 MiB; "
                + "the full request must be under 6 MiB.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> invalidUpload(
            HttpServletRequest request) {

        recordFrameworkFailure(
                request, "INVALID_MULTIPART_REQUEST");

        return response(
                400,
                "The upload could not be read. Choose the file again.");
    }

    @ExceptionHandler({
            DataAccessException.class,
            TransactionException.class
    })
    public ResponseEntity<Map<String, String>> databaseFailure(
            RuntimeException exception,
            HttpServletRequest request) {

        logger.error("Database operation failed", exception);

        recordFrameworkFailure(
                request, "DATABASE_OPERATION_FAILED");

        return response(
                503,
                "TrustVault could not complete the request. "
                + "Try again later.");
    }

    private void recordFrameworkFailure(
            HttpServletRequest request, String reason) {

        if (!"/api/documents".equals(request.getServletPath())) {
            return;
        }

        EventType type;

        if ("POST".equals(request.getMethod())) {
            type = EventType.UPLOAD;
        } else if ("GET".equals(request.getMethod())) {
            type = EventType.LIST;
        } else {
            return;
        }

        Principal principal = request.getUserPrincipal();
        String email = principal == null
                ? null
                : principal.getName();

        try {
            // Parsing may fail before an account ID can be resolved.
            auditService.recordFailure(
                    null, email, null, type, reason);

        } catch (RuntimeException exception) {
            // A database outage may also prevent saving the audit entry.
            // The original request still fails.
            logger.error(
                    "Could not persist the failed request's audit entry",
                    exception);
        }
    }

    private static ResponseEntity<Map<String, String>> response(
            int status, String message) {

        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", message));
    }
}