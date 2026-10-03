#!/usr/bin/env bash
set -euo pipefail
APP="com.sockc.billinsight"
BASE="/tmp/billinsight-v026"

show_crash() {
  echo "=== Android crash buffer ==="
  adb logcat -d -b crash -v time | tail -n 150 || true
}
trap 'result=$?; if [ "$result" -ne 0 ]; then show_crash; fi' EXIT

gradle --no-daemon :app:assembleDebug
git worktree add --detach "$BASE" v0.2.6
(cd "$BASE" && gradle --no-daemon :app:assembleDebug)
adb install -r "$BASE/app/build/outputs/apk/debug/app-debug.apk"
adb logcat -c
adb shell am start -W -n "$APP/.MainActivity"
sleep 7
adb shell pidof "$APP"
adb shell am force-stop "$APP"

# Capture the real V0.2.6 SQLite schema from its own initial startup.
adb exec-out run-as "$APP" cat databases/bill_insight.db > /tmp/ledger-v12.db
if adb shell run-as "$APP" test -s databases/bill_insight.db-wal; then
  adb exec-out run-as "$APP" cat databases/bill_insight.db-wal > /tmp/ledger-v12.db-wal
fi
python3 - <<'PY'
import sqlite3
db=sqlite3.connect("/tmp/ledger-v12.db")
version=db.execute("PRAGMA user_version").fetchone()[0]
assert version==12, f"Expected V0.2.6 schema v12, got {version}"
db.execute("INSERT INTO transactions (platform,occurred_at,counterparty,description,direction_text,trade_type,amount_cent,flow_type,category,nature_modified,payment_method,transaction_id,merchant_order_id,source_file,fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
("WECHAT", 1780000000000, "拾贰便利店", "二维码收款", "支出", "商户消费", 1200, "EXPENSE", "其他", 0, "零钱", "ci-smoke", "ci-smoke", "微信账单.csv", "ci-smoke-fingerprint"))
db.commit()
db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
db.close()
print("Seeded real V0.2.6 schema with a sample merchant bill")
PY
adb shell run-as "$APP" rm -f databases/bill_insight.db-wal databases/bill_insight.db-shm
adb shell -T run-as "$APP" sh -c 'cat > databases/bill_insight.db' < /tmp/ledger-v12.db

# Update in place: the app ID and debug signing key stay identical.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n "$APP/.MainActivity"
sleep 9
adb shell pidof "$APP"
adb shell am force-stop "$APP"
adb exec-out run-as "$APP" cat databases/bill_insight.db > /tmp/ledger-v13.db
if adb shell run-as "$APP" test -s databases/bill_insight.db-wal; then
  adb exec-out run-as "$APP" cat databases/bill_insight.db-wal > /tmp/ledger-v13.db-wal
fi
python3 - <<'PY'
import sqlite3
db=sqlite3.connect("/tmp/ledger-v13.db")
version=db.execute("PRAGMA user_version").fetchone()[0]
assert version==13, f"Expected upgraded schema v13, got {version}"
row=db.execute("SELECT counterparty,amount_cent,category FROM transactions WHERE fingerprint='ci-smoke-fingerprint'").fetchone()
assert row==("拾贰便利店",1200,"其他"), f"Original imported ledger changed or missing: {row}"
tables={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
assert {"finance_installment_plans","finance_installment_links"}<=tables, tables
print("PASS: V0.2.6 -> V0.3.0 Android boot; migration retained original ledger")
db.close()
PY
