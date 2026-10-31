# API Reference

Base URL: `http://localhost:8080`

All endpoints are JSON. Every request except the ones listed as *public* must
carry a JWT:

```
Authorization: Bearer <token>
```

The browser client instead relies on the `SLM_TOKEN` **httpOnly cookie** that
`POST /api/auth/login` sets — the two are interchangeable, the filter accepts
either (`JwtAuthenticationFilter`).

---

## 1. Error format

Every non-2xx response uses the same envelope:

```json
{
  "timestamp": "2026-10-31T01:35:47.308883Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "reason: Reason must be between 5 and 500 characters",
  "path": "/api/leaves/apply",
  "fieldErrors": [
    { "field": "reason", "message": "Reason must be between 5 and 500 characters" }
  ]
}
```

| `code` | HTTP | Meaning |
|--------|------|---------|
| `VALIDATION_ERROR` | 400 | Bean-validation failure; `fieldErrors` lists each field |
| `BAD_REQUEST` | 400 | Business rule violated (past date, self-approval, unknown id, …) |
| `UNAUTHORIZED` | 401 | Missing/invalid/expired token, or bad credentials |
| `FORBIDDEN` | 403 | Authenticated but not allowed (wrong role, outside team) |
| `NOT_FOUND` | 404 | Resource does not exist, or belongs to somebody else |
| `CONFLICT` | 409 | Duplicate email, overlap, repeated decision, already cancelled |
| `INTERNAL_ERROR` | 500 | Unexpected failure |

`fieldErrors` is present only for `VALIDATION_ERROR`. `server.error.include-message=always`
is enabled so `message` is always populated (useful in Postman).

---

## 2. Authentication — `/api/auth` (public)

### POST `/api/auth/register`
Creates an **EMPLOYEE** account only (admins are seeded, never registered).

```json
{
  "fullName": "Asha Reddy",
  "email": "asha@smartleave.com",
  "password": "Secret@123",
  "departmentId": 1,
  "designation": "Software Engineer"
}
```

* `201` → `{ "token", "tokenType": "Bearer", "expiresInMs", "user": { … } }` (also sets the cookie)
* `400` validation · `409` duplicate email · `400` unknown department

### POST `/api/auth/login`

```json
{ "email": "admin@smartleave.com", "password": "Admin@123" }
```

* `200` → same body as register, plus `Set-Cookie: SLM_TOKEN=…; HttpOnly; SameSite=Lax`
* `401` — wrong password and unknown email return the **same** message
  (`Invalid email or password`) so accounts cannot be enumerated

```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9…",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "user": {
    "id": 4, "employeeId": "EMP1004", "fullName": "Rahul Verma",
    "email": "employee1@smartleave.com", "role": "EMPLOYEE",
    "departmentId": 1, "departmentName": "Engineering",
    "managerId": 2, "managerName": "Priya Sharma",
    "designation": "Senior Software Engineer", "active": true
  }
}
```

### POST `/api/auth/logout`
`204`, clears the cookie (`SLM_TOKEN=; Max-Age=0`). Stateless server — the
token itself dies with the cookie.

### GET `/api/auth/me`
`200` with the `user` object above, or `401`. Used on page refresh to restore
the session without persisting anything in web storage.

---

## 3. Shared/public data

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| `GET` | `/api/departments` | **public** | `{ id, departmentName, description, employeeCount }[]` |
| `GET` | `/api/leave-types` | any role | `{ id, leaveName, annualEntitlement, description, active }[]` |
| `GET` | `/actuator/health` | public | `{"status":"UP"}` |

---

## 4. Employee — `/api`

### Leave workflow

| Method | Path | Body | Success |
|--------|------|------|---------|
| `POST` | `/api/leaves/apply` | `{ "leaveTypeId", "startDate": "YYYY-MM-DD", "endDate", "reason" }` | `201` application |
| `GET` | `/api/leaves/my-requests` | – | `200` application[] (newest first) |
| `GET` | `/api/leaves/{id}` | – | `200` application · `404` if it is not yours |
| `PUT` | `/api/leaves/{id}/cancel` | `{ "remarks": "optional" }` | `200` application |

Application object:

```json
{
  "id": 1,
  "employee": { "id": 4, "employeeId": "EMP1004", "fullName": "Rahul Verma", "role": "EMPLOYEE",
                 "departmentId": 1, "departmentName": "Engineering",
                 "managerId": 2, "managerName": "Priya Sharma",
                 "designation": "Senior Software Engineer", "active": true },
  "leaveType": { "id": 1, "leaveName": "Casual Leave", "annualEntitlement": 12.0,
                 "description": "Short personal leave, approved by the reporting manager", "active": true },
  "startDate": "2026-11-03",
  "endDate": "2026-11-05",
  "totalDays": 3.0,
  "reason": "Family function out of town",
  "status": "PENDING",
  "managerRemarks": null,
  "approvedById": null,
  "approvedByName": null,
  "appliedAt": "2026-10-31T12:24:15",
  "updatedAt": "2026-10-31T12:24:15"
}
```

Rules enforced on `apply`:

| # | Rule | Failure |
|---|------|---------|
| 1 | start date not in the past | `400` |
| 2 | end ≥ start | `400` |
| 3 | at least one working day (weekends excluded) | `400` |
| 4 | reason 5–500 chars, leave type active | `400` `VALIDATION_ERROR` |
| 5 | no overlapping `PENDING`/`APPROVED` request | `409` |
| 6 | account must be active | `403` |

Cancel rules: `PENDING` and `APPROVED` can be cancelled (`APPROVED` restores the
balance while the leave has not started), `REJECTED`/`CANCELLED` → `409`,
somebody else's request → `404`.

### Dashboard, balance, profile

| Method | Path | Success |
|--------|------|---------|
| `GET` | `/api/employee/dashboard` | `200` see below |
| `GET` | `/api/employee/leave-balance` | `200` balance[] |
| `GET` | `/api/employee/profile` | `200` user object |

```json
{
  "totalEntitlementDays": 37.0, "totalAvailableDays": 31.0, "totalUsedDays": 6.0,
  "pendingCount": 1, "approvedCount": 2, "rejectedCount": 0, "unreadNotifications": 2,
  "balances": [
    { "id": 12, "leaveTypeId": 1, "leaveName": "Casual Leave", "year": 2026,
      "totalDays": 12.0, "usedDays": 6.0, "availableDays": 6.0 }
  ],
  "recentApplications": [ /* application[], max 5 */ ]
}
```

### Notifications

| Method | Path | Success |
|--------|------|---------|
| `GET` | `/api/notifications` | `200` `{ id, message, read, createdAt }[]` |
| `GET` | `/api/notifications/unread-count` | `200` `{ count }` |
| `POST` | `/api/notifications/read-all` | `204` |

---

## 5. Manager — `/api/manager` (**role `MANAGER`**, else `403`)

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/api/manager/dashboard` | `{ teamSize, pendingCount, approvedCount, rejectedCount, onLeaveToday, recentApplications, team }` |
| `GET` | `/api/manager/requests?status=&page=&size=` | paged `{ content, page, size, totalElements, totalPages, last }` |
| `GET` | `/api/manager/team` | team members with balances |
| `PUT` | `/api/manager/requests/{id}/approve` | body `{ "remarks": "optional" }` |
| `PUT` | `/api/manager/requests/{id}/reject` | body `{ "remarks": "required" }` |

Decision rules:

| Rule | Failure |
|------|---------|
| requester must report to this manager | `403` `You can only process leave requests from your own team` |
| a manager cannot decide their own request | `403` `You cannot approve your own leave request` |
| request must still be `PENDING` | `409` `…repeated decisions are not allowed` |
| rejection requires remarks | `400` |
| balance must cover the request on approval | `400` `Insufficient leave balance …` |
| unknown id | `404` |

On approval the balance is deducted **transactionally** — if the deduction fails
the status stays `PENDING` (verified by an integration test).

---

## 6. Admin — `/api/admin` (**role `ADMIN`**, else `403`)

| Method | Path | Body / query | Success |
|--------|------|--------------|---------|
| `GET` | `/api/admin/dashboard` | – | `{ totalEmployees, activeEmployees, totalDepartments, totalApplications, pendingCount, approvedCount, rejectedCount }` |
| `GET` | `/api/admin/employees` | `?q=&role=&departmentId=&page=&size=` | paged user summaries |
| `POST` | `/api/admin/employees` | `{ fullName, email, password, departmentId, designation, role, managerId? }` | `201` |
| `PUT` | `/api/admin/employees/{id}` | partial fields (`designation`, `managerId`, `departmentId`, …) | `200` |
| `DELETE` | `/api/admin/employees/{id}` | – | `200` deactivates (never hard-deletes) |
| `GET` | `/api/admin/employees/{id}/leave-balances` | – | `200` balance[] |
| `PUT` | `/api/admin/leave-balances/{id}` | `{ id, totalDays }` | `200` updated balance |
| `GET` | `/api/admin/departments` | – | `200` department[] |
| `POST` | `/api/admin/departments` | `{ departmentName, description }` | `201` |
| `GET` | `/api/admin/applications` | `?status=&page=&size=` | paged applications |
| `GET` | `/api/admin/reports` | – | see below |
| `GET` | `/api/admin/ping` | – | `200` liveness probe for the admin role |

```json
// GET /api/admin/reports
{
  "byDepartment": [
    { "departmentName": "Engineering", "employeeCount": 7, "applicationCount": 9, "approvedDays": 11.0 }
  ],
  "byMonth": [
    { "year": 2026, "month": 10, "applicationCount": 16, "approvedCount": 5, "approvedDays": 12.0 }
  ],
  "generatedAt": "2026-10-31T07:06:04.688859"
}
```

Admin guard rails: `role` may only be `EMPLOYEE` or `MANAGER` (`400 Role must be
EMPLOYEE or MANAGER`), duplicate email/employee-id → `409`, an employee cannot
be their own manager → `400`, admin accounts cannot be deactivated → `400`,
balance cannot drop below the used days → `400`.

---

## 7. CORS

Only these origins are echoed with `Access-Control-Allow-Origin`:

```
http://localhost:5173  http://127.0.0.1:5173  http://localhost:4173
```

(configurable through `CORS_ALLOWED_ORIGINS`). A preflight from any other
origin gets `403` and **no** `Access-Control-Allow-Origin` header — covered by
the Postman collection's "Foreign origin preflight is refused" test.

---

## 8. Rate of change

There is no HATEOAS or versioning: the API is a flat, predictable REST surface
that mirrors the UI actions, which keeps it easy to demo and to script in
Postman.
