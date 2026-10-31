package com.smartleave.dto;

import com.smartleave.dto.CommonDto.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Authentication request and response payloads. */
public final class AuthDto {

    private AuthDto() {
    }

    /** Public self-registration. The role is deliberately absent: it is always EMPLOYEE. */
    public record RegisterRequest(
            @NotBlank(message = "Full name is required")
            @Size(max = 100, message = "Full name must be at most 100 characters")
            String fullName,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 150, message = "Email must be at most 150 characters")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
            String password,

            Long departmentId,

            @Size(max = 80, message = "Designation must be at most 80 characters")
            String designation) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @NotBlank(message = "Password is required")
            String password) {
    }

    /**
     * @param token       raw JWT (also set as an httpOnly cookie)
     * @param tokenType   always "Bearer"
     * @param expiresInMs token lifetime in milliseconds
     */
    public record AuthResponse(String token,
                               String tokenType,
                               long expiresInMs,
                               UserSummary user) {
    }
}
