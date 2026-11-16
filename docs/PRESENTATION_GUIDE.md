# AI Khata — University Project Guide

## Problem statement

Small shops often record customer credit and repayments in handwritten notebooks. Searching old pages, calculating balances, and preparing monthly summaries takes time. Paper records can be damaged or lost.

## Proposed solution

AI Khata is an offline-first Android shop ledger. The shopkeeper creates a private PIN, adds customers, records credit and repayments, views running balances and prepares monthly summaries. The Android app stores all ledger data in a local SQLite database on the phone. It does not require a hosted website or remote database.

An optional AI feature lets the shopkeeper photograph or select a handwritten ledger page. Only when the shopkeeper starts a scan does the app send that selected image to Gemini over the internet. The AI suggests structured entries; the shopkeeper reviews and confirms them before they are saved to the phone.

## Android app architecture

1. **Kotlin** builds the Android screens and handles app behaviour.
2. **SQLite** stores the shop name, customers and transactions in the app's private local storage.
3. **PBKDF2 password hashing** stores a salted hash of the shop PIN instead of the readable PIN.
4. **Android Keystore** encrypts the optional Gemini API key before it is stored in app preferences.
5. **Gemini API** is called only for an optional image scan. Ordinary ledger use works offline.
6. **JSON backup and restore** lets the shopkeeper export a local backup file and restore it later.

The repository also retains a separate Flask/browser classroom edition in the root directory. The Android APK does not call or depend on that server.

## Demonstration sequence

1. Launch AI Khata. On first use, create the shop name and a six-digit PIN.
2. Turn off internet or enable airplane mode. Show that the app still opens and works.
3. Add a customer.
4. Record a credit/sale for that customer.
5. Record a partial payment and show the updated outstanding balance.
6. Open the customer ledger and explain its transaction history.
7. Open Reports and show all-time and current-month totals.
8. Open Settings and demonstrate the local PIN change.
9. Open Settings → Local database backup and export a backup file.
10. If a Gemini key is configured and internet is available, take or select a sample ledger photo. Review the AI suggestions and explicitly confirm before saving.
11. Lock the app and demonstrate that the PIN is required again.

## How the balance is calculated

A **credit** entry means the shop supplied goods or services and the customer owes the shop. A **payment** entry means the customer paid money back.

Outstanding balance = total credit − total payments.

Example: a customer receives goods worth Rs. 1,000 and pays Rs. 300. Their outstanding balance is Rs. 700.

## Data privacy and limitations

- Customer names, phone numbers, sales, payments and balances are stored in the app's private SQLite database on the phone.
- The app does not automatically upload or synchronize ledger data.
- The Android app has internet permission because the optional Gemini photo-reading feature needs it. Manual ledger screens do not make network requests.
- When AI scanning is used, the selected image and prompt are sent to Google Gemini. Do not scan sensitive records unless the shopkeeper accepts that transfer.
- The Gemini API key is encrypted using Android Keystore. It is not included in the APK source or sent to our own server.
- The SQLite database is protected by Android's app-private storage sandbox, but it is not separately encrypted with SQLCipher. Rooted-device access or advanced forensic access is outside this protection.
- Uninstalling the app or clearing its storage deletes the local database. Export regular backups before changing phones or uninstalling.
- A backup file contains shop name, customers and transactions, but not the PIN hash or AI key. The existing local PIN remains in place after a restore.
- There is no multi-phone synchronization in this offline version.

## Testing checklist

- First launch creates a local shop profile and rejects a short or mismatched PIN.
- A wrong PIN does not unlock the app.
- The shop can be locked and unlocked.
- Customers can be added and searched.
- Credit increases the outstanding balance.
- Payment decreases the outstanding balance.
- Customer transaction history displays saved entries.
- Reports calculate totals from local records.
- Records remain available after closing and reopening the app.
- Backup export creates a JSON file.
- Restore replaces ledger records after explicit confirmation.
- AI photo extraction is optional and requires internet only when manually started.
- AI suggestions are not saved until the shopkeeper confirms them.

## Technologies in simple language

- **Kotlin:** the language used to build the Android application.
- **SQLite:** a small database that lives on the phone.
- **Android Keystore:** Android's secure storage for cryptographic keys.
- **Gemini API:** an optional online AI service used to interpret a photo of handwriting.
- **JSON:** a structured format used for AI results and local backup files.

## Team details

Student names: To be added.

Registration numbers: To be added.

University / department: To be added.

Supervisor: To be added.
