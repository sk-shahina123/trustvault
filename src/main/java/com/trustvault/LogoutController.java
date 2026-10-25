package com.trustvault;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class LogoutController {
    @GetMapping(value="/logout", produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirm(HttpServletRequest request) {
        CsrfToken csrf=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        String html="""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"><title>Sign out | TrustVault</title>
                <style>body{font:16px system-ui;background:#eef2f7;color:#172b4d;padding:32px}
                main{max-width:360px;margin:48px auto;padding:28px;background:white;border-radius:16px}
                button{padding:12px 24px;background:#174ea6;color:white;border:0;border-radius:8px;font:inherit}</style></head>
                <body><main><h1>Sign out?</h1><p>Your current session will end.</p>
                <form method="post" action="/logout"><input type="hidden" name="{{NAME}}" value="{{TOKEN}}">
                <button type="submit">Sign out</button></form><p><a href="/">Keep working</a></p></main></body></html>
                """.replace("{{NAME}}",HtmlUtils.htmlEscape(csrf.getParameterName()))
                .replace("{{TOKEN}}",HtmlUtils.htmlEscape(csrf.getToken()));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(html);
    }
}
