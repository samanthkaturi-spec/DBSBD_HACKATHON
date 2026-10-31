-- =====================================================================
--  Smart Leave Management System - MySQL 8.x Schema
--  Database : smart_leave_management
--  Usage    : Run this file in MySQL Workbench or the mysql CLI.
--             The script is idempotent: re-running it never drops data,
--             it only creates whatever is missing.
--  Reset    : DROP DATABASE smart_leave_management;  then run schema.sql
--             followed by seed.sql.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS smart_leave_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smart_leave_management;

-- ---------------------------------------------------------------------
-- 1. DEPARTMENTS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS departments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    department_name VARCHAR(100) NOT NULL,
    description     VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_name (department_name)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 2. USERS  (self-referencing manager_id)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id   VARCHAR(20)  NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    department_id BIGINT       NOT NULL,
    manager_id    BIGINT       NULL,
    designation   VARCHAR(80)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_employee_id (employee_id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_users_manager FOREIGN KEY (manager_id) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN')),
    KEY idx_users_role (role),
    KEY idx_users_department (department_id),
    KEY idx_users_manager (manager_id)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. LEAVE_TYPES
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS leave_types (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    leave_name        VARCHAR(50)   NOT NULL,
    annual_entitlement DECIMAL(5, 1) NOT NULL DEFAULT 0,
    description       VARCHAR(255)  NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leave_types_name (leave_name),
    CONSTRAINT chk_leave_types_entitlement CHECK (annual_entitlement >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 4. LEAVE_BALANCES   (unique per user + leave type + year)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS leave_balances (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    leave_type_id   BIGINT        NOT NULL,
    year            INT           NOT NULL,
    total_days      DECIMAL(5, 1) NOT NULL DEFAULT 0,
    used_days       DECIMAL(5, 1) NOT NULL DEFAULT 0,
    available_days  DECIMAL(5, 1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_balance_user_type_year (user_id, leave_type_id, year),
    CONSTRAINT fk_balance_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_balance_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_balance_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_balance_total CHECK (total_days >= 0),
    CONSTRAINT chk_balance_used CHECK (used_days >= 0),
    CONSTRAINT chk_balance_available CHECK (available_days >= 0),
    KEY idx_balance_year (year)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 5. LEAVE_APPLICATIONS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS leave_applications (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NOT NULL,
    leave_type_id    BIGINT        NOT NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    total_days       DECIMAL(5, 1) NOT NULL,
    reason           VARCHAR(500)  NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    manager_remarks  VARCHAR(500)  NULL,
    approved_by      BIGINT        NULL,
    applied_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_leave_app_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_leave_app_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_leave_app_approver FOREIGN KEY (approved_by) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_leave_app_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_leave_app_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_leave_app_days CHECK (total_days > 0),
    KEY idx_leave_app_user_status (user_id, status),
    KEY idx_leave_app_status (status),
    KEY idx_leave_app_dates (start_date, end_date),
    KEY idx_leave_app_applied_at (applied_at)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 6. NOTIFICATIONS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    message    VARCHAR(255) NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    KEY idx_notifications_user_read (user_id, is_read, created_at)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Notes on normalization
-- 1NF : every column holds an atomic value; no repeating groups.
-- 2NF : all non-key columns depend on the whole primary key (single-column
--       surrogate keys everywhere, so no partial dependency is possible).
-- 3NF : departments, leave types and balances are separated from users and
--       applications; an application stores only foreign keys instead of
--       repeating department / leave-type / approver details.
-- ---------------------------------------------------------------------
