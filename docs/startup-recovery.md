# V0.2.10 startup diagnostic

This interim recovery build guards the initial ledger/finance refresh against
uncaught database or data exceptions. It **does not** assume that every startup
crash has the same cause or modify/delete any existing data.

If startup loading fails, the app remains open and offers:
- a concise exception class and first stack frames that users can copy;
- Retry loading;
- Attempt encrypted export, when the local database is still readable.

If Android exits before this recovery screen, obtain the AndroidRuntime stack
from `adb logcat -d -v time AndroidRuntime:E '*:S'`. In particular, crashes
from class initialization, Compose rendering or native code might occur before
the refresh handler is entered.

Do not uninstall, clear application data or force a database downgrade.
