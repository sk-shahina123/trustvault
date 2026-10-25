package com.trustvault;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {
    private final AuditQueryService service;
    public AuditController(AuditQueryService service) { this.service = service; }

    @GetMapping("/api/admin/audit-events")
    public ResponseEntity<List<AuditQueryService.Event>> list(Authentication authentication,
            @RequestParam(name = "before", required = false) Long before) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(authentication.getName(), before));
    }
}
