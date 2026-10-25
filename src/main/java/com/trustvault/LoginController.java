package com.trustvault;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class LoginController {
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String login(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String error = request.getParameter("error") == null ? ""
                : "<p role=\"alert\">Invalid email or password.</p>";
        return """
                <!doctype html><html lang="en"><head>
                <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>TrustVault — Sign in</title>
                <style>
                body{margin:0;background:#eef2f7;color:#172b4d;font:16px system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}
                main{background:white;padding:32px;border-radius:16px;width:min(360px,80vw);box-shadow:0 12px 40px #172b4d18}
                h1{margin-top:0}label{display:block;margin:18px 0 6px}
                input,button{box-sizing:border-box;width:100%;padding:12px;border:1px solid #b8c4d4;border-radius:8px;font:inherit}
                button{margin-top:24px;background:#174ea6;color:white;border:0;cursor:pointer}
                [role=alert]{color:#b42318}
                </style></head><body><main><h1>TrustVault</h1><p>Secure document access</p>
                %s
                <form method="post" action="/login">
                <label for="username">Email</label><input id="username" name="username" type="email" autocomplete="username" required>
                <label for="password">Password</label><input id="password" name="password" type="password" autocomplete="current-password" required>
                <input type="hidden" name="%s" value="%s">
                <button type="submit">Sign in</button></form></main></body></html>
                """.formatted(error, HtmlUtils.htmlEscape(csrf.getParameterName()),
                        HtmlUtils.htmlEscape(csrf.getToken()));
    }
}

