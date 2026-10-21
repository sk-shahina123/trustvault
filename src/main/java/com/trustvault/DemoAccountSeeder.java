package com.trustvault;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("seed-demo")
public class DemoAccountSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder(12);

    public DemoAccountSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        createIfMissing(
                "admin@trustvault.test", "AdminDemo!2026", "ADMIN");

        createIfMissing(
                "owner@trustvault.test", "OwnerDemo!2026", "OWNER");

        createIfMissing(
                "viewer@trustvault.test", "ViewerDemo!2026", "VIEWER");
    }

    private void createIfMissing(
            String email, String password, String role) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE email = ?",
                Integer.class,
                email
        );

        if (count != null && count > 0) {
            System.out.println("Demo account already exists: " + email);
            return;
        }

        String passwordHash = passwordEncoder.encode(password);

        jdbcTemplate.update(
                "INSERT INTO app_users (email, password_hash, role) "
                        + "VALUES (?, ?, ?)",
                email,
                passwordHash,
                role
        );

        System.out.println("Created demo account: " + email);
    }
}