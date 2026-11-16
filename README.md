# AI Khata — Offline-first Android ledger

AI Khata is a shop ledger designed to work on the phone without a web host, account, subscription or continuous internet connection.

## How it works

- Local database: customer records, credit, repayments, balances and reports are stored in a private SQLite database inside the Android app.
- Offline use: creating customers, recording sales/credit, recording payments, viewing balances and reports do not need internet.
- Shopkeeper lock: first launch creates a local PIN. The PIN is stored as a salted PBKDF2 hash, not readable text.
- Optional AI: when the shopkeeper chooses a ledger photo, that image is sent to Google Gemini over the internet. The AI result is only a draft; the shopkeeper reviews and confirms it before entries are saved locally.
- AI key: the Gemini API key is encrypted using Android Keystore before it is saved on the phone.
- No cloud database: there is no Render, Flask server, hosted website or remote database in the Android app.

## Build the Android app

Open GitHub → Actions → AI Khata checks and Android APK. The workflow runs the Python backend regression tests (kept for the web/demo edition), builds the Android app and uploads app-debug.apk as ai-khata-debug-apk.

The Android project is in android/. The app supports Android 6.0+ and is suitable for Android 10. No URL or hosting setup is required.

## Local data and backup

The SQLite database lives in the app's private internal storage. Other ordinary apps cannot directly read it. Uninstalling AI Khata or clearing its app storage deletes the local records. Keep a regular backup before changing phones or uninstalling. A backup/export and restore screen is a recommended next feature.

The app currently stores the shop name, customers, transaction history and PIN hash locally. It does not automatically sync between multiple phones. If the phone is lost or damaged, data is not recoverable unless a backup has been made.

## Optional AI setup

Add a Gemini API key in Settings & AI key. Without a key, the app remains a complete manual ledger and works offline. Internet is only used when the user deliberately runs AI photo extraction. The selected image and prompt are sent to Google Gemini; ledger records remain on the phone. AI availability, model names, free quotas and pricing are controlled by Google and can change.

## Web/classroom edition

The root app.py, Flask templates and Python tests are retained as a separate web demonstration. The Android APK does not use that backend. The mobile app's ledger database is native Android SQLite.

## Presentation explanation

- Kotlin: the Android app screens and app logic.
- SQLite: stores the shop's customers and ledger entries locally.
- PBKDF2 + Android Keystore: protects the shop PIN and locally saved AI key.
- Gemini API: optional online handwriting recognition, only when the user chooses to scan.
