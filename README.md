# AI Khata

A simple digital khata for shopkeepers. Keep customer accounts, credit sales and payments together in one app.

## Shopkeeper features

- Create a shop and protect it with a private PIN.
- Add customers with their names and phone numbers.
- Open an individual customer khata and see the running balance.
- Record credit sales and payments with dates and descriptions.
- Edit customer details and correct transaction entries.
- Delete an individual entry or permanently delete a customer's khata after confirmation.
- Search customers by name or phone, sort by outstanding balance, and filter accounts with money due.
- View all-time totals and month-by-month reports.
- Share a customer's account statement.
- Export a backup and restore it when needed.
- Review shareable all-time and monthly reports, including customers with the highest outstanding balances.
- Optionally read a photo of a handwritten ledger with AI. Add multiple Gemini keys; the app automatically retries alternate keys and supported Flash models. Check suggested entries before saving them.

## Offline use and AI photo reading

Customer accounts, transaction entries, balances, reports, search, edits, deletions and backups are handled on the phone in its private SQLite database. These ledger features do not need an online database.

AI handwriting recognition needs an internet connection. The app first sends a selected photo to its configured AI Khata backend; the backend must be deployed and have a Gemini API key configured. The app also supports optional personal Gemini keys as an advanced fallback. Personal keys are encrypted with Android Keystore. If a model or key returns an error, the app tries other configured keys and compatible Gemini Flash models. A scan photo is sent only after the shopkeeper deliberately chooses it. The shopkeeper must review and confirm the suggested entries before they are added.

## Privacy and data

A shop PIN is stored as a salted PBKDF2 hash. The optional AI access key is encrypted with Android Keystore. Ledger data is stored in the app's private SQLite database.

Export a backup before uninstalling the app, clearing its storage or changing phones. Uninstalling or clearing app storage removes the records on that phone. Backups are JSON files and should be kept somewhere private. The app does not automatically sync between multiple phones.

## Android app

The native Android project is in `android/`. It supports Android 6.0 and newer, including Android 10. The primary dashboard, customer list, customer khata/transaction history, reports and shop settings use Jetpack Compose. The latest interface refresh adds subtle entrance motion, animated status indicators and totals, gradient metric panels, responsive progress visuals and smoother screen transitions. No server, website URL or hosting account is needed for ordinary ledger use.

To build the APK, open GitHub → Actions → **AI Khata checks and Android APK**. The workflow runs the Python regression tests and builds the Android debug APK. The APK is uploaded as the `ai-khata-debug-apk` artifact.

## University project notes

- Kotlin is used for the Android app screens and app behaviour.
- SQLite stores the shop's customer and ledger records.
- PBKDF2 protects the PIN; Android Keystore protects the optional AI key.
- The Gemini API is used only for optional handwritten-ledger photo reading, with multi-key and multi-model fallback.

The Python/Flask files in the repository are retained as a separate classroom/web demonstration. The Android APK does not depend on that backend.


## Part 5 — Inventory, expenses and profitability (v2.7.0)

The Android app now includes an offline business workspace with three sections:

- **Inventory:** create and edit products, track SKU/unit, purchase and selling prices, opening/current stock, low-stock thresholds, manual stock corrections, and record a customer sale. A recorded inventory sale creates a customer credit entry and deducts stock in one SQLite transaction. Failed or over-stock sales do not create a ledger entry.
- **Expenses:** record, edit and delete dated shop expenses by category, with notes.
- **Profit & loss:** monthly tracked product revenue, cost of goods sold captured at the time of each sale, gross profit, recorded operating expenses, estimated net profit, current inventory value and low-stock count.

The calculation deliberately distinguishes cash collection from revenue. A customer payment settles an earlier balance and is not counted as a second sale. Older/manual credit entries without a saved product cost are excluded from this tracked-product profit estimate, so the estimate does not pretend to know their cost.

The local database upgrades to schema version 3 without discarding existing customer or transaction records. Password-protected backups now include products, expenses and product-sale cost snapshots; older version-1 backups remain restorable (they do not contain inventory or expense data). Deleting a product-sale entry or deleting a customer restores the quantities from that customer's product sales to inventory. Transaction edits also validate the entered date.
