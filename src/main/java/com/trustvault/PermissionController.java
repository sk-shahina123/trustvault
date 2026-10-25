package com.trustvault;

import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/api/documents/{documentId}/permissions")
    public ResponseEntity<List<PermissionService.PermissionEntry>> list(
            Authentication authentication,
            @PathVariable("documentId") long documentId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(permissionService.list(
                        authentication.getName(), documentId));
    }

    @PostMapping(
            value = "/api/documents/{documentId}/permissions",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, String>> grant(
            Authentication authentication,
            @PathVariable("documentId") long documentId,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "permission", required = false)
                    String permission) {

        permissionService.grant(
                authentication.getName(), documentId, email, permission);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Permission saved."));
    }

    @DeleteMapping("/api/documents/{documentId}/permissions/{recipientId}")
    public ResponseEntity<Map<String, String>> revoke(
            Authentication authentication,
            @PathVariable("documentId") long documentId,
            @PathVariable("recipientId") long recipientId) {

        permissionService.revoke(
                authentication.getName(), documentId, recipientId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Access revoked."));
    }
}