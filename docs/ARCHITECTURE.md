# Architecture

## 1. Big picture

```mermaid
flowchart LR
    subgraph Client["Browser (SPA)"]
        UI["React 18 + Vite<br/>routes · contexts · axios"]
    end

    subgraph Backend["Spring Boot 3.5 / Java 21"]
        SEC["Security filter chain<br/>JWT + CORS + RBAC"]
        CTL["Controllers<br/>thin HTTP layer"]
        SVC["Services<br/>business rules @Transactional"]
        REP["Spring Data repositories"]
    end

    DB[("MySQL 8<br/>6 InnoDB tables")]

    UI -- "POST /api/leaves/apply<br/>Cookie: SLM_TOKEN=…" --> SEC
    SEC --> CTL --> SVC --> REP --> DB
    DB --> REP --> SVC
    SVC -- "JSON DTO" --> CTL --> UI
```

The backend is deliberately layered:

| Layer | Package | Responsibility |
|-------|---------|----------------|
| Controller | `controller` | Map HTTP ↔ DTO, choose status codes, no business logic |
| Service | `service` | All business rules, `@Transactional` boundaries, notifications |
| Repository | `repository` | Spring Data JPA queries (`@Query` for overlap/lock checks) |
| Entity | `entity` | JPA mapping, cascades, `@Version`-free optimistic design with `SELECT … FOR UPDATE` on decisions |
| Security | `security` | JWT issue/validate, `LoggedinUser` principal, filters |
| Exception | `exception` | `ApiException` + `@RestControllerAdvice` producing one error envelope |

Frontend mirrors that idea: `services/api.js` owns HTTP, `context/*` owns global
state (auth, toasts, theme), `pages/*` own screen composition, `components/ui.jsx`
owns presentational primitives.

---

## 2. Security model

```mermaid
flowchart TD
    R[Request] --> F{JWT filter<br/>cookie or Bearer}
    F -->|no/invalid token| U[401 UNAUTHORIZED]
    F -->|valid| A{Path rule}
    A -->|/api/auth/**, GET /api/departments, OPTIONS| P[permitAll]
    A -->|/api/admin/**| RA{role == ADMIN?}
    A -->|/api/manager/**| RM{role == MANAGER?}
    A -->|/api/**| AC[authenticated]
    RA -->|no| F403[403 FORBIDDEN]
    RM -->|no| F403
    AC --> CTL[Controller]
    RA -->|yes| CTL
    RM -->|yes| CTL
```

* **Stateless** (`SessionCreationPolicy.STATELESS`) — no server session, tokens
  are HMAC-SHA512 signed (JJWT), 24 h expiry, issuer `smart-leave-management`.
* **httpOnly cookie + Bearer**: the SPA never touches the token (XSS-proof
  storage), while Postman/curl use the `Authorization` header.
* **CSRF is disabled** because the cookie is only ever sent to a same-origin API
  with an explicit `Origin` check through CORS, and every mutation is a
  `POST/PUT/DELETE` with `Content-Type: application/json`.
* **CORS allow-list** — an unknown origin gets no `Access-Control-Allow-Origin`
  header at all (verified by the Postman suite).
* **Object-level authorization** in services, not only in the URL rules:
  a manager can only decide requests of direct reports (`assertInsideTeam`),
  and an employee reading `/api/leaves/{id}` of a colleague gets `404` rather
  than `403`, so existence is never leaked.
* **Passwords** are BCrypt-hashed; no plaintext credential exists anywhere.

---

## 3. Database — ER diagram

```mermaid
erDiagram
    departments ||--o{ users : "employs"
    users }o--|| departments : "belongs to"
    users |o--o{ users : "manager_id (self reference)"
    users ||--o{ leave_balances : "owns"
    leave_types ||--o{ leave_balances : "has balance for"
    users ||--o{ leave_applications : "applies"
    leave_types ||--o{ leave_applications : "categorised as"
    users |o--o{ leave_applications : "approved_by (nullable)"
    users ||--o{ notifications : "receives"

    departments {
        bigint id PK
        varchar department_name UK
        varchar description
        datetime created_at
    }
    users {
        bigint id PK
        varchar employee_id UK
        varchar full_name
        varchar email UK
        varchar password_hash
        varchar role "EMPLOYEE|MANAGER|ADMIN"
        bigint department_id FK
        bigint manager_id FK "nullable, self ref"
        varchar designation
        boolean active
        datetime created_at
        datetime updated_at
    }
    leave_types {
        bigint id PK
        varchar leave_name UK
        decimal annual_entitlement
        varchar description
        boolean active
    }
    leave_balances {
        bigint id PK
        bigint user_id FK
        bigint leave_type_id FK
        int year "unique per user+type"
        decimal total_days
        decimal used_days
        decimal available_days
    }
    leave_applications {
        bigint id PK
        bigint user_id FK
        bigint leave_type_id FK
        date start_date
        date end_date
        decimal total_days "working days"
        varchar reason "5..500"
        varchar status "PENDING|APPROVED|REJECTED|CANCELLED"
        varchar manager_remarks
        bigint approved_by FK "nullable"
        datetime applied_at
        datetime updated_at
    }
    notifications {
        bigint id PK
        bigint user_id FK
        varchar message
        boolean is_read
        datetime created_at
    }
```

Notes:

* Invariants such as `available_days = total_days - used_days` are maintained by
  `LeaveBalanceService` (single write path) rather than triggers.
* `leave_applications.status` is a `VARCHAR(20)` mapped to the Java enum
  `LeaveStatus`; decisions use `SELECT … FOR UPDATE` (`findForUpdate`) so two
  simultaneous approvals cannot both succeed.
* The overlap check is a single SQL predicate
  (`start_date <= :end AND end_date >= :start` plus status filter), so rule 9 is
  enforced by the database, not by looping in Java.
* Seed dates are anchored to the **next Monday**, so the demo always shows fresh
  data no matter when it is executed.

---

## 4. Request lifecycle (approve flow)

```mermaid
sequenceDiagram
    participant M as Manager browser
    participant C as ManagerController
    participant S as ManagerService
    participant L as LeaveBalanceService
    participant D as MySQL
    participant E as Employee browser

    M->>C: PUT /api/manager/requests/16/approve
    C->>S: approve(managerId=2, applicationId=16)
    S->>D: SELECT … FOR UPDATE (application)
    S->>D: verify employee.manager_id == 2
    S->>D: status == PENDING ?
    S->>L: deduct(4, Casual, 2026, 3 days)
    L->>D: UPDATE leave_balances SET used+3, available-3
    L-->>S: ok
    S->>D: status = APPROVED, approved_by = 2
    S->>D: INSERT notifications (employee + self)
    D-->>S: committed
    S-->>M: 200 application (APPROVED)
    E-->>M: sees APPROVED + new notification
```

Everything above happens in **one transaction**: if the balance deduction fails
the whole thing rolls back and the request stays `PENDING`
(`LeaveWorkflowIntegrationTest` asserts exactly that).

---

## 5. Working-day calculation

`util/WorkingDaysCalculator` walks the range and counts only
`MON..FRI` — it is a pure function with a dedicated unit test suite
(half-open ranges, single day, weekend-only, month and year boundaries).
The same rule is used when the SPA previews "Requested duration: 3 working
days", so the client and server always agree.

---

## 6. Frontend architecture

```mermaid
flowchart TD
    M[main.jsx] --> AC[AuthProvider - /api/auth/me on boot]
    AC --> TC[ThemeProvider]
    TC --> TO[ToastProvider]
    TO --> R[Router]
    R --> PB{Public<br/>/login /register /unauthorized}
    R --> PR{ProtectedRoute}
    PR -->|EMPLOYEE| E[employee pages]
    PR -->|MANAGER| MG[manager pages]
    PR -->|ADMIN| AD[admin pages]
    E --> L[Layout / DashboardLayout]
    MG --> L
    AD --> L
```

* `ProtectedRoute` renders a redirect (not a blank screen) for wrong roles and
  keeps the requested location so login returns the user to where they were.
* `services/api.js` centralises base URL, JSON headers, 401 → logout handling
  and maps the backend error envelope to a single human-readable message used
  by every toast.
* Theme (light/dark) is the only value in `localStorage`; the session itself
  lives exclusively in the httpOnly cookie.
* Recharts renders the admin reports from `/api/admin/reports`; no chart data is
  fabricated client-side.

---

## 7. Key design decisions

| Decision | Why |
|----------|-----|
| Deduct balance on approval, not on submission | Matches real HR policy and keeps `PENDING` reversible |
| Notifications as rows, not email | Hackathon scope; instant in-app feedback without SMTP |
| Flat DTOs returned by services | Controllers stay trivial; response shape is stable for the SPA and Postman |
| Single error envelope + `code` | The SPA maps `code` to a toast, Postman asserts on it |
| Idempotent SQL seed with `WHERE NOT EXISTS` | Any machine can re-run the seed safely; demo data is reproducible |
| `ddl-auto=validate` | Schema is owned by `database/schema.sql`; Hibernate never mutates it silently |
| Cookie auth for SPA + Bearer for tools | XSS-safe browser session, scriptable API tests |
