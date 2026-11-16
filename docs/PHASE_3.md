# AI Khata — Phase 3

## Scope delivered in this phase branch

- Customer khata detail and transaction history are presented in Jetpack Compose.
- Sales/credit and payment entries retain the existing add, edit and delete dialogs and SQLite handlers.
- Customer editing, duplicate-account merging, statement sharing and khata deletion remain available from the detail screen.
- Monthly and all-time reports show sales, payments, outstanding balances, transaction counts and customers with amounts due.
- Reports can be filtered by month and shared as text through Android's share sheet.
- Shop settings are presented in Compose and retain shop-name updates, PIN changes, optional fingerprint unlock, encrypted personal Gemini-key management, backup export and restore.
- Customer-detail and report data are loaded through the repository on an IO dispatcher.
- A database regression test covers monthly transaction counting and monthly totals.
- Android app version: 2.6.0 (versionCode 8).

## Data compatibility

This phase does not change the SQLite schema, existing customer/transaction identifiers, PIN hash format, encrypted AI-key storage, or JSON backup format. Existing local records are intended to remain available after updating the app.

## Acceptance checks

1. Run the GitHub Actions Python regression tests.
2. Run Android unit tests and assemble the debug APK.
3. Install over the previous AI Khata build on Android 10 without clearing app storage.
4. Confirm PIN unlock, customer search, customer detail, credit/payment creation, edit/delete, merge, reports, settings, AI key save/remove and backup/restore.
5. Confirm the back button returns through Compose destinations and cannot return to a stale ledger screen after a lock.

The automated build does not replace the physical-device acceptance checks in step 3–5.
