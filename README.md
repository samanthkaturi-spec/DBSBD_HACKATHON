# Smart Leave Management System

A full-stack leave management application built for a college mini-hackathon.
Employees apply for leave, managers approve or reject it from their own team,
and HR/admin staff administer departments, employees, balances and reports.

It is a **real full-stack system**, not a UI prototype: Spring Boot REST APIs,
MySQL persistence, JWT authentication with role-based authorization, a React
single-page app, automated backend tests, automated frontend tests and a
Postman/Newman API suite.

---

## 1. Technology stack

| Layer     | Technology |
|-----------|------------|
| Backend   | Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation, Actuator) |
| Auth      | JWT (JJWT) + httpOnly cookie, stateless sessions, role based access (`ADMIN`, `MANAGER`, `EMPLOYEE`) |
| Database  | MySQL 8.x (InnoDB), schema + idempotent demo seed in [`database/`](database/) |
| Frontend  | React 18, Vite 6, Tailwind CSS 3, React Router 6, Axios, React Hook Form, Recharts |
| Testing   | JUnit 5 + MockMvc + Testcontainers-style MySQL harness (backend), Vitest + React Testing Library (frontend), Postman/Newman (API), Bash smoke test |

---

## 2. Repository layout

```
smart-leave-management/
├── backend/            Spring Boot application (Maven)
│   ├── src/main/java/com/smartleave/
│   │   ├── config/     Security, CORS, JPA, OpenAPI-ish configuration
│   │   ├── controller/ REST controllers (auth, leaves, manager, admin, ...)
│   │   ├── dto/        Request/response records + bean validation
│   │   ├── entity/     JPA entities mapped to the 6 tables
│   │   ├── exception/  ApiError envelope + global exception handler
│   │   ├── repository/ Spring Data JPA repositories
│   │   ├── security/   JWT issuance/validation, authentication filter
│   │   ├── service/    Business rules (leave, manager, admin, auth, ...)
│   │   └── util/       WorkingDaysCalculator (weekend exclusion)
│   └── src/test/       Unit, service and full-stack integration tests
├── frontend/           React + Vite SPA
│   └── src/
│       ├── components/ Layout, protected routes, reusable UI kit
│       ├── context/    Auth, toast and theme contexts
│       ├── pages/      public, employee, manager, admin pages
│       ├── services/   Axios client (interceptors, error mapping)
│       └── test/       Vitest + RTL test suites
├── database/           schema.sql + seed.sql (also bundled into the jar)
├── docs/               API, architecture (ER diagram), testing, demo guide
└── testing/            Postman collection, environment, reset + runner scripts
```

---

## 3. Prerequisites

* JDK 21 (`java -version`)
* Maven 3.9+ (`mvn -version`) — or use the Maven wrapper if you add one
* MySQL 8.x running locally
* Node.js 20+ and npm

---

## 4. Setup

### 4.1 Database

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed.sql
```

Both scripts are idempotent: `schema.sql` only creates what is missing, and
`seed.sql` guards every insert with `WHERE NOT EXISTS`, so re-running them never
duplicates data.

If you skip this step the backend creates the schema automatically on first
start (`spring.sql.init.mode=always` with the same files bundled in the jar).

### 4.2 Backend

```bash
cd backend
mvn spring-boot:run
```

Configuration comes from environment variables with safe local defaults:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `smart_leave_management` | Schema name |
| `DB_USER` | `root` | MySQL user |
| `DB_PASSWORD` | *(empty)* | MySQL password |
| `JWT_SECRET` | *(ephemeral dev key)* | HMAC secret for tokens |
| `JWT_EXPIRATION_MS` | `86400000` | Token lifetime (24 h) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Allowed SPA origins |
| `SERVER_PORT` | `8080` | HTTP port |

Verify it is up:

```bash
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

### 4.3 Frontend

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173
```

`frontend/.env.example` documents the optional `VITE_API_BASE_URL` override
(the default proxy/base is `http://localhost:8080`).

---

## 5. Demo accounts

The seed creates demo users with BCrypt password hashes. The login page can
fill a demo account email, but does not publish or fill passwords. Configure
local demo passwords securely before signing in; never commit them.

For the API smoke test, export `DEMO_ADMIN_PASSWORD`, `DEMO_MANAGER_PASSWORD`,
and `DEMO_EMPLOYEE_PASSWORD` with the locally configured seeded-account
passwords. Set the corresponding `demo_*_password` variables in the Postman
environment before running its collection.

## 6. Running the tests

```bash
# Backend: unit + service + full-stack integration tests (needs MySQL)
# Tests honour the same DB_* variables, e.g. a MySQL on port 3307:
#   DB_PORT=3307 mvn test
cd backend && mvn test

# Frontend: component and routing tests
cd frontend && npm test

# API suite: resets demo data, then runs the Postman collection with Newman
./testing/run-api-tests.sh

# Quick shell smoke test (94 assertions) - resets demo data first
./testing/smoke-test.sh
```

See [docs/TESTING.md](docs/TESTING.md) for what each suite covers and the
current results.

---

## 7. Documentation

| Document | Contents |
|----------|----------|
| [docs/API.md](docs/API.md) | Every endpoint, payloads, status codes, error format |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, security model, **ER diagram**, request flow |
| [docs/TESTING.md](docs/TESTING.md) | Test suites, how to run them, coverage map |
| [docs/DEMO.md](docs/DEMO.md) | 5-minute demo script + requirements checklist |

---

## 8. Business rules implemented

1. Leave start date cannot be in the past; end date cannot precede start.
2. Duration counts **working days only** — Saturday and Sunday are excluded.
3. A range containing only weekends is invalid (0 working days).
4. Balances are deducted **on approval**, never on submission.
5. Rejection and cancellation never deduct; cancelling approved leave restores
   the balance while the leave has not started.
6. No overlapping `PENDING` or `APPROVED` requests for the same employee.
7. A manager can only decide requests from their own team (`403` otherwise).
8. A manager cannot approve their own request (`403`).
9. Decisions are allowed only while the request is `PENDING` (`409` on repeat).
10. Rejection requires remarks; approval remarks are optional.
11. Reading another employee's request returns `404` (ownership is not leaked).
12. Public registration creates `EMPLOYEE` accounts only; admins are seeded.
13. Every state change produces in-app notifications for the involved users.

---

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Access denied for user 'root'` | Export `DB_USER`/`DB_PASSWORD` before starting the backend |
| Backend starts, API calls return `401` | Log in first — `/api/**` requires a JWT (cookie or `Authorization: Bearer`) |
| `403` from a browser call | The origin is not in `CORS_ALLOWED_ORIGINS` |
| Newman run fails with `409 Overlapping leave` | Reset the demo data: `./testing/reset-db.sh` |
| Port 8080 already in use | `SERVER_PORT=8081 mvn spring-boot:run` and point the frontend base URL at it |
