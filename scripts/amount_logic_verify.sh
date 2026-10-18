#!/usr/bin/env bash
# Amount-logic verification for loyalty redemption after BigDecimal fix.
# Verifies: quote discount/payable == points * pointValue, exact to the paisa,
# including a FRACTIONAL point value (2.50) to prove no double drift.
set -u
BASE_URL="${BASE_URL:-http://localhost:8080/arusuvai}"
ADMIN_EMAIL="${ADMIN_EMAIL:?set ADMIN_EMAIL}"
ADMIN_PASS="${ADMIN_PASS:?set ADMIN_PASS}"
USER_EMAIL="${USER_EMAIL:?set USER_EMAIL}"
USER_PASS="${USER_PASS:?set USER_PASS}"

pass=0; fail=0
ok(){ echo "    PASS $1"; pass=$((pass+1)); }
no(){ echo "    FAIL $1"; fail=$((fail+1)); }

login(){ # email pass -> refresh token
  curl -s -X POST "$BASE_URL/auth?isLogin=true" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"passwordHash\":\"$2\"}" | jq -r '.data.refreshToken // empty'
}
access(){ # refresh -> access
  curl -s -X POST "$BASE_URL/auth?isRefresh=true" -H 'Content-Type: application/json' \
    -d "{\"token\":\"$1\"}" | jq -r '.data.accessToken // empty'
}

echo "==> auth"
UREF=$(login "$USER_EMAIL" "$USER_PASS");  UT=$(access "$UREF")
AREF=$(login "$ADMIN_EMAIL" "$ADMIN_PASS"); AT=$(access "$AREF")
[ -n "$UT" ] && ok "customer access token" || { no "customer login"; exit 1; }
[ -n "$AT" ] && ok "admin access token" || { no "admin login"; exit 1; }

set_pv(){ # value
  curl -s -X PUT "$BASE_URL/api/admin/loyalty/redemptions/settings" \
    -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
    -d "{\"pointValueInr\":$1}" | jq -r '.data.pointValueInr'
}
quote(){ # points total -> json
  curl -s "$BASE_URL/api/loyalty/redeem/quote?points=$1&total=$2" -H "Authorization: Bearer $UT"
}

ORIG=$(curl -s "$BASE_URL/api/admin/loyalty/redemptions/settings" -H "Authorization: Bearer $AT" | jq -r '.data.pointValueInr')
echo "==> original pointValueInr=$ORIG"

BAL=$(curl -s "$BASE_URL/api/loyalty/card" -H "Authorization: Bearer $UT" | jq -r '.data.card.points // 0')
echo "==> customer balance=$BAL points"

# num-eq helper (tolerant to 10 vs 10.00)
neq(){ awk -v a="$1" -v b="$2" 'BEGIN{exit !(a+0==b+0)}'; }

run_matrix(){ # pointValue
  local PV="$1"
  echo "==> matrix @ pointValue=$PV"
  set_pv "$PV" >/dev/null
  # cases: requested , orderTotal
  for c in "5 500" "100 500" "3 25" "7 1000" "2 15" "0 500"; do
    set -- $c; local REQ=$1 TOT=$2
    local J; J=$(quote "$REQ" "$TOT")
    local PTU DISC PAY
    PTU=$(echo "$J" | jq -r '.data.pointsToUse')
    DISC=$(echo "$J" | jq -r '.data.discount')
    PAY=$(echo "$J" | jq -r '.data.payable')
    # expected
    local EXP_DISC EXP_PAY
    EXP_DISC=$(awk -v p="$PTU" -v v="$PV" 'BEGIN{printf "%.2f", p*v}')
    EXP_PAY=$(awk -v t="$TOT" -v d="$EXP_DISC" 'BEGIN{x=t-d; if(x<0)x=0; printf "%.2f", x}')
    if neq "$DISC" "$EXP_DISC" && neq "$PAY" "$EXP_PAY"; then
      ok "req=$REQ total=$TOT -> ptu=$PTU discount=$DISC payable=$PAY (expected d=$EXP_DISC p=$EXP_PAY)"
    else
      no "req=$REQ total=$TOT -> ptu=$PTU discount=$DISC payable=$PAY (expected d=$EXP_DISC p=$EXP_PAY)"
    fi
  done
}

run_matrix 10
run_matrix 2.5   # fractional — the case the BigDecimal fix protects

echo "==> restore pointValueInr=$ORIG"
set_pv "$ORIG" >/dev/null

echo "========================================"
echo "  Passed: $pass   Failed: $fail"
echo "========================================"
[ "$fail" -eq 0 ]
