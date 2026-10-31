package com.smartleave.controller;

import com.smartleave.dto.AuthDto.AuthResponse;
import com.smartleave.dto.AuthDto.LoginRequest;
import com.smartleave.dto.AuthDto.RegisterRequest;
import com.smartleave.dto.CommonDto.UserSummary;
import com.smartleave.security.JwtService;
import com.smartleave.security.LoggedInUser;
import com.smartleave.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * POST /api/auth/register - public, creates an EMPLOYEE only
 * POST /api/auth/login    - returns a JWT and also sets an httpOnly cookie
 * POST /api/auth/logout   - clears the cookie (client-side session invalidation)
 * GET  /api/auth/me       - identity of the current principal
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie(response.token()).toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(response.token()).toString())
                .body(response);
    }

    /** Stateless server: "logout" invalidates the token by removing the cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cleared = ResponseCookie.from(jwtService.getCookieName(), "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal LoggedInUser principal) {
        return authService.me(requireUserId(principal));
    }

    /**
     * The cookie carries the SPA session (httpOnly, SameSite=Lax); the raw
     * token is also returned in the body so API clients such as Postman can
     * send it as {@code Authorization: Bearer}.
     */
    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(jwtService.getCookieName(), token)
                .httpOnly(true)
                .secure(false)          // set to true when serving over HTTPS
                .path("/")
                .maxAge(Duration.ofMillis(jwtService.getExpirationMs()))
                .sameSite("Lax")
                .build();
    }
}
