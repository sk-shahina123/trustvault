package com.trustvault;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
public class RegistrationController {
    private final RegistrationService registration;
    public RegistrationController(RegistrationService registration) { this.registration = registration; }

    @GetMapping(value="/register", produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> form(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(page(csrf, ""));
    }

    @PostMapping(value="/register", consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> submit(@RequestParam String email, @RequestParam String password,
            @RequestParam String confirmPassword, HttpServletRequest request) {
        try {
            registration.register(email,password,confirmPassword,request.getRemoteAddr());
            return ResponseEntity.status(303).header(HttpHeaders.LOCATION,"/login?registered")
                    .header(HttpHeaders.CACHE_CONTROL,"no-store").build();
        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(409).header(HttpHeaders.CACHE_CONTROL,"no-store")
                    .contentType(MediaType.TEXT_HTML).body(page((CsrfToken)request.getAttribute(CsrfToken.class.getName()),
                            "Unable to create this account. Use another address or sign in."));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).header(HttpHeaders.CACHE_CONTROL,"no-store")
                    .contentType(MediaType.TEXT_HTML).body(page((CsrfToken)request.getAttribute(CsrfToken.class.getName()),exception.getReason()));
        }
    }

    private String page(CsrfToken csrf, String message) {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"><title>TrustVault — Register</title>
                <style>body{font:16px system-ui;background:#eef2f7;color:#172b4d;margin:0;padding:32px}
                main{max-width:420px;margin:24px auto;background:white;padding:28px;border-radius:16px}
                label{display:block;margin:16px 0 6px}input,button{box-sizing:border-box;width:100%;padding:12px;font:inherit;border:1px solid #aab8cc;border-radius:8px}
                button{margin:20px 0;background:#174ea6;color:white}[role=alert]{color:#b42318}</style></head>
                <body><main><h1>Create your TrustVault account</h1>
                <p>You can upload your own documents and share them with named accounts. Existing documents stay private. Only administrators can review the audit log.</p>
                <p role="alert">{{MESSAGE}}</p><form method="post" action="/register">
                <label for="email">Email</label><input id="email" name="email" type="email" maxlength="254" autocomplete="username" required>
                <label for="password">Password (at least 12 characters)</label><input id="password" name="password" type="password" minlength="12" maxlength="72" autocomplete="new-password" required>
                <label for="confirmPassword">Confirm password</label><input id="confirmPassword" name="confirmPassword" type="password" minlength="12" maxlength="72" autocomplete="new-password" required>
                <input type="hidden" name="{{CSRF_NAME}}" value="{{CSRF_TOKEN}}"><button type="submit">Create account</button></form>
                <a href="/login">Already registered? Sign in</a></main></body></html>
                """.replace("{{MESSAGE}}",HtmlUtils.htmlEscape(message == null ? "Registration failed." : message))
                .replace("{{CSRF_NAME}}",HtmlUtils.htmlEscape(csrf.getParameterName()))
                .replace("{{CSRF_TOKEN}}",HtmlUtils.htmlEscape(csrf.getToken()));
    }
}
