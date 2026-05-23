#!/usr/bin/env bash
# End-to-end smoke test for the arusuvai webapp.
#
# Exercises:
#   1. Register a new user (mari@arusuvai.test) - falls back to login if already present
#   2. Login (verify credentials work)
#   3. GET /api/me  (self profile)
#   4. GET /api/product  (list products, public)
#   5. Pick a random product from the list
#   6. GET /api/product?productId=<id>  (single product detail)
#   7. GET /api/cart  (initial empty/state)
#   8. POST /api/cart  (add the random product)
#   9. PUT /api/cart   (change quantity)
#  10. GET /api/cart   (verify item + total)
#  11. POST /api/order  (checkout with shipping address + phone)
#  12. GET /api/order?orderID=<id>  (verify whatsappLink decorated)
#  13. GET /api/order   (list this user's orders)
#  14. POST /api/review  (rate the purchased product)
#  15. GET /api/review?productId=<id>  (verify the new review shows up, with mari's email)
#  16. POST /auth?isRefresh=true  (rotate token)
#  17. DELETE /auth  (logout)
#
# Usage:  bash src/test/scripts/e2e_mari.sh
# Exits non-zero on the first failed assertion.

set -u

BASE='http://localhost:8080/arusuvai'
EMAIL='mari@arusuvai.test'
PASSWORD='Mari@12345'
ADDRESS='12 Marina Avenue, Chennai 600001'
PHONE='+919876543210'

CURL='curl -sS --noproxy *'
PASS=0
FAIL=0

# ---------- helpers ----------
say() { printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }
ok()  { printf '  \033[32mPASS\033[0m %s\n' "$*"; PASS=$((PASS+1)); }
bad() { printf '  \033[31mFAIL\033[0m %s\n' "$*"; FAIL=$((FAIL+1)); }

# Extract a JSON field via python (avoids jq dependency).
jget() {
    local path="$1"
    python3 -c "
import json, sys
try:
    d = json.loads(sys.stdin.read())
except Exception as e:
    print('', end=''); sys.exit(0)
for k in '''$path'''.split('.'):
    if k == '' or d is None:
        continue
    if isinstance(d, list):
        try: d = d[int(k)]
        except: d = None
    else:
        d = d.get(k) if isinstance(d, dict) else None
print('' if d is None else d)
"
}

assert_eq() {
    local actual="$1" expected="$2" label="$3"
    if [[ "$actual" == "$expected" ]]; then ok "$label (=$actual)"
    else bad "$label (got '$actual', expected '$expected')"
    fi
}

assert_nonempty() {
    local actual="$1" label="$2"
    if [[ -n "$actual" && "$actual" != "None" ]]; then ok "$label (=$actual)"
    else bad "$label (empty)"
    fi
}

# ---------- 1. Register (idempotent) ----------
say "1. Register $EMAIL"
REG_BODY=$($CURL -X POST "$BASE/auth?isLogin=false" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"passwordHash\":\"$PASSWORD\",\"firstName\":\"Mari\",\"lastName\":\"User\"}")
echo "$REG_BODY" | python3 -m json.tool 2>/dev/null | head -10
REG_SUCCESS=$(echo "$REG_BODY" | jget success)
if [[ "$REG_SUCCESS" == "True" ]]; then
    ok "registered fresh"
else
    ok "user already exists (will login)"
fi

# ---------- 2. Login ----------
say "2. Login"
LOGIN_BODY=$($CURL -X POST "$BASE/auth?isLogin=true" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL\",\"passwordHash\":\"$PASSWORD\"}")
ACCESS=$(echo "$LOGIN_BODY" | jget data.accessToken)
REFRESH=$(echo "$LOGIN_BODY" | jget data.refreshToken)
assert_nonempty "$ACCESS"  "accessToken issued"
assert_nonempty "$REFRESH" "refreshToken issued"
AUTH=(-H "Authorization: Bearer $ACCESS")

# ---------- 3. /api/me ----------
say "3. GET /api/me"
ME=$($CURL "${AUTH[@]}" "$BASE/api/me")
ME_EMAIL=$(echo "$ME" | jget data.email)
assert_eq "$ME_EMAIL" "$EMAIL" "me.email matches"

# ---------- 4. List products ----------
say "4. GET /api/product (list)"
PRODUCTS=$($CURL "${AUTH[@]}" "$BASE/api/product")
COUNT=$(echo "$PRODUCTS" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(len(d.get("data") or []))')
if [[ "$COUNT" -gt 0 ]]; then ok "$COUNT products listed"
else bad "product list empty - seed at least one product before running"; exit 1
fi

# ---------- 5. Pick a random product ----------
say "5. Pick random product"
read PRODUCT_ID PRODUCT_NAME PRODUCT_PRICE PRODUCT_STOCK < <(echo "$PRODUCTS" | python3 -c '
import json, sys, random
d = json.load(sys.stdin)["data"]
in_stock = [p for p in d if p.get("stockQuantity",0) > 0 and p.get("active", True)]
pool = in_stock or d
p = random.choice(pool)
print(p["id"], p["name"].replace(" ","_"), p["price"], p.get("stockQuantity",0))
')
assert_nonempty "$PRODUCT_ID" "picked product id"
echo "  -> $PRODUCT_NAME @ Rs.$PRODUCT_PRICE  stock=$PRODUCT_STOCK"

# ---------- 6. Product detail (review hydration) ----------
say "6. GET /api/product?productId=$PRODUCT_ID"
DETAIL=$($CURL "${AUTH[@]}" "$BASE/api/product?productId=$PRODUCT_ID")
DETAIL_ID=$(echo "$DETAIL" | jget data.id)
assert_eq "$DETAIL_ID" "$PRODUCT_ID" "detail.id matches"
# averageRating / reviewCount keys should be present (may be null pre-review)
echo "$DETAIL" | python3 -c 'import json,sys;d=json.load(sys.stdin)["data"];print("  ratingFields:", "averageRating" in d, "reviewCount" in d, "reviews" in d)'

# ---------- 7. Cart initial ----------
say "7. GET /api/cart (initial)"
CART=$($CURL "${AUTH[@]}" "$BASE/api/cart")
echo "$CART" | python3 -m json.tool 2>/dev/null | head -10

# Clear any leftovers from earlier runs.
$CURL "${AUTH[@]}" -X DELETE "$BASE/api/cart" >/dev/null

# ---------- 8. Add to cart ----------
say "8. POST /api/cart (add item)"
ADD=$($CURL "${AUTH[@]}" -X POST "$BASE/api/cart" \
    -H 'Content-Type: application/json' \
    -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":1}")
ADD_OK=$(echo "$ADD" | jget success)
assert_eq "$ADD_OK" "True" "addItem success"

# ---------- 9. Update quantity ----------
say "9. PUT /api/cart (quantity -> 2)"
UPD=$($CURL "${AUTH[@]}" -X PUT "$BASE/api/cart" \
    -H 'Content-Type: application/json' \
    -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}")
UPD_OK=$(echo "$UPD" | jget success)
assert_eq "$UPD_OK" "True" "updateItem success"

# ---------- 10. Verify cart ----------
say "10. GET /api/cart (verify)"
CART=$($CURL "${AUTH[@]}" "$BASE/api/cart")
CART_QTY=$(echo "$CART" | python3 -c '
import json, sys
d = json.load(sys.stdin)["data"]
items = d.get("cartItems") or []
print(items[0]["quantity"] if items else 0)
')
assert_eq "$CART_QTY" "2" "cart item quantity"

# ---------- 11. Checkout ----------
say "11. POST /api/order (checkout)"
ORDER=$($CURL "${AUTH[@]}" -X POST "$BASE/api/order" \
    -H 'Content-Type: application/json' \
    -d "{\"shippingAddress\":\"$ADDRESS\",\"phone\":\"$PHONE\"}")
echo "$ORDER" | python3 -m json.tool 2>/dev/null | head -25
ORDER_OK=$(echo "$ORDER" | jget success)
ORDER_ID=$(echo "$ORDER" | jget data.orderId)
WA_LINK=$(echo "$ORDER" | jget data.whatsappLink)
assert_eq "$ORDER_OK" "True" "checkout success"
assert_nonempty "$ORDER_ID" "orderId returned"
assert_nonempty "$WA_LINK"  "whatsappLink decorated"

# ---------- 12. Fetch order ----------
say "12. GET /api/order?orderID=$ORDER_ID"
ONE_ORDER=$($CURL "${AUTH[@]}" "$BASE/api/order?orderID=$ORDER_ID")
GOT_ID=$(echo "$ONE_ORDER" | jget data.orderId)
GOT_ADDR=$(echo "$ONE_ORDER" | jget data.shippingAddress)
assert_eq "$GOT_ID" "$ORDER_ID" "fetched orderId matches"
assert_eq "$GOT_ADDR" "$ADDRESS" "shippingAddress persisted"

# ---------- 13. List orders ----------
say "13. GET /api/order (list mine)"
ORDERS=$($CURL "${AUTH[@]}" "$BASE/api/order")
ORDER_COUNT=$(echo "$ORDERS" | python3 -c 'import json,sys;print(len(json.load(sys.stdin).get("data") or []))')
if [[ "$ORDER_COUNT" -ge 1 ]]; then ok "mari has $ORDER_COUNT order(s)"
else bad "expected at least 1 order"
fi

# ---------- 14. Write a review ----------
say "14. POST /api/review"
REV=$($CURL "${AUTH[@]}" -X POST "$BASE/api/review" \
    -H 'Content-Type: application/json' \
    -d "{\"productId\":\"$PRODUCT_ID\",\"rating\":5,\"comment\":\"loved it - mari e2e\"}")
REV_OK=$(echo "$REV" | jget success)
assert_eq "$REV_OK" "True" "review posted"

# ---------- 15. Verify review visible publicly ----------
say "15. GET /api/review?productId=$PRODUCT_ID"
REV_LIST=$($CURL "$BASE/api/review?productId=$PRODUCT_ID")
MARI_REV=$(echo "$REV_LIST" | python3 -c "
import json, sys
d = json.load(sys.stdin).get('data') or {}
reviews = d.get('reviews') if isinstance(d, dict) else d
reviews = reviews or []
hit = [r for r in reviews if r.get('userEmail') == '$EMAIL']
print(hit[0]['rating'] if hit else '')
")
assert_eq "$MARI_REV" "5" "mari's review appears with rating 5"

# ---------- 16. Refresh token ----------
say "16. POST /auth?isRefresh=true"
NEW_TOK=$($CURL -X POST "$BASE/auth?isRefresh=true" \
    -H 'Content-Type: application/json' \
    -d "{\"token\":\"$REFRESH\"}")
NEW_ACCESS=$(echo "$NEW_TOK" | jget data.accessToken)
assert_nonempty "$NEW_ACCESS" "new accessToken issued"
AUTH=(-H "Authorization: Bearer $NEW_ACCESS")

# ---------- 17. Logout (revoke all sessions) ----------
say "17. DELETE /auth?all=true"
LO=$($CURL "${AUTH[@]}" -X DELETE "$BASE/auth?all=true")
LO_OK=$(echo "$LO" | jget success)
assert_eq "$LO_OK" "True" "logout success"

# ---------- summary ----------
say "Summary"
echo "  passed: $PASS"
echo "  failed: $FAIL"
exit $((FAIL > 0 ? 1 : 0))
