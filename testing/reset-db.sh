#!/usr/bin/env bash
# =====================================================================
#  Smart Leave Management System - database reset
#
#  Truncates every application table and re-runs database/seed.sql so
#  the demo data is byte-for-byte identical to a first boot.
#
#  The Spring Boot backend does NOT need to be restarted: the schema is
#  left in place, only the rows are replaced, so a reset takes < 1s.
#
#  Usage:
#     ./testing/reset-db.sh
#
#  Environment (all optional, same names the backend uses):
#     MYSQL_BIN     path to the mysql client   (default: autodetect)
#     MYSQL_SOCKET  unix socket of the server  (default: project .mysql)
#     DB_HOST/DB_PORT/DB_USER/DB_NAME
# =====================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_NAME="${DB_NAME:-smart_leave_management}"
SOCK="${MYSQL_SOCKET:-$ROOT/.mysql/mysql.sock}"

if [[ -z "${MYSQL_BIN:-}" ]]; then
    if command -v mysql >/dev/null 2>&1; then MYSQL_BIN="$(command -v mysql)"
    elif [[ -x /usr/local/mysql/bin/mysql ]]; then MYSQL_BIN=/usr/local/mysql/bin/mysql
    else
        echo "mysql client not found - set MYSQL_BIN" >&2
        exit 1
    fi
fi

# Prefer the project-local socket (the bundled dev server), fall back to TCP.
if [[ -S "$SOCK" ]]; then
    SQL() { "$MYSQL_BIN" -u "$DB_USER" -S "$SOCK" "$@"; }
else
    SQL() { "$MYSQL_BIN" -u "$DB_USER" -h "$DB_HOST" -P "$DB_PORT" "$@"; }
fi

if ! SQL -e "USE $DB_NAME;" >/dev/null 2>&1; then
    echo "Database '$DB_NAME' does not exist - creating it from database/schema.sql"
    SQL < "$ROOT/database/schema.sql"
fi

echo "Resetting $DB_NAME ..."
SQL -D "$DB_NAME" <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE notifications;
TRUNCATE TABLE leave_applications;
TRUNCATE TABLE leave_balances;
TRUNCATE TABLE users;
TRUNCATE TABLE leave_types;
TRUNCATE TABLE departments;
SET FOREIGN_KEY_CHECKS = 1;
SQL

# seed.sql is guarded with WHERE NOT EXISTS, so it re-inserts everything
# that was just truncated and leaves nothing duplicated.
SQL < "$ROOT/database/seed.sql" >/dev/null

SQL -D "$DB_NAME" -t -e "
SELECT (SELECT COUNT(*) FROM users)              AS users,
       (SELECT COUNT(*) FROM leave_applications)  AS leave_requests,
       (SELECT COUNT(*) FROM leave_balances)      AS balances;"
echo "Database reset complete."
