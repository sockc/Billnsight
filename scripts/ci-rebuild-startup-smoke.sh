#!/usr/bin/env bash
set -euo pipefail
APP="com.sockc.billinsight"
BASE="/tmp/billinsight-v026"

show_crash() {
  echo "=== Android crash buffer ==="
  adb logcat -d -b crash -v time | tail -n 150 || true
}
trap 'result=$?; if [ "$result" -ne 0 ]; then show_crash; fi' EXIT

# Validate visible home screen, not merely that the process remains running.
check_home_screen() {
  local label="$1"
  adb shell uiautomator dump /sdcard/billinsight-ui.xml >/dev/null
  adb exec-out cat /sdcard/billinsight-ui.xml > /tmp/billinsight-ui.xml
  if grep -Eq '账本读取失败|账本初始化失败|SQLiteException|SQLITE_ERROR' /tmp/billinsight-ui.xml; then
    echo "FAIL: $label displayed the ledger error screen"
    grep -o -E '.{0,100}(账本读取失败|账本初始化失败|SQLiteException|SQLITE_ERROR).{0,100}' /tmp/billinsight-ui.xml || true
    return 1
  fi
  if ! grep -Eq 'BillInsight|所选期间总支出' /tmp/billinsight-ui.xml; then
    echo "FAIL: $label did not render the dashboard"
    head -c 1300 /tmp/billinsight-ui.xml || true
    return 1
  fi
  echo "PASS: $label renders dashboard without SQLite errors"
}



gradle --no-daemon :app:assembleDebug
git worktree add --detach "$BASE" v0.2.6
(cd "$BASE" && gradle --no-daemon :app:assembleDebug)
adb install -r "$BASE/app/build/outputs/apk/debug/app-debug.apk"
adb logcat -c
adb shell am start -W -n "$APP/.MainActivity"
sleep 7
adb shell pidof "$APP"
check_home_screen "original V0.2.6"
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
# Stream into the sandbox through dd, avoiding adb remote-shell quoting/redirection.
adb shell -T run-as "$APP" dd "of=/data/user/0/$APP/databases/bill_insight.db" bs=65536 < /tmp/ledger-v12.db

# Update in place: the app ID and debug signing key stay identical.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n "$APP/.MainActivity"
sleep 9
adb shell pidof "$APP"
check_home_screen "upgraded V0.3.4"

# Exercise the actual newly added Finance Center UI. Merely launching the
# process or dashboard would miss Compose/page-specific regressions.
adb shell uiautomator dump /sdcard/billinsight-ui.xml >/dev/null
adb exec-out cat /sdcard/billinsight-ui.xml > /tmp/billinsight-ui.xml
coords=$(python3 - <<'PY'
import re,xml.etree.ElementTree as ET
root=ET.parse("/tmp/billinsight-ui.xml").getroot()
nodes=[n for n in root.iter("node")
       if n.attrib.get("text")=="金融中心" or n.attrib.get("content-desc")=="金融中心"]
assert nodes, "Finance Center bottom-navigation item not visible"
bounds=nodes[-1].attrib["bounds"]
l,t,r,b=map(int,re.findall(r"\d+",bounds))
print((l+r)//2,(t+b)//2)
PY
)
adb shell input tap $coords
sleep 8
adb shell pidof "$APP"
adb shell uiautomator dump /sdcard/billinsight-finance.xml >/dev/null
adb exec-out cat /sdcard/billinsight-finance.xml > /tmp/billinsight-finance.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET,re
root=ET.parse("/tmp/billinsight-finance.xml").getroot()
texts={v for n in root.iter("node")
       for v in (n.attrib.get("text",""),n.attrib.get("content-desc","")) if v}
for needle in ("金融中心","账单还款","资金流转"):
    assert any(needle in t for t in texts), ("Finance Center missing "+needle,texts)
assert any(re.search(r"20\d{2}年",t) for t in texts), ("Year filter missing",texts)
assert not any("账本读取失败" in t or "SQLiteException" in t for t in texts)
print("PASS: upgraded financial center renders year selector and financial sections")
PY
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
print("PASS: V0.2.6 -> V0.3.4 Android boot; migration retained original ledger")
db.close()
PY

# Verify a completely empty/cleared database also launches to the dashboard.
# This only clears data on the disposable CI emulator, never the user's phone.
adb shell pm clear "$APP"
adb logcat -c
adb shell am start -W -n "$APP/.MainActivity"
sleep 7
adb shell pidof "$APP"
check_home_screen "fresh install / cleared app data"
echo "PASS: empty database startup is safe"
