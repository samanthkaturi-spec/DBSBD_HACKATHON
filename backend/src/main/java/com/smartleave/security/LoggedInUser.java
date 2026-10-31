package com.smartleave.security;

import com.smartleave.entity.Role;

/**
 * Principal stored in the security context after a JWT is validated.
 * Controllers only ever receive identity from here - never from the request
 * body or query string - which is what enforces "never trust the frontend".
 *
 * @param userId database id of the authenticated user
 * @param email  login email (JWT subject)
 * @param role   application role
 * @param name   display name
 */
public record LoggedInUser(Long userId, String email, Role role, String name) {

    public boolean hasRole(Role expected) {
        return role == expected;
    }
}
