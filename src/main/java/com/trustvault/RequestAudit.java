package com.trustvault;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.trustvault.AuditService.EventType;

/** Records rejections that happen before a document service is called. */
@Component
public class RequestAudit {
    private static final Logger LOG = LoggerFactory.getLogger(RequestAudit.class);
    private static final Pattern DOCUMENT =
            Pattern.compile("^/api/documents/([0-9]+)(?:/|$)");
    private final AuditService audit;

    public RequestAudit(AuditService audit) { this.audit = audit; }

    public boolean record(HttpServletRequest request, String outcome, String reason) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") && !path.equals("/audit.html")) return true;
        EventType type = EventType.ACCESS;
        String method = request.getMethod();
        Long documentId = null;
        Matcher matcher = DOCUMENT.matcher(path);
        if (matcher.find()) {
            try { documentId = Long.valueOf(matcher.group(1)); }
            catch (NumberFormatException ignored) { /* Invalid ID, still audit request. */ }
        }
        if (path.startsWith("/api/admin/") || path.equals("/audit.html")) {
            type = EventType.VIEW_AUDIT;
        } else if (path.equals("/api/documents")) {
            type = "POST".equals(method) ? EventType.UPLOAD : EventType.LIST;
        } else if (path.startsWith("/api/documents/")) {
            if (path.endsWith("/download")) type = EventType.DOWNLOAD;
            else if (path.endsWith("/edit")) type = EventType.EDIT;
            else if (path.contains("/permissions")) {
                type = switch (method) {
                    case "POST" -> EventType.GRANT;
                    case "DELETE" -> EventType.REVOKE;
                    default -> EventType.LIST;
                };
            } else if ("DELETE".equals(method)) type = EventType.DELETE;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken) ? auth.getName() : null;
        try {
            // Never record credentials, tokens, request bodies or query strings.
            audit.recordRequest(email, documentId, type, outcome, reason,
                    method, path.substring(0, Math.min(path.length(), 512)));
            return true;
        } catch (RuntimeException exception) {
            LOG.error("Could not persist rejected request audit; request remains blocked", exception);
            return false;
        }
    }
}
