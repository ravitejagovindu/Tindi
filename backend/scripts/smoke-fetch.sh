#!/usr/bin/env bash
# Smoke test for POST /api/v1/source/fetch against a running backend (real YouTube!).
# Usage: backend/scripts/smoke-fetch.sh <youtube-url> [base-url]
# Skips itself unless a real key is set in backend/config/local.properties. Never prints the key.
set -u
URL="${1:?usage: smoke-fetch.sh <youtube-url> [base-url]}"
BASE="${2:-http://localhost:7000/api/v1}"
CFG="$(dirname "$0")/../config/local.properties"

if ! grep -Eq '^tindi\.youtube-api-key=[^[:space:]]+' "$CFG" 2>/dev/null || grep -Eqi '^tindi\.youtube-api-key=PASTE' "$CFG"; then
  echo "SKIP: tindi.youtube-api-key is not set in backend/config/local.properties"; exit 0
fi

EMAIL="smoke$(date +%s)@example.com"
TOKEN=$(curl -s -m 10 -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"secret123\"}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "FAIL: signup"; exit 1; }
A="Authorization: Bearer $TOKEN"

echo "-- no token (expect 401)";  curl -s -m 10 -o /dev/null -w '%{http_code}\n' -X POST "$BASE/source/fetch" -H 'Content-Type: application/json' -d "{\"url\":\"$URL\"}"
echo "-- instagram (expect 400)"; curl -s -m 10 -w '  [%{http_code}]\n' -X POST "$BASE/source/fetch" -H "$A" -H 'Content-Type: application/json' -d '{"url":"https://www.instagram.com/reel/abc/"}'
echo "-- real fetch (expect 200)"
curl -s -m 20 -w '\n[%{http_code}]\n' -X POST "$BASE/source/fetch" -H "$A" -H 'Content-Type: application/json' -d "{\"url\":\"$URL\"}" | cut -c1-1500
curl -s -m 10 -o /dev/null -X POST "$BASE/auth/logout" -H "$A"
