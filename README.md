# AI Khata

A mobile-first digital ledger for small shops. Shopkeepers can manage customer balances, record credit and payments, review monthly summaries, and optionally use a vision AI provider to extract entries from a photographed ledger page.

## Project status

This repository contains the first application foundation. The shopkeeper account lock, customer ledger, manual transactions, dashboard, reports, and AI extraction endpoints are implemented. The Android WebView shell and automated APK build are included separately. A hosted backend and a configured AI API key are required for the Android app to connect to a live service.

## Run locally

1. Install Python 3.11 or newer.
2. Create a virtual environment with `python -m venv .venv`.
3. Activate it: Windows `.venv\\Scripts\\activate`; macOS/Linux `source .venv/bin/activate`.
4. Install dependencies with `pip install -r requirements.txt`.
5. Copy `.env.example` to `.env`, set a strong `SECRET_KEY`, and optionally add an AI API key. Load the variables into your shell or use your preferred dotenv runner.
6. Start the server with `python app.py`.
7. Open `http://127.0.0.1:5000`. The first run asks the shopkeeper to create a private account.

## Shopkeeper security

- First-run setup creates one owner account. There is no public sign-up or guest write access.
- The shopkeeper signs in with a username and PIN (at least 6 digits) or password (at least 8 characters).
- Credentials are stored as a one-way password hash, not as plain text.
- Five incorrect login attempts temporarily lock that IP for 15 minutes.
- Forms and write APIs use a per-session CSRF token.
- Sessions are HTTP-only, SameSite=Lax and expire after 12 hours of inactivity.
- The Lock button ends the session immediately.

This protects application pages and ledger changes behind the shopkeeper account. It does not protect an unlocked phone, a compromised hosting account, a leaked server secret, or a database file copied by someone with server access. Use a device screen lock too. For real production use, enable HTTPS, configure persistent database/storage, backups, monitoring and a reviewed deployment secret.

## AI handwriting reader

AI extraction is optional. Without an API key, all customer and transaction features remain usable manually.

For Gemini, set `AI_PROVIDER=gemini` and `GEMINI_API_KEY`. Set `GEMINI_MODEL` to a currently available vision-capable Gemini model for your account.

For OpenRouter, set `AI_PROVIDER=openrouter`, `OPENROUTER_API_KEY`, and `OPENROUTER_MODEL` to a vision-capable model available to your account. Free model availability and rate limits change; a free model is not guaranteed to remain free.

API keys are read from server environment variables and are never sent to the browser. AI results are displayed for shopkeeper review; entries are not saved until the shopkeeper confirms them.

## Deploy on Render

1. Push this repository to GitHub.
2. In Render, choose New > Blueprint, connect this repository, and apply `render.yaml`.
3. Set AI provider credentials in the Render service environment settings if AI scanning is needed.
4. Set `APP_PUBLIC_URL` to the final HTTPS service address.
5. Set the Android `WEB_APP_URL` Gradle property or GitHub Actions variable to the same address when building the APK.

**Storage warning:** the included SQLite database is suitable for local development and a controlled classroom demo. Many free hosting plans use an ephemeral filesystem, so data can be lost when an instance is replaced. Do not store real shop accounts on an ephemeral instance. Before real customer use, move to managed PostgreSQL or attach supported persistent storage and verify backups/restoration.

## Android APK

The Android project is in `android/`. The GitHub Actions workflow builds a debug APK and publishes it as a downloadable workflow artifact when pushed or manually dispatched. The APK is a WebView client and needs the deployed web service URL to work. It does not contain an API key.

## University presentation

See `docs/PRESENTATION_GUIDE.md` for the architecture, demonstration sequence, and technology explanations. Student names and university details are intentionally left as placeholders until the team provides the correct information.

## Credits

Student developers: To be added after confirmation.

University / department: To be added after confirmation.

© 2026 AI Khata. All rights reserved by the project team, subject to the final ownership agreement.
