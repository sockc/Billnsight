# V0.3.0: stable V0.2.6 rebuild
- Git base tree is the proven V0.2.6 tag, with *only* merchant-recognition and installment files transplanted from V0.2.8.
- Database version **13** is retained. Do not install the older V0.2.6 APK over a version-13 ledger; Android and SQLite cannot safely downgrade while preserving your data.
- V0.2.6 startup/dashboard remain the primary path. Installment-history synchronization and the large finance plan query run on **explicit entry to the Finance Installments tab** only, with handled errors.
- The rebuilt UI catches initial ledger/database errors and allows copying diagnostics without deleting the database.
- No existing bill, card installment, alias, merchant rule, or finance linkage is wiped.
- If a device still closes **before** the diagnostic screen, gather AndroidRuntime/Crash logcat; Android native crashes and Compose failures are outside the database-read guard.
