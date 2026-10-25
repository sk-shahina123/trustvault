package com.trustvault;

import java.util.List;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
public class SecurityConfig {

    @Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, RequestAudit audit)
        throws Exception {

    AccessDeniedHandler denied = (request, response, exception) -> {
        boolean saved = audit.record(request, "DENIED",
                exception instanceof CsrfException ? "CSRF_REJECTED" : "ROLE_REQUIRED");
        response.setStatus(saved ? 403 : 503);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Access unavailable. Refresh and sign in again.\"}");
    };
    AuthenticationEntryPoint entry = (request, response, exception) -> {
        boolean saved = audit.record(request, "DENIED", "AUTHENTICATION_REQUIRED");
        response.setHeader("Cache-Control", "no-store");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(saved ? 401 : 503);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Please sign in.\"}");
        } else {
            response.sendRedirect("/login");
        }
    };

    http
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/healthz", "/register").permitAll()
                    .requestMatchers("/api/admin/**", "/audit.html").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .exceptionHandling(errors -> errors
                    .accessDeniedHandler(denied).authenticationEntryPoint(entry))
            .formLogin(login -> login.permitAll())
            .httpBasic(basic -> basic.authenticationEntryPoint(entry))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")))
            .logout(Customizer.withDefaults());

    return http.build();
}

    @Bean
    public WebSecurityCustomizer rejectedRequests(RequestAudit audit) {
        return web -> web.requestRejectedHandler((request, response, exception) -> {
            boolean saved = audit.record(request, "DENIED", "REQUEST_REJECTED");
            response.setStatus(saved ? 400 : 503);
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Request rejected.\"}");
        });
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService(JdbcTemplate jdbcTemplate) {
        return email -> {
            List<UserDetails> users = jdbcTemplate.query(
                    "SELECT email, password_hash, role, enabled "
                            + "FROM app_users WHERE email = ?",
                    (rs, rowNum) -> User.withUsername(rs.getString("email"))
                            .password(rs.getString("password_hash"))
                            .roles(rs.getString("role"))
                            .disabled(!rs.getBoolean("enabled"))
                            .build(),
                    email
            );

            if (users.isEmpty()) {
                throw new UsernameNotFoundException(
                        "Invalid email or password");
            }

            return users.get(0);
        };
    }
}
