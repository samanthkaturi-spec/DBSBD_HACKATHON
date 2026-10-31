# Demo guide + delivery checklist

## 1. Pre-flight (2 minutes, do it once)

```bash
# 1. MySQL must be running (project-local dev server or system MySQL)
mysql -u root -e "SELECT VERSION();"   

# 2. Backend  → http://localhost:8080
cd backend && mvn spring-boot:run

# 3. Reset demo data so the story is reproducible
./testing/reset-db.sh

# 4. Frontend → http://localhost:5173
cd frontend && npm install && npm run dev
```

Open `http://localhost:5173` in an incognito window so no session interferes.
Keep a terminal ready with `./testing/run-api-tests.sh` if a judge asks to see
the API suite.

**Before the demo, run the automated proof once:**

```bash
cd backend  && mvn test        # backend unit + integration tests
cd frontend && npm test        # component tests
./testing/run-api-tests.sh     # Postman collection via Newman (145 assertions)
```

---

## 2. Five-minute script

### 0:00 — Opening
> "This is a real full-stack leave management system: Spring Boot + MySQL +
> JWT authorization on the back end, React + Tailwind on the front end, and
> three automated test suites."

### 0:20 — Employee applies (role: EMPLOYEE)
1. Login page → click the **Employee** demo row → *Sign in*.
2. Dashboard: balances, recent applications, pending count come from the API.
3. **Apply for Leave** → pick *Casual Leave*, choose Mon→Wed next month →
   the UI previews **"Requested duration: 3 working days"** (weekends excluded).
4. Submit → success banner, then **My Applications** shows the new `PENDING` row.

**Talking point:** the same weekend-exclusion rule runs in Java
(`WorkingDaysCalculator`) with its own unit tests, so client and server agree.

### 1:30 — Manager decides (role: MANAGER)
1. Sign out → **Manager** demo row → sign in.
2. **Pending Approvals** shows the request that was just created.
3. Click **Approve** → confirmation dialog → *Approve*.
   (Optionally show a **Reject** first: the dialog refuses to submit without
   remarks — that is a server rule too, `400`.)
4. The row leaves the queue immediately.

**Talking point:** one transaction covers status change + balance deduction +
two notifications; a failed deduction rolls the whole thing back.

### 2:30 — Back to the employee
1. Sign out → **Employee** → **Leave Balance**: used days increased, available
   days decreased by exactly 3.
2. **My Applications**: status `APPROVED`, "Approved by Priya Sharma".
3. **Notifications**: one unread message about the decision.

### 3:10 — Admin (role: ADMIN)
1. Sign out → **Admin** → sign in.
2. **HR dashboard**: totals for employees, departments, applications by status.
3. **Departments** → create a department (validation shows inline).
4. **Employees** → search, edit a designation, deactivate somebody (confirmation
   dialog, and the admin account refuses to deactivate itself).
5. **Reports** → two charts (department-wise, monthly trend) + tables, straight
   from MySQL aggregates, with a *Print* action.

### 4:00 — Security, on request
Set `DEMO_EMPLOYEE_PASSWORD` in your local shell to the password configured
for the seeded employee account before running the login request below.

```bash
# no token → 401
curl -i http://localhost:8080/api/admin/dashboard
# employee token on an admin route → 403
curl -i -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"employee1@smartleave.com\",\"password\":\"$DEMO_EMPLOYEE_PASSWORD\"}"   # take the token
curl -i http://localhost:8080/api/admin/dashboard -H "Authorization: Bearer <token>"
# foreign origin preflight → 403 and no Access-Control-Allow-Origin
curl -i -X OPTIONS http://localhost:8080/api/auth/login -H "Origin: https://evil.example" \
  -H "Access-Control-Request-Method: POST"
```

Or simply run `./testing/run-api-tests.sh` live — it resets the data, runs 71
requests and prints `145 assertions, 0 failed`.

### 4:40 — Close
> "Backend, database, frontend, tests and docs are all in the repository —
> `README.md` has the setup, `docs/` has the API reference, ER diagram and the
> testing guide."

---

## 3. If something goes wrong

| Problem | Fix (10 seconds) |
|---------|------------------|
| Port 8080 busy | `lsof -i :8080` then kill the stale `java -jar` process |
| Data looks messy mid-demo | `./testing/reset-db.sh` (no restart needed) |
| Frontend shows a 401 flash | Hard refresh; the session restores from the cookie |
| Backend won't start | MySQL not running, or wrong `DB_PORT` (local dev server uses `3307`) |

---

## 4. Delivery checklist

### Functional
- [x] Employees can register, log in and view their own profile
- [x] Employees apply for leave and see every request with its status
- [x] Weekend-excluded working-day calculation is shown before submitting
- [x] Balances are deducted only on approval and restored on cancellation
- [x] Managers see only their own team's requests and can approve/reject
- [x] Rejection requires remarks (enforced by the server, not just the UI)
- [x] Admins manage departments, employees, balances and view all applications
- [x] Admin reports aggregate by department and by month
- [x] In-app notifications are created for apply / approve / reject / cancel

### Security
- [x] BCrypt password hashing — no plain-text credentials anywhere
- [x] JWT auth with httpOnly cookie (SPA) and Bearer header (tools)
- [x] Role-based authorization: `ADMIN`, `MANAGER`, `EMPLOYEE` route rules
- [x] Object-level checks: own-requests-only, own-team-only, no self-approval
- [x] CORS allow-list; foreign origins get `403` without ACAO
- [x] Uniform error envelope with no stack traces or SQL leakage
- [x] Login does not reveal whether an email exists

### Quality
- [x] Backend automated tests (unit + service + full-stack integration on real MySQL)
- [x] Frontend automated tests (Vitest + React Testing Library)
- [x] Postman collection with positive **and** negative/security tests (Newman)
- [x] Shell smoke test for a quick manual run
- [x] Integration tests verify database operations (persistence, rollback, balances)
- [x] `mvn test`, `npm test` and `run-api-tests.sh` all pass

### Documentation
- [x] `README.md` — installation, configuration, credentials, structure
- [x] `docs/API.md` — every endpoint with payloads and status codes
- [x] `docs/ARCHITECTURE.md` — layers, security model, **ER diagram**, flows
- [x] `docs/TESTING.md` — how to run every suite and what it covers
- [x] `docs/DEMO.md` — this runbook and checklist

### Repository hygiene
- [x] `.gitignore` covers `target/`, `node_modules/`, `.mysql/`, IDE files
- [x] No secrets committed — all credentials come from environment variables
- [x] SQL scripts are idempotent and versioned in `database/`
