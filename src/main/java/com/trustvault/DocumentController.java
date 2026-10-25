package com.trustvault;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "headerName", csrfToken.getHeaderName(),
                        "token", csrfToken.getToken()));
    }

    @PostMapping(
            value = "/api/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            Authentication authentication,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "file", required = false) MultipartFile file) {

        // Identify the uploader from the authenticated account.
        long id = documentService.upload(
                authentication.getName(),
                name,
                category,
                file);

        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "id", id,
                        "message", "Document uploaded."));
    }

    @GetMapping("/api/documents")
    public ResponseEntity<List<DocumentService.DocumentSummary>> list(
            Authentication authentication,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "owner", required = false) String owner,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(documentService.search(authentication.getName(), name, owner, category, dateFrom, dateTo));
    }
    @GetMapping("/api/documents/{documentId}")
    public ResponseEntity<DocumentService.DocumentSummary> details(Authentication authentication,
            @PathVariable("documentId") long documentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(documentService.details(authentication.getName(), documentId));
    }

    @PostMapping(value = "/api/documents/{documentId}/edit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> edit(Authentication authentication,
            @PathVariable("documentId") long documentId,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        documentService.edit(authentication.getName(), documentId, name, category, file);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Document updated."));
    }

    @DeleteMapping("/api/documents/{documentId}")
    public ResponseEntity<Map<String, String>> delete(Authentication authentication,
            @PathVariable("documentId") long documentId) {
        documentService.delete(authentication.getName(), documentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Document deleted."));
    }

    @GetMapping("/api/documents/{documentId}/download")
public ResponseEntity<byte[]> download(
        Authentication authentication,
        @PathVariable("documentId") long documentId) {

    DocumentService.DocumentDownload file =
            documentService.download(
                    authentication.getName(), documentId);

    String disposition = ContentDisposition.attachment()
            .filename(file.filename(), StandardCharsets.UTF_8)
            .build()
            .toString();

    return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .contentLength(file.data().length)
            .body(file.data());
}
}
