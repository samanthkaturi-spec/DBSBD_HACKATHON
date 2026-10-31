# Testing guide

Four independent suites protect the system. Each one runs from the repository
root with a single command, and **all four currently pass**.

| Suite | Command | Current result |
|-------|---------|----------------|
| Backend — JUnit 5 | `cd backend && mvn test` | **50 tests, 0 failures, 0 errors** |
| Frontend — Vitest + RTL | `cd frontend && npm test` | **30 tests in 6 files, all pass** |
| API — Postman/Newman | `./testing/run-api-tests.sh` | **145 assertions / 71 requests, 0 failed** |
| API — shell smoke test | `./testing/smoke-test.sh` | **94 assertions, 0 failed** |

> The backend tests create their **own** database (`smart_leave_management_test`)
> by rewriting `database/schema.sql` and `database/seed.sql`, so they never touch
> demo data. Connection settings come from the same `DB_HOST / DB_PORT /
> DB_USER / DB_PASSWORD` environment variables the application uses — point them
> at your MySQL server, e.g. `DB_PORT=3307 mvn test`.

---

## 1. Backend suite (`mvn test`)

| Class | Tests | What it proves |
|-------|-------|----------------|
| `util/WorkingDaysCalculatorTest` | 6 | Weekend exclusion across single days, ranges, month/year boundaries, weekend-only ranges |
| `security/JwtServiceTest` | 4 | Token round-trip, expiry, wrong secret, claim contents (role, uid, issuer) |
| `service/AuthServiceTest` | 8 | BCrypt verification, duplicate email, register-creates-employee-only, login failure messages |
| `service/LeaveServiceTest` | 11 | Apply rules (past date, end before start, zero working days, overlap → 409), cancel state machine, balance restore, ownership checks |
| `service/ManagerServiceTest` | 9 | Approve/reject, own-team rule, self-approval rule, repeat decision → 409, remarks required, insufficient balance |
| `integration/LeaveWorkflowIntegrationTest` | 4 | **Real MySQL**: apply → approve → balance deducted; reject; cancel restores balance; registered employee has no manager |
| `integration/SecurityIntegrationTest` | 8 | **Real MySQL + full filter chain**: anonymous 401, role matrix 403, invalid token, login failures, cookie lifecycle, registration role lock, other-employee 404, CORS preflight |

Run a single class:

```bash
cd backend
DB_PORT=3307 mvn test -Dtest=SecurityIntegrationTest
```

---

## 2. Frontend suite (`npm test`)

Vitest + React Testing Library + jsdom (`frontend/src/test/`):

| File | Focus |
|------|-------|
| `Login.test.jsx` | Required-field validation, credential submission, error toast from the API envelope |
| `Dashboard.test.jsx` | Renders API-driven cards and recent applications |
| `ApplyLeave.test.jsx` | Form rules, working-day preview, successful submission |
| `MyApplications.test.jsx` | List rendering, status filters, cancel confirmation |
| `ProtectedRoute.test.jsx` | Signed-out redirect, role-based redirect, allowed role renders |
| `ApiError.test.jsx` | Backend error envelope → single human-readable message |

`src/test/helpers.jsx` renders any component with auth, toast, theme and router
providers; `src/test/mocks.js` holds shared fixtures; `src/test/setup.js` wires
jsdom matchers and browser API stubs.

Watch mode: `npm run test:watch`.

---

## 3. API suite (Postman + Newman)

```bash
./testing/run-api-tests.sh          # health check → DB reset → newman run
SKIP_RESET=1 ./testing/run-api-tests.sh   # against the current data
BASE_URL=http://host:8080 ./testing/run-api-tests.sh
```

Files:

* `testing/Smart-Leave-Management.postman_collection.json` — 71 requests in 6 folders
* `testing/Smart-Leave-Management.postman_environment.json` — `base_url`, captured JWTs, generated dates
* `testing/reset-db.sh` — truncates and re-seeds the demo database (no backend restart needed)
* `testing/run-api-tests.sh` — the wrapper above; exits non-zero if any assertion fails

Folders:

| # | Folder | Coverage |
|---|--------|----------|
| 1 | Authentication | login (all 3 roles), register, `/me`, logout clears the httpOnly cookie |
| 2 | Employee workflow | dashboard, balance, apply, list, read one, cancel, notifications |
| 3 | Manager workflow | dashboard, team, paged requests, approve, reject with remarks |
| 4 | Leave Application Workflow | full happy path incl. weekend exclusion (Mon–Wed = 3 days) and balance deduction |
| 5 | Admin workflow | dashboard, employees CRUD, departments, balances, applications, reports |
| 6 | Negative and Security Tests | no token 401, bad JWT 401, wrong role 403, wrong password 401, unknown email 401, cross-employee 404, invalid id 404, past/end-before-start/short-reason 400, overlap 409, repeat approval 409, outside team 403, self approval 403, insufficient balance 400 (rollback keeps PENDING), malformed JSON 400, duplicate email 409, admin role rejected 400, foreign-origin preflight refused |

Dates are computed at run time by pre-request scripts (next Monday + offsets),
so the suite never collides with the seeded demo data.

**Newman gotchas worth remembering**

* Script variables must not be named `data` — it is reserved by the Postman
  sandbox and `const data = …` throws `SyntaxError: Identifier 'data' has
  already been declared`.
* Response headers are read with `pm.response.headers.get(name)` (there is no
  `.values()`).
* Re-running against a database that already contains the generated dates
  produces `409 Overlapping leave`; run `./testing/reset-db.sh` first — the
  wrapper does this automatically.

---

## 4. Shell smoke test

```bash
./testing/smoke-test.sh              # resets the demo data, then runs 94 assertions
SKIP_RESET=1 ./testing/smoke-test.sh # run against the current data instead
```

94 assertions using only `bash`, `curl` and `python3` — handy when Node is not
available. It resets the demo database first (fixed dates must not collide with
a previous run) and creates one throw-away employee plus a few applications
while it runs; `./testing/reset-db.sh` restores the pristine seed afterwards.

---

## 5. What is *not* covered

* Load/performance testing — out of scope for a mini-hackathon MVP.
* Cross-browser visual regression — verified manually in Chromium only.
* Email/SMS notifications — notifications are in-app rows by design.
