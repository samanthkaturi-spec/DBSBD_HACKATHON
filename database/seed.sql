-- =====================================================================
--  Smart Leave Management System - demo data
--  Safe to run repeatedly: every statement is guarded by WHERE NOT EXISTS
--  so executing this file twice inserts nothing the second time.
--
--  Demo credentials (BCrypt hashes, passwords are never stored in plain text)
--    admin@smartleave.com     / Admin@123
--    manager1@smartleave.com  / Manager@123
--    manager2@smartleave.com  / Manager@123
--    employee1..8@smartleave.com / Employee@123
--
--  Dates are anchored to the next Monday so the demo always shows fresh,
--  realistic data regardless of when the seed is executed.
-- =====================================================================

USE smart_leave_management;

-- ------------------------------------------------- 1. departments (3)
INSERT INTO departments (department_name, description)
SELECT 'Engineering', 'Product engineering and quality assurance'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Engineering');

INSERT INTO departments (department_name, description)
SELECT 'Human Resources', 'HR operations, payroll and leave administration'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Human Resources');

INSERT INTO departments (department_name, description)
SELECT 'Sales', 'Field sales and key account management'
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Sales');

-- ------------------------------------------------- 2. leave types (3)
INSERT INTO leave_types (leave_name, annual_entitlement, description, active)
SELECT 'Casual Leave', 12.0, 'Short personal leave, approved by the reporting manager', TRUE
WHERE NOT EXISTS (SELECT 1 FROM leave_types WHERE leave_name = 'Casual Leave');

INSERT INTO leave_types (leave_name, annual_entitlement, description, active)
SELECT 'Sick Leave', 10.0, 'Medical leave; a certificate is required above 2 days', TRUE
WHERE NOT EXISTS (SELECT 1 FROM leave_types WHERE leave_name = 'Sick Leave');

INSERT INTO leave_types (leave_name, annual_entitlement, description, active)
SELECT 'Earned Leave', 15.0, 'Accumulated leave that can be planned well in advance', TRUE
WHERE NOT EXISTS (SELECT 1 FROM leave_types WHERE leave_name = 'Earned Leave');

-- ------------------------------------------------- 3. users (11)
--    1 admin, 2 managers, 8 employees - department and manager are linked by
--    employee_id so the seed can run in a single pass.
INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1001', 'Ajay Kumar', 'admin@smartleave.com',
       '$2a$10$fqDkbwmGROSKMu2RWf2b5OWrW/R9cOc5WF52VxbS3dilwlpxAHl7W',
       'ADMIN',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Human Resources') d),
       NULL, 'HR Director', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1002', 'Priya Sharma', 'manager1@smartleave.com',
       '$2a$10$6s08dA0GaRl2c.1E5nzFdOY6eG65iERvO4B5gOyg22CeQHKPjXVGu',
       'MANAGER',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Engineering') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1001') m),
       'Engineering Manager', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'manager1@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1003', 'Ravi Teja', 'manager2@smartleave.com',
       '$2a$10$XOUjOw2YPK5GBiSvWke5z.cIKeBMCt/JhydKG.OXbQTMB4yjnNRHa',
       'MANAGER',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Sales') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1001') m),
       'Sales Manager', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'manager2@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1004', 'Rahul Verma', 'employee1@smartleave.com',
       '$2a$10$qB1U.Nl9UE3JFcCSVARRNuhRgRTYlj59xyOTkZBlfVHbu2kB/qVGW',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Engineering') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1002') m),
       'Senior Software Engineer', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee1@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1005', 'Sneha Iyer', 'employee2@smartleave.com',
       '$2a$10$ZLyAjejiwAiFH5xgZU9SCuO1ZvvceqzgahABHpkVVODbX7h.a6VsW',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Engineering') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1002') m),
       'Software Engineer', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee2@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1006', 'Arjun Reddy', 'employee3@smartleave.com',
       '$2a$10$k6Omak90hf6up5fSGM4/OughBgxp6VZgPaykiHDvx.y51RPmJkR0e',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Engineering') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1002') m),
       'QA Engineer', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee3@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1007', 'Kavya Nair', 'employee4@smartleave.com',
       '$2a$10$mV.Rt2nLeid/e1LJS3Dsb.mi0TYeihQAplx9yNcXRjE7TFLd0xg1y',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Engineering') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1002') m),
       'DevOps Engineer', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee4@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1008', 'Vikram Singh', 'employee5@smartleave.com',
       '$2a$10$19eyxvnP4d1vf15JSPa8QOreTWGV0CKCw18jo0EwZRekeBT36.ifi',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Sales') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1003') m),
       'Sales Executive', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee5@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1009', 'Ananya Rao', 'employee6@smartleave.com',
       '$2a$10$9vxC4ryOZlyZbPbnL9q4puR3N1UhWjY1KXv4Qxh8rKLq8me8zFyW2',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Sales') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1003') m),
       'Sales Executive', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee6@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1010', 'Deepak Joshi', 'employee7@smartleave.com',
       '$2a$10$oWn8obFISAueMNlzUbg5rOW1MrfghYVZLLn0bEpBbH32KKfz9ltk2',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Sales') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1003') m),
       'Key Account Manager', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee7@smartleave.com');

INSERT INTO users (employee_id, full_name, email, password_hash, role,
                   department_id, manager_id, designation, active)
SELECT 'EMP1011', 'Divya Menon', 'employee8@smartleave.com',
       '$2a$10$9gFRDrNaDxWlzfCPiApGoeSUxtc6kkPM1MjMJvuTD7aVgRGT1r5ja',
       'EMPLOYEE',
       (SELECT id FROM (SELECT id FROM departments WHERE department_name = 'Sales') d),
       (SELECT id FROM (SELECT id FROM users WHERE employee_id = 'EMP1003') m),
       'Marketing Specialist', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee8@smartleave.com');

-- ------------------------------------------------- 4. leave balances (33)
--    One row per user x leave type for the current calendar year.
--    used_days reflects the approved demo applications below.
INSERT INTO leave_balances (user_id, leave_type_id, year, total_days, used_days, available_days)
SELECT u.id, lt.id, YEAR(CURDATE()), lt.annual_entitlement,
       CASE
           WHEN u.employee_id = 'EMP1004' AND lt.leave_name = 'Casual Leave' THEN 3.0
           WHEN u.employee_id = 'EMP1005' AND lt.leave_name = 'Sick Leave'    THEN 2.0
           WHEN u.employee_id = 'EMP1009' AND lt.leave_name = 'Sick Leave'    THEN 1.0
           ELSE 0.0
       END,
       lt.annual_entitlement
         - CASE
               WHEN u.employee_id = 'EMP1004' AND lt.leave_name = 'Casual Leave' THEN 3.0
               WHEN u.employee_id = 'EMP1005' AND lt.leave_name = 'Sick Leave'    THEN 2.0
               WHEN u.employee_id = 'EMP1009' AND lt.leave_name = 'Sick Leave'    THEN 1.0
               ELSE 0.0
           END
FROM users u
CROSS JOIN leave_types lt
WHERE lt.active = TRUE
  AND NOT EXISTS (
        SELECT 1 FROM (SELECT user_id, leave_type_id, year FROM leave_balances) b
         WHERE b.user_id = u.id AND b.leave_type_id = lt.id AND b.year = YEAR(CURDATE()));

-- ------------------------------------------------- 5. leave applications (10)
--    MON = next Monday, so pending requests are always in the future and
--    approved/rejected examples are always in the past.

-- PENDING: Rahul, Casual, Mon-Wed (3 working days)
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, m.mon_date, DATE_ADD(m.mon_date, INTERVAL 2 DAY), 3.0,
       'Family function out of town', 'PENDING', NULL, NULL, NOW(), NOW()
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Casual Leave'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1004'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'PENDING');

-- PENDING: Sneha, Sick, Mon-Tue (2 working days)
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, m.mon_date, DATE_ADD(m.mon_date, INTERVAL 1 DAY), 2.0,
       'Viral fever, resting at home', 'PENDING', NULL, NULL, NOW(), NOW()
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Sick Leave'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1005'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'PENDING');

-- PENDING: Arjun, Earned, Thu-Fri (2 working days)
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, DATE_ADD(m.mon_date, INTERVAL 3 DAY), DATE_ADD(m.mon_date, INTERVAL 4 DAY), 2.0,
       'Outstation wedding', 'PENDING', NULL, NULL, NOW(), NOW()
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Earned Leave'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1006'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'PENDING');

-- PENDING: Kavya, Casual, Mon-Tue (2 working days)
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, m.mon_date, DATE_ADD(m.mon_date, INTERVAL 1 DAY), 2.0,
       'House shifting', 'PENDING', NULL, NULL, NOW(), NOW()
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Casual Leave'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1007'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'PENDING');

-- PENDING: Vikram, Earned, Mon-Fri (5 working days)
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, m.mon_date, DATE_ADD(m.mon_date, INTERVAL 4 DAY), 5.0,
       'Annual vacation with family', 'PENDING', NULL, NULL, NOW(), NOW()
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Earned Leave'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1008'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'PENDING');

-- APPROVED: Rahul, Casual, last Mon-Wed (3 days) -> deducted above
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, DATE_SUB(m.mon_date, INTERVAL 7 DAY), DATE_SUB(m.mon_date, INTERVAL 5 DAY), 3.0,
       'Sibling engagement ceremony', 'APPROVED', 'Approved', mgr.id,
       DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Casual Leave'
JOIN users mgr ON mgr.employee_id = 'EMP1002'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1004'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'APPROVED');

-- APPROVED: Sneha, Sick, last Mon-Tue (2 days) -> deducted above
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, DATE_SUB(m.mon_date, INTERVAL 7 DAY), DATE_SUB(m.mon_date, INTERVAL 6 DAY), 2.0,
       'Dental surgery', 'APPROVED', 'Approved', mgr.id,
       DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Sick Leave'
JOIN users mgr ON mgr.employee_id = 'EMP1002'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1005'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'APPROVED');

-- REJECTED: Arjun, Earned, last Thu-Fri -> balance untouched
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, DATE_SUB(m.mon_date, INTERVAL 4 DAY), DATE_SUB(m.mon_date, INTERVAL 3 DAY), 2.0,
       'Personal trip', 'REJECTED', 'Release week is planned - please reschedule', mgr.id,
       DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Earned Leave'
JOIN users mgr ON mgr.employee_id = 'EMP1002'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1006'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'REJECTED');

-- APPROVED: Ananya, Sick, last Monday (1 day) -> deducted above
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, DATE_SUB(m.mon_date, INTERVAL 7 DAY), DATE_SUB(m.mon_date, INTERVAL 7 DAY), 1.0,
       'Migraine', 'APPROVED', 'Approved', mgr.id,
       DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Sick Leave'
JOIN users mgr ON mgr.employee_id = 'EMP1003'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1009'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'APPROVED');

-- REJECTED: Deepak, Casual, last Thu-Fri -> balance untouched
INSERT INTO leave_applications (user_id, leave_type_id, start_date, end_date, total_days,
                                reason, status, manager_remarks, approved_by, applied_at, updated_at)
SELECT u.id, lt.id, DATE_SUB(m.mon_date, INTERVAL 4 DAY), DATE_SUB(m.mon_date, INTERVAL 3 DAY), 2.0,
       'Attending a friend''s wedding', 'REJECTED', 'Client visit that week - pick another slot', mgr.id,
       DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)
FROM users u
JOIN leave_types lt ON lt.leave_name = 'Casual Leave'
JOIN users mgr ON mgr.employee_id = 'EMP1003'
CROSS JOIN (SELECT DATE_ADD(CURDATE(), INTERVAL (8 - WEEKDAY(CURDATE())) DAY) AS mon_date) m
WHERE u.employee_id = 'EMP1010'
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, leave_type_id, status FROM leave_applications) la
                   WHERE la.user_id = u.id AND la.leave_type_id = lt.id AND la.status = 'REJECTED');

-- ------------------------------------------------- 6. notifications (6)
INSERT INTO notifications (user_id, message, is_read, created_at)
SELECT u.id, 'You have a pending leave request waiting for manager approval.', FALSE, NOW()
FROM users u
WHERE u.employee_id IN ('EMP1004', 'EMP1005', 'EMP1006', 'EMP1007', 'EMP1008')
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, message FROM notifications) n
                   WHERE n.user_id = u.id
                     AND n.message = 'You have a pending leave request waiting for manager approval.');

INSERT INTO notifications (user_id, message, is_read, created_at)
SELECT u.id, 'New leave requests from your team are waiting for a decision.', FALSE, NOW()
FROM users u
WHERE u.employee_id IN ('EMP1002', 'EMP1003')
  AND NOT EXISTS (SELECT 1 FROM (SELECT user_id, message FROM notifications) n
                   WHERE n.user_id = u.id
                     AND n.message = 'New leave requests from your team are waiting for a decision.');
