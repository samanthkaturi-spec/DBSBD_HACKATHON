package com.smartleave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Smart Leave Management System backend.
 *
 * <p>Modular monolith: controllers, services and repositories live in one
 * deployable Spring Boot artifact but are separated by package so each
 * business module (employee / manager / admin) can be extracted later.</p>
 */
@SpringBootApplication
public class SmartLeaveApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLeaveApplication.class, args);
    }
}
