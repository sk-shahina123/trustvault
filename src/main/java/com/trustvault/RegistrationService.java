package com.trustvault;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final Map<String, Integer> attempts = new HashMap<>();
    private long window = Instant.now().getEpochSecond() / 3600;
    private int total;

    public RegistrationService(JdbcTemplate jdbc, PasswordEncoder encoder,
            AuditService audit, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
        transactions = new TransactionTemplate(manager);
    }

    // Bounded, process-local abuse protection; not a distributed rate limiter.
    private synchronized void limit(String address) {
        long now = Instant.now().getEpochSecond() / 3600;
        if (now != window) { attempts.clear(); total = 0; window = now; }
        int count = attempts.getOrDefault(address, 0);
        if (count >= 10 || total >= 100)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Registration limit reached. Try again later.");
        attempts.put(address, count + 1);
        total++;
    }

    public void register(String email, String password, String confirmation, String address) {
        limit(address);
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.equals(confirmation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use at least 12 characters, at most 72 UTF-8 bytes, and matching passwords.");
        String hash = encoder.encode(password);
        transactions.executeWithoutResult(status -> {
            // The browser never chooses a role. Every signup gets only OWNER rights.
            jdbc.update("INSERT INTO app_users (email,password_hash,role,enabled) VALUES (?,?,'OWNER',1)", normalized, hash);
            Long id = jdbc.queryForObject("SELECT id FROM app_users WHERE email=?", Long.class, normalized);
            audit.recordSuccess(id, normalized, null, AuditService.EventType.REGISTER, "OWNER_ACCOUNT_CREATED");
        });
    }
}
