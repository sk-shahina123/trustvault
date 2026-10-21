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

@Configuration
public class SecurityConfig {

    @Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {

    http
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults())
            .httpBasic(Customizer.withDefaults())
            .logout(Customizer.withDefaults());

    return http.build();
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
