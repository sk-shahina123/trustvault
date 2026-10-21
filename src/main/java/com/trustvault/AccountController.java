package com.trustvault;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    @GetMapping("/api/me")
    public Map<String, String> currentUser(Authentication authentication) {

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String authorityName = authority.getAuthority();

            if (authorityName.startsWith("ROLE_")) {
                return Map.of(
                        "email", authentication.getName(),
                        "role", authorityName.substring(5)
                );
            }
        }

        throw new IllegalStateException(
                "Signed-in account has no application role");
    }
}
