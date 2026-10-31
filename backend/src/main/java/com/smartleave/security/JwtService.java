package com.smartleave.security;

import com.smartleave.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates HS256 JWTs.
 *
 * <p>The signing secret is never hardcoded: it is read from the {@code
 * JWT_SECRET} environment variable. When it is absent (local development)
 * an ephemeral random secret is generated at startup, so a restart simply
 * invalidates outstanding tokens.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final String issuer;
    private final long expirationMs;
    private final String cookieName;
    private final boolean ephemeralSecret;

    public JwtService(@Value("${smartleave.security.jwt.secret:}") String secret,
                      @Value("${smartleave.security.jwt.issuer:smart-leave-management}") String issuer,
                      @Value("${smartleave.security.jwt.expiration-ms:86400000}") long expirationMs,
                      @Value("${smartleave.security.jwt.cookie-name:SLM_TOKEN}") String cookieName) {
        this.issuer = issuer;
        this.expirationMs = expirationMs;
        this.cookieName = cookieName;

        byte[] secretBytes;
        if (secret == null || secret.isBlank()) {
            secretBytes = new byte[64];
            new SecureRandom().nextBytes(secretBytes);
            this.ephemeralSecret = true;
            log.warn("JWT_SECRET is not configured - using an ephemeral development secret. "
                    + "Tokens will be invalidated when the server restarts. "
                    + "Set JWT_SECRET in production.");
        } else {
            secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET must be at least 32 bytes long");
            }
            this.ephemeralSecret = false;
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(Long userId, String email, Role role, String name) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(email)
                .claim("uid", userId)
                .claim("role", role.name())
                .claim("name", name)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object uid = parseClaims(token).get("uid");
        if (uid instanceof Number number) {
            return number.longValue();
        }
        throw new JwtException("Token is missing the uid claim");
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected invalid JWT: {}", ex.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String getCookieName() {
        return cookieName;
    }

    public boolean isEphemeralSecret() {
        return ephemeralSecret;
    }

    /** Debug helper so tests can assert the base64 of a configured secret. */
    static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
