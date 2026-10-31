#!/usr/bin/env bash
# =====================================================================
#  Smart Leave Management System - end-to-end API smoke test
#
#  Runs ~35 assertions against a live backend (default http://localhost:8080)
#  covering: authentication, role based access, employee workflows,
#  manager approvals, business rules and admin operations.
#
#  Usage:
#     ./smoke-test.sh                       # against http://localhost:8080
#     BASE_URL=http://host:port ./smoke-test.sh
#
#  NOTE: the script resets the demo database before it starts (so fixed dates
#  never collide with a previous run) and creates one throw-away employee and a
#  few leave applications. Set SKIP_RESET=1 to run against the current data;
#  run ./testing/reset-db.sh afterwards to restore the pristine demo data.
# =====================================================================
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
: "${DEMO_ADMIN_PASSWORD:?Set DEMO_ADMIN_PASSWORD to the locally configured admin password}"
: "${DEMO_MANAGER_PASSWORD:?Set DEMO_MANAGER_PASSWORD to the locally configured manager password}"
: "${DEMO_EMPLOYEE_PASSWORD:?Set DEMO_EMPLOYEE_PASSWORD to the locally configured employee password}"
BODY=$(mktemp)
PASS=0
FAIL=0
FAILED=()

py() { python3 -c "$1"; }
auth_body() { python3 -c 'import json,sys; print(json.dumps({"email":sys.argv[1],"password":sys.argv[2]}))' "$1" "$2"; }

NEXT_MON=$(py "import datetime;d=datetime.date.today();print(d+datetime.timedelta(days=(7-d.weekday())%7 or 7))")
NEXT_MON2=$(py "import datetime;print(datetime.date.fromisoformat('$NEXT_MON')+datetime.timedelta(days=7))")
NEXT_MON3=$(py "import datetime;print(datetime.date.fromisoformat('$NEXT_MON')+datetime.timedelta(days=14))")
NEXT_MON3_END=$(py "import datetime;print(datetime.date.fromisoformat('$NEXT_MON')+datetime.timedelta(days=15))")

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); FAILED+=("$1"); printf '  \033[31mFAIL\033[0m  %s\n' "$1"; }

assert_eq() {   # description expected actual
  if [ "$2" = "$3" ]; then ok "$1 ($3)"; else bad "$1 - expected [$2] got [$3]"; fi
}
assert_contains() {  # description needle haystack
  case "$3" in
    *"$2"*) ok "$1" ;;
    *) bad "$1 - [$2] not found in: ${3:0:160}" ;;
  esac
}

# call METHOD PATH TOKEN BODY  -> prints http status, response body in $BODY
call() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  local args=(-s -X "$method" -H "Content-Type: application/json" -o "$BODY" -w "%{http_code}")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}" "$BASE$path"
}

jqf() { jq -r "$1" "$BODY" 2>/dev/null; }

echo "=== Smart Leave Management - API smoke test ==="
echo "Target: $BASE"
echo "Dates: next Monday = $NEXT_MON, following = $NEXT_MON2, +3 weeks = $NEXT_MON3"
echo

# ------------------------------------------------------- fresh demo data
# The script creates leave requests on fixed dates (next Monday and friends),
# so it always starts from the pristine seed. Set SKIP_RESET=1 to keep the
# current data instead.
RUN_TAG="${RANDOM}${RANDOM}"
if [ "${SKIP_RESET:-0}" != "1" ]; then
    if bash "$(dirname "${BASH_SOURCE[0]}")/reset-db.sh" >/dev/null 2>&1; then
        echo "Database reset to the demo seed."
    else
        echo "WARNING: could not reset the database - a previous run may cause 409 conflicts." >&2
    fi
fi

# ---------------------------------------------------------------- health
status=$(call GET /actuator/health)
assert_eq "backend health endpoint" "200" "$status"

# ---------------------------------------------------------- authentication
status=$(call POST /api/auth/login "" $(auth_body 'admin@smartleave.com' "$DEMO_ADMIN_PASSWORD"))
assert_eq "admin login" "200" "$status"
ADMIN_TOKEN=$(jqf .token)

status=$(call POST /api/auth/login "" $(auth_body 'manager1@smartleave.com' "$DEMO_MANAGER_PASSWORD"))
assert_eq "manager login" "200" "$status"
M1_TOKEN=$(jqf .token)
assert_eq "login response role" "MANAGER" "$(jqf '.user.role')"

status=$(call POST /api/auth/login "" $(auth_body 'employee1@smartleave.com' "$DEMO_EMPLOYEE_PASSWORD"))
assert_eq "employee login" "200" "$status"
EMP_TOKEN=$(jqf .token)
assert_eq "employee role is EMPLOYEE" "EMPLOYEE" "$(jqf '.user.role')"
assert_eq "token type is Bearer" "Bearer" "$(jqf .tokenType)"

status=$(call POST /api/auth/login "" $(auth_body 'admin@smartleave.com' "$DEMO_ADMIN_PASSWORD"))
assert_eq "wrong password rejected" "401" "$status"

status=$(call POST /api/auth/login "" '{"email":"nobody@smartleave.com","password":"whatever123"}')
assert_eq "unknown email rejected" "401" "$status"

status=$(call POST /api/auth/register "" "{\"fullName\":\"Public Signer\",\"email\":\"public.signer.${RUN_TAG}@smartleave.com\",\"password\":\"Public@123\",\"departmentId\":1,\"designation\":\"Executive\"}")
assert_eq "public registration (EMPLOYEE only)" "201" "$status"
REG_ROLE=$(jqf .user.role)
assert_eq "registration cannot pick a role" "EMPLOYEE" "$REG_ROLE"
REG_TOKEN=$(jqf .token)

# ------------------------------------------------------- security negative
status=$(call GET /api/admin/dashboard "")
assert_eq "no token -> 401" "401" "$status"
assert_contains "401 payload has message" "Authentication required" "$(cat "$BODY")"

status=$(call GET /api/admin/dashboard "not-a-valid-jwt")
assert_eq "invalid JWT -> 401" "401" "$status"

status=$(call GET /api/admin/dashboard "$EMP_TOKEN")
assert_eq "employee calling admin API -> 403" "403" "$status"
assert_contains "403 payload says forbidden" "FORBIDDEN" "$(cat "$BODY")"

status=$(call GET /api/manager/requests "$EMP_TOKEN")
assert_eq "employee calling manager API -> 403" "403" "$status"

status=$(call GET /api/admin/dashboard "$M1_TOKEN")
assert_eq "manager calling admin API -> 403" "403" "$status"

status=$(call GET /api/employee/dashboard "$REG_TOKEN")
assert_eq "employee can open own dashboard" "200" "$status"

# ------------------------------------------------------------- employee API
status=$(call GET /api/employee/dashboard "$EMP_TOKEN")
assert_eq "employee dashboard" "200" "$status"
BALANCES=$(jqf '.balances | length')
[ "$BALANCES" -ge 3 ] && ok "dashboard returns all leave balances ($BALANCES)" \
                      || bad "dashboard balances expected >= 3 got $BALANCES"
AVAIL_START=$(printf '%.0f' "$(jqf '.totalAvailableDays')")

status=$(call GET /api/employee/leave-balance "$EMP_TOKEN")
assert_eq "leave balance endpoint" "200" "$status"
assert_contains "balance rows include Casual Leave" "Casual Leave" "$(cat "$BODY")"

status=$(call GET /api/employee/profile "$EMP_TOKEN")
assert_eq "employee profile" "200" "$status"
assert_eq "profile email matches principal" "employee1@smartleave.com" "$(jqf .email)"

status=$(call GET /api/notifications "$EMP_TOKEN")
assert_eq "notifications endpoint" "200" "$status"

status=$(call GET /api/leave-types "$EMP_TOKEN")
assert_eq "leave types endpoint" "200" "$status"
LEAVE_TYPE_ID=$(jqf '.[0].id')

# ------------------------------------------------- employee apply workflow
status=$(call POST /api/leaves/apply "$EMP_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON\",\"endDate\":\"$NEXT_MON\",\"reason\":\"Smoke test single day leave\"}")
assert_eq "apply for leave -> 201" "201" "$status"
APP_ID=$(jqf .id)
assert_eq "new application is PENDING" "PENDING" "$(jqf .status)"
assert_eq "duration excludes weekends" "1" "$(jqf .totalDays)"

status=$(call POST /api/leaves/apply "$EMP_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON\",\"endDate\":\"$NEXT_MON\",\"reason\":\"Duplicate of the same week\"}")
assert_eq "overlapping application -> 409" "409" "$status"
assert_contains "overlap message" "Overlapping" "$(cat "$BODY")"

status=$(call POST /api/leaves/apply "$EMP_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"2020-01-06\",\"endDate\":\"2020-01-07\",\"reason\":\"Historic leave request\"}")
assert_eq "past start date -> 400" "400" "$status"

status=$(call POST /api/leaves/apply "$EMP_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON2\",\"endDate\":\"$NEXT_MON\",\"reason\":\"Backwards range\"}")
assert_eq "end before start -> 400" "400" "$status"

status=$(call POST /api/leaves/apply "$EMP_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON\",\"endDate\":\"$NEXT_MON\",\"reason\":\"x\"}")
assert_eq "short reason rejected by validation" "400" "$status"

status=$(call GET "/api/leaves/$APP_ID" "$EMP_TOKEN")
assert_eq "fetch own application" "200" "$status"
assert_eq "application id matches" "$APP_ID" "$(jqf .id)"

status=$(call GET "/api/leaves/$((APP_ID + 99999))" "$EMP_TOKEN")
assert_eq "unknown application id -> 404" "404" "$status"

status=$(call GET /api/leaves/my-requests "$EMP_TOKEN")
assert_eq "my requests list" "200" "$status"

# ------------------------------------------------------ manager decisions
status=$(call GET /api/manager/dashboard "$M1_TOKEN")
assert_eq "manager dashboard" "200" "$status"
TEAM_SIZE=$(jqf .teamSize)
[ "$TEAM_SIZE" -ge 4 ] && ok "manager team size = $TEAM_SIZE" || bad "manager team size expected >= 4 got $TEAM_SIZE"

status=$(call GET "/api/manager/requests?status=PENDING" "$M1_TOKEN")
assert_eq "pending requests list" "200" "$status"
PENDING_PAGE=$(jqf '.totalElements')
[ "$PENDING_PAGE" -ge 1 ] && ok "at least one pending request ($PENDING_PAGE)" \
                          || bad "expected pending requests, got $PENDING_PAGE"

# approve the request created above (employee1 reports to manager1)
status=$(call PUT "/api/manager/requests/$APP_ID/approve" "$M1_TOKEN" '{"remarks":"Approved via smoke test"}')
assert_eq "manager approves request" "200" "$status"
assert_eq "status becomes APPROVED" "APPROVED" "$(jqf .status)"
assert_eq "approver recorded" "Priya Sharma" "$(jqf '.approvedByName')"

status=$(call PUT "/api/manager/requests/$APP_ID/approve" "$M1_TOKEN" '{"remarks":"again"}')
assert_eq "repeated approval blocked -> 409" "409" "$status"
assert_contains "duplicate approval message" "already" "$(cat "$BODY")"

status=$(call GET /api/employee/dashboard "$EMP_TOKEN")
AVAIL_AFTER=$(printf '%.0f' "$(jqf '.totalAvailableDays')")
assert_eq "balance deducted by exactly 1 day" "$((AVAIL_START - 1))" "$AVAIL_AFTER"

# rejection requires remarks - use employee2's seeded pending request
status=$(call GET "/api/manager/requests?status=PENDING" "$M1_TOKEN")
REJECT_ID=$(jqf '.content[] | select(.employee.email=="employee2@smartleave.com") | .id' | head -1)
if [ -z "$REJECT_ID" ]; then
  status=$(call POST /api/leaves/apply "$EMP_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON2\",\"endDate\":\"$NEXT_MON2\",\"reason\":\"Fallback request for rejection test\"}")
  REJECT_ID=$(jqf .id)
fi
status=$(call PUT "/api/manager/requests/$REJECT_ID/reject" "$M1_TOKEN" '{"remarks":""}')
assert_eq "reject without remarks -> 400" "400" "$status"
assert_contains "remarks required message" "remarks are required" "$(cat "$BODY")"

status=$(call PUT "/api/manager/requests/$REJECT_ID/reject" "$M1_TOKEN" '{"remarks":"Please reschedule next month"}')
assert_eq "manager rejects with remarks" "200" "$status"
assert_eq "status becomes REJECTED" "REJECTED" "$(jqf .status)"

# ---------------------------------------------------------- team scoping
OTHER_TOKEN=""
status=$(call POST /api/auth/login "" $(auth_body 'employee5@smartleave.com' "$DEMO_EMPLOYEE_PASSWORD"))
if [ "$status" = "200" ]; then OTHER_TOKEN=$(jqf .token); fi
status=$(call POST /api/leaves/apply "$OTHER_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON3\",\"endDate\":\"$NEXT_MON3\",\"reason\":\"Sales team leave request\"}")
assert_eq "employee2 applies (sales team)" "201" "$status"
SALES_APP_ID=$(jqf .id)

status=$(call PUT "/api/manager/requests/$SALES_APP_ID/approve" "$M1_TOKEN" '{"remarks":"trying to approve outside my team"}')
assert_eq "manager approving outside team -> 403" "403" "$status"

# self approval
M2_LOGIN=$(call POST /api/auth/login "" $(auth_body 'manager2@smartleave.com' "$DEMO_MANAGER_PASSWORD"))
M2_TOKEN=$(jqf .token)
status=$(call POST /api/leaves/apply "$M2_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON2\",\"endDate\":\"$NEXT_MON2\",\"reason\":\"Manager needs a day off\"}")
assert_eq "manager can apply for leave" "201" "$status"
M2_APP_ID=$(jqf .id)
status=$(call PUT "/api/manager/requests/$M2_APP_ID/approve" "$M2_TOKEN" '{"remarks":"self approved"}')
assert_eq "self approval blocked -> 403" "403" "$status"

# cancellation
status=$(call PUT "/api/leaves/$SALES_APP_ID/cancel" "$OTHER_TOKEN" '{"remarks":"plans changed"}')
assert_eq "employee cancels own pending request" "200" "$status"
assert_eq "cancelled status" "CANCELLED" "$(jqf .status)"

status=$(call PUT "/api/leaves/$SALES_APP_ID/cancel" "$M1_TOKEN" '{"remarks":"manager trying to cancel"}')
assert_eq "cancelling someone else's request -> 403" "403" "$status"

# insufficient balance
status=$(call GET "/api/admin/employees?q=employee5@smartleave.com&size=5" "$ADMIN_TOKEN")
assert_eq "admin employee search" "200" "$status"
TEST_EMP_ID=$(jqf '.content[0].id')
status=$(call GET "/api/admin/employees/$TEST_EMP_ID/leave-balances" "$ADMIN_TOKEN")
assert_eq "admin reads employee balances" "200" "$status"
CASUAL_BAL_ID=$(jqf '.[] | select(.leaveName=="Casual Leave") | .id')
status=$(call PUT "/api/admin/leave-balances/$CASUAL_BAL_ID" "$ADMIN_TOKEN" "{\"id\":$CASUAL_BAL_ID,\"totalDays\":1}")
assert_eq "admin adjusts entitlement" "200" "$status"

status=$(call POST /api/leaves/apply "$OTHER_TOKEN" "{\"leaveTypeId\":$LEAVE_TYPE_ID,\"startDate\":\"$NEXT_MON3\",\"endDate\":\"$NEXT_MON3_END\",\"reason\":\"Needs two days but only one is left\"}")
assert_eq "employee applies after balance cut" "201" "$status"
LOW_APP_ID=$(jqf .id)
status=$(call PUT "/api/manager/requests/$LOW_APP_ID/approve" "$M2_TOKEN" '{"remarks":"approve"}')
assert_eq "insufficient balance blocks approval -> 400" "400" "$status"
assert_contains "insufficient balance message" "Insufficient leave balance" "$(cat "$BODY")"
status=$(call GET "/api/leaves/$LOW_APP_ID" "$OTHER_TOKEN")
assert_eq "failed approval leaves request PENDING" "PENDING" "$(jqf .status)"
status=$(call PUT "/api/admin/leave-balances/$CASUAL_BAL_ID" "$ADMIN_TOKEN" "{\"id\":$CASUAL_BAL_ID,\"totalDays\":12}")
assert_eq "entitlement restored" "200" "$status"

# ------------------------------------------------------------ admin module
status=$(call GET /api/admin/dashboard "$ADMIN_TOKEN")
assert_eq "admin dashboard" "200" "$status"
DEPTS=$(jqf .totalDepartments)
[ "$DEPTS" -ge 3 ] && ok "admin sees $DEPTS departments (seed guarantees 3)" || bad "expected >= 3 departments, got $DEPTS"
EMPLOYEES=$(jqf .totalEmployees)
[ "$EMPLOYEES" -ge 11 ] && ok "admin sees $EMPLOYEES employees" || bad "expected >= 11 employees, got $EMPLOYEES"
[ "$(jqf .pendingCount)" -ge 1 ] && ok "admin dashboard counts pending requests" || bad "no pending count"

status=$(call GET "/api/admin/employees?page=0&size=5" "$ADMIN_TOKEN")
assert_eq "employee listing with pagination" "200" "$status"
assert_eq "page size respected" "5" "$(jqf '.content | length')"

# unique per run so the script can be re-run with SKIP_RESET=1 as well
TMP_EMAIL="temp.test.${RUN_TAG}@smartleave.com"
TMP_DEPT="Quality Assurance ${RUN_TAG}"

status=$(call POST /api/admin/employees "$ADMIN_TOKEN" "{\"fullName\":\"Temp Test User\",\"email\":\"$TMP_EMAIL\",\"password\":\"Temp@12345\",\"departmentId\":1,\"managerId\":2,\"designation\":\"Intern\",\"role\":\"EMPLOYEE\"}")
assert_eq "admin creates employee -> 201" "201" "$status"
NEW_EMP_ID=$(jqf .id)

status=$(call POST /api/admin/employees "$ADMIN_TOKEN" "{\"fullName\":\"Temp Test User\",\"email\":\"$TMP_EMAIL\",\"password\":\"Temp@12345\",\"departmentId\":1,\"managerId\":2,\"designation\":\"Intern\",\"role\":\"EMPLOYEE\"}")
assert_eq "duplicate email -> 409" "409" "$status"

status=$(call POST /api/admin/employees "$ADMIN_TOKEN" '{"fullName":"Bad Admin","email":"bad.admin@smartleave.com","password":"Admin@12345","departmentId":1,"designation":"Boss","role":"ADMIN"}')
assert_eq "admin cannot create another ADMIN -> 400" "400" "$status"

status=$(call PUT "/api/admin/employees/$NEW_EMP_ID" "$ADMIN_TOKEN" '{"designation":"Senior Intern","departmentId":3}')
assert_eq "admin updates employee" "200" "$status"
assert_eq "designation updated" "Senior Intern" "$(jqf .designation)"

status=$(call DELETE "/api/admin/employees/$NEW_EMP_ID" "$ADMIN_TOKEN")
assert_eq "admin deactivates employee" "200" "$status"
assert_eq "employee is now inactive" "false" "$(jqf .active)"

status=$(call GET /api/admin/departments "$ADMIN_TOKEN")
assert_eq "department listing" "200" "$status"
DEPT_LIST=$(jqf 'length')
[ "$DEPT_LIST" -ge 3 ] && ok "at least three departments listed ($DEPT_LIST)" || bad "expected >= 3 departments in list, got $DEPT_LIST"

status=$(call POST /api/admin/departments "$ADMIN_TOKEN" "{\"departmentName\":\"$TMP_DEPT\",\"description\":\"QA guild\"}")
assert_eq "admin creates department -> 201" "201" "$status"
NEW_DEPT_ID=$(jqf .id)
status=$(call POST /api/admin/departments "$ADMIN_TOKEN" "{\"departmentName\":\"$TMP_DEPT\",\"description\":\"duplicate\"}")
assert_eq "duplicate department -> 409" "409" "$status"

status=$(call GET "/api/admin/applications?status=PENDING" "$ADMIN_TOKEN")
assert_eq "admin application listing" "200" "$status"

status=$(call GET /api/admin/reports "$ADMIN_TOKEN")
assert_eq "reports endpoint" "200" "$status"
[ "$(jqf '.byDepartment | length')" -ge 3 ] && ok "department report rows" || bad "missing department report rows"
[ "$(jqf '.byMonth | length')" -ge 1 ] && ok "monthly report rows" || bad "missing monthly report rows"

# ------------------------------------------------- cookie based SPA session
COOKIE_JAR=$(mktemp)
status=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
        -d $(auth_body 'employee1@smartleave.com' "$DEMO_EMPLOYEE_PASSWORD") -o "$BODY" -w "%{http_code}")
assert_eq "login sets a session cookie" "200" "$status"
status=$(curl -s -b "$COOKIE_JAR" -o "$BODY" -w "%{http_code}" "$BASE/api/auth/me")
assert_eq "cookie alone authenticates /api/auth/me" "200" "$status"
assert_eq "cookie session identity" "employee1@smartleave.com" "$(jqf .email)"
status=$(curl -s -X POST "$BASE/api/auth/logout" -c "$COOKIE_JAR" -o /dev/null -w "%{http_code}")
assert_eq "logout" "204" "$status"
status=$(curl -s -b "$COOKIE_JAR" -o /dev/null -w "%{http_code}" "$BASE/api/auth/me")
assert_eq "session invalid after logout" "401" "$status"
rm -f "$COOKIE_JAR"

# ------------------------------------------------------------------ result
echo
echo "=============================================="
echo " Passed: $PASS   Failed: $FAIL"
if [ "$FAIL" -gt 0 ]; then
  printf ' Failed assertions:\n'
  for f in "${FAILED[@]}"; do printf '   - %s\n' "$f"; done
  echo "=============================================="
  exit 1
fi
echo " All API smoke tests passed."
echo "=============================================="
rm -f "$BODY"
