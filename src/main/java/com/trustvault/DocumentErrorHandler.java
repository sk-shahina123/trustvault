package com.trustvault;

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

import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class DocumentErrorHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentErrorHandler.class);

    private final RequestAudit requestAudit;

    public DocumentErrorHandler(RequestAudit requestAudit) {
        this.requestAudit = requestAudit;
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
        requestAudit.record(request, "FAILURE", reason);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class})
    public ResponseEntity<Map<String, String>> invalidParameters(HttpServletRequest request) {
        recordFrameworkFailure(request, "INVALID_REQUEST_PARAMETERS");
        return response(400, "Invalid request parameters.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> wrongMethod(HttpServletRequest request) {
        recordFrameworkFailure(request, "METHOD_NOT_ALLOWED");
        return response(405, "This request method is unavailable.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> wrongType(HttpServletRequest request) {
        recordFrameworkFailure(request, "UNSUPPORTED_CONTENT_TYPE");
        return response(415, "Unsupported request content type.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> missing(HttpServletRequest request) {
        requestAudit.record(request, "DENIED", "ROUTE_UNAVAILABLE");
        return response(404, "Resource not found or access unavailable.");
    }

    private static ResponseEntity<Map<String, String>> response(
            int status, String message) {

        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", message));
    }
}
