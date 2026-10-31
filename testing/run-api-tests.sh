#!/usr/bin/env bash
# =====================================================================
#  Smart Leave Management System - full API test run
#
#  1. verifies the backend is reachable,
#  2. resets the database to the pristine demo seed (reset-db.sh),
#  3. runs the Postman collection with Newman,
#  4. exits non-zero if a single assertion fails.
#
#  Usage:
#     ./testing/run-api-tests.sh                 # reset + full collection
#     BASE_URL=http://host:8080 ./testing/run-api-tests.sh
#     SKIP_RESET=1 ./testing/run-api-tests.sh    # run against current data
#
#  Prerequisites: backend running, Node.js (npx pulls newman), mysql client.
# =====================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
COLLECTION="$ROOT/testing/Smart-Leave-Management.postman_collection.json"
ENVIRONMENT="$ROOT/testing/Smart-Leave-Management.postman_environment.json"
LOG="${NEWMAN_LOG:-/tmp/smart-leave-newman.log}"

echo "==> Checking backend at $BASE_URL"
up=false
for _ in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$BASE_URL/actuator/health" 2>/dev/null || true)
    if [ "$code" = "200" ]; then up=true; break; fi
    sleep 1
done
if [ "$up" != "true" ]; then
    echo "Backend is not responding on $BASE_URL." >&2
    echo "Start it first:  cd backend && mvn spring-boot:run" >&2
    exit 1
fi
echo "    backend is UP"

if [ "${SKIP_RESET:-0}" != "1" ]; then
    echo "==> Resetting demo data"
    if ! bash "$ROOT/testing/reset-db.sh"; then
        echo "Database reset failed - set SKIP_RESET=1 to run against the current data." >&2
        exit 1
    fi
fi

# --env-var overrides the base_url stored in the environment file.
echo "==> Running the Postman collection with Newman"
npx -y newman run "$COLLECTION" -e "$ENVIRONMENT" --env-var "base_url=$BASE_URL" --color off 2>&1 | tee "$LOG"
status=${PIPESTATUS[0]}

echo
if [ "$status" -eq 0 ]; then
    echo "API test run PASSED (log: $LOG)"
else
    echo "API test run FAILED (log: $LOG)"
    grep -E "assertions │" "$LOG" || true
fi
exit "$status"
