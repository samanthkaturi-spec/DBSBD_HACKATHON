package com.smartleave.security;

import com.smartleave.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService service(String secret) {
        return new JwtService(secret, "smart-leave-management", 3600_000L, "SLM_TOKEN");
    }

    @Test
    @DisplayName("A generated token can be parsed back and carries identity and role")
    void roundTrip() {
        JwtService jwt = service("unit-test-secret-that-is-long-enough-0123456789");
        String token = jwt.generateToken(7L, "employee1@smartleave.com", Role.EMPLOYEE, "Rahul Verma");

        Claims claims = jwt.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("employee1@smartleave.com");
        assertThat(claims.get("uid", Number.class).longValue()).isEqualTo(7L);
        assertThat(claims.get("role", String.class)).isEqualTo("EMPLOYEE");
        assertThat(claims.getIssuer()).isEqualTo("smart-leave-management");
        assertThat(jwt.extractEmail(token)).isEqualTo("employee1@smartleave.com");
        assertThat(jwt.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("A tampered or foreign token is rejected")
    void rejectsInvalidTokens() {
        JwtService jwt = service("unit-test-secret-that-is-long-enough-0123456789");
        JwtService other = service("a-different-secret-that-is-also-long-enough-01234");

        String forged = other.generateToken(1L, "admin@smartleave.com", Role.ADMIN, "Ajay Kumar");
        assertThat(jwt.isTokenValid(forged)).isFalse();
        assertThat(jwt.isTokenValid("not.a.jwt")).isFalse();
        assertThat(jwt.isTokenValid("")).isFalse();
        assertThatThrownBy(() -> jwt.parseClaims(forged + "tampered"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("A missing JWT_SECRET still produces a usable ephemeral key")
    void ephemeralSecretWhenNotConfigured() {
        JwtService jwt = service("");
        assertThat(jwt.isEphemeralSecret()).isTrue();
        String token = jwt.generateToken(1L, "admin@smartleave.com", Role.ADMIN, "Ajay Kumar");
        assertThat(jwt.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("An explicitly configured secret is never treated as ephemeral")
    void configuredSecret() {
        JwtService jwt = service("configured-secret-with-more-than-thirty-two-bytes!!");
        assertThat(jwt.isEphemeralSecret()).isFalse();
    }
}
