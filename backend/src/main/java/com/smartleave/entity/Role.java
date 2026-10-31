package com.smartleave.entity;

/**
 * Application roles. Spring Security authorities are always prefixed with
 * ROLE_ (ROLE_EMPLOYEE, ROLE_MANAGER, ROLE_ADMIN).
 */
public enum Role {
    EMPLOYEE,
    MANAGER,
    ADMIN
}
