# AI Khata — University Project Guide

## Problem
Small shops often record customer credit and repayments in handwritten notebooks. Searching old pages, calculating balances, and preparing monthly summaries can take time. Handwriting may also be difficult to read later.

## Proposed solution
AI Khata provides a private digital ledger. A shopkeeper creates an account, adds customers, records credit and repayments, checks each customer's running balance, and reviews monthly totals. An optional image-reading feature sends a ledger photo to a configured vision AI provider and returns suggested entries for the shopkeeper to review before saving.

## Architecture

1. **HTML** defines the content and forms shown in the browser.
2. **CSS** controls layout, colours, spacing, typography and responsive design.
3. **JavaScript** handles image selection, AI extraction requests, editable review rows and chart rendering.
4. **Python + Flask** handles routes, login, validation, AI-provider requests and application logic.
5. **SQLite** stores the owner account, customers and transactions during development.
6. **Gemini / OpenRouter API** optionally reads an uploaded ledger image and returns structured JSON. The key stays on the server.
7. **Kotlin + Android WebView** packages the hosted web application in an Android installable app.

## Demonstration sequence

1. Open AI Khata and create the shopkeeper account.
2. Show that the dashboard is inaccessible without unlocking the account.
3. Add a customer and record a credit entry.
4. Record a partial payment and show the updated balance.
5. Open the customer ledger to explain the transaction history.
6. Open Reports and show monthly totals.
7. When a vision API key is configured, select or photograph a sample ledger page, run extraction, correct uncertain fields, and explicitly confirm the entries.
8. Press Lock and demonstrate that private pages require the shopkeeper's credentials again.

## How to explain the balance

A **credit** entry means the shop supplied goods or services and the customer owes the shop. A **payment** entry means the customer paid money back. The outstanding balance is the sum of credit entries minus the sum of payments.

## Security explanation

There is one shopkeeper owner account and no public registration after initial setup. The PIN/password is hashed with Werkzeug's password-hashing utility. Protected routes require an authenticated session. Write requests include CSRF tokens, and repeated incorrect sign-in attempts are temporarily throttled. This is application-level access control, not a replacement for a phone screen lock or production security review.

## Testing checklist

- First-run setup accepts a valid PIN/password and rejects a short one.
- Login rejects an incorrect PIN/password.
- Five failed attempts trigger a temporary lock.
- Dashboard and customer pages redirect an unauthenticated user to login.
- Adding a customer works and the customer appears in the list.
- Credit increases the outstanding balance.
- Payment decreases the outstanding balance.
- Deleting a transaction recalculates the balance.
- Reports show monthly credit, payment and entry counts.
- Image extraction rejects unsupported file types and oversized files.
- Extracted rows are not saved until confirmed.
- Lock ends the session and protected pages require login again.

## Limitations to disclose honestly

- Handwriting recognition quality depends on image clarity, language, handwriting and the selected model. Always review extracted figures before saving.
- AI provider free tiers, model names and rate limits can change.
- The Android wrapper needs an internet connection and a deployed backend.
- SQLite on ephemeral free hosting is not appropriate for permanent real-shop data. Use a persistent database and tested backups before a public launch.

## Team details

Student names: To be added.

Registration numbers: To be added.

University / department: To be added.

Supervisor: To be added.
