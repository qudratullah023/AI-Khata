# AI Khata

AI Khata is a mobile-first digital khata for small shops. The shopkeeper can manage customer balances, record credit and repayments, review monthly summaries, and optionally extract draft transactions from a photograph of a handwritten ledger using a vision AI provider.

## What is included

- Private first-run shopkeeper account, PIN/password hashing, lock/logout, CSRF protection and temporary login throttling.
- Customer accounts with phone details, searchable list and individual transaction history.
- Credit and payment recording with automatic outstanding-balance calculations.
- Monthly activity reports and dashboard summaries.
- Optional Gemini or OpenRouter image extraction. The app shows editable draft rows; it never saves an AI result until the shopkeeper confirms it.
- Light/dark theme and responsive desktop/mobile web interface.
- Android WebView app shell with camera/gallery file selection.
- Automated backend regression tests and GitHub Actions debug-APK build.

## Run locally

1. Install Python 3.11 or newer.
2. Create and activate a virtual environment: `python -m venv .venv`.
3. Install packages: `pip install -r requirements.txt`.
4. Set environment variables from `.env.example`. At minimum, create a long random `SECRET_KEY`. AI API keys are optional.
5. Start the web app: `python app.py`.
6. Open `http://127.0.0.1:5000`. On first launch, create the shopkeeper account.

Run automated tests with:

```bash
python -m unittest discover -s tests -v
```

## Shopkeeper security

- First-run setup creates the single owner account. Public sign-up is disabled after setup.
- The shopkeeper signs in with a username and a PIN of at least 6 digits or a password of at least 8 characters.
- Credentials are stored as a one-way Werkzeug password hash, never as readable plain text.
- Five incorrect attempts within the sign-in window temporarily block further attempts from that IP.
- Forms and write APIs require a per-session CSRF token.
- Private pages and APIs require an active owner session. API clients receive a JSON 401 response when the session is locked or expired.
- Session cookies are HTTP-only and SameSite=Lax. Sessions expire after 12 hours of inactivity.
- The Lock button ends the current session.

This is application-level access control, not a substitute for a phone screen lock or a professional production security review. Use HTTPS and a strong persistent `SECRET_KEY`. Anyone with direct access to the server or database file may still access stored data.

## AI handwriting reader

AI scanning is optional. Manual customer and ledger features work without an AI key.

For Gemini, configure `AI_PROVIDER=gemini` and `GEMINI_API_KEY`. You can set `GEMINI_MODEL` to a currently available vision-capable model.

For OpenRouter, configure `AI_PROVIDER=openrouter`, `OPENROUTER_API_KEY`, and `OPENROUTER_MODEL` to a vision-capable model available to your account. Free model availability, pricing and rate limits can change.

API keys stay in the server environment and are never sent to the browser or included in the APK. The server checks that an uploaded file is actually a supported image, even if its filename or MIME type claims otherwise. The shopkeeper must review and confirm extracted fields before anything is written to the ledger.

## Deploy the web service

1. In Render, choose **New → Blueprint**, connect this GitHub repository and apply `render.yaml`.
2. Wait for the service to finish deploying and open its HTTPS URL.
3. Add the required environment values in Render. Keep the generated `SECRET_KEY`; add an AI key only when photo extraction is required.
4. Set `APP_PUBLIC_URL` to the actual HTTPS service URL.
5. Open the service and create the shopkeeper account.

**Storage warning:** the included SQLite database is intended for local development and a controlled classroom demonstration. Free web-host instances commonly have ephemeral filesystems, so records may be lost when an instance is replaced or restarted. Do not use this deployment as the permanent ledger for a real shop. Before real use, move the database to managed persistent storage (preferably a managed relational database), configure backups, and test restoration.

## Android APK

The Android project is in `android/`. It is a WebView client for the hosted Flask app; the APK does not contain the Python server, the database or any AI API key. It requires an internet connection and a deployed HTTPS backend.

The GitHub Actions workflow **AI Khata checks and Android APK**:
- Runs the Python regression tests.
- Compiles a debug APK.
- Uploads `app-debug.apk` as the artifact `ai-khata-debug-apk`.

After the workflow succeeds, open the repository's **Actions** tab, open the latest run, then download the **ai-khata-debug-apk** artifact. The APK can be installed on Android 6.0 or newer, including Android 10.

To build the APK for a different hosted URL, set the repository Actions variable `WEB_APP_URL` to the deployed HTTPS URL, or manually run the workflow and supply `web_app_url`. The default URL in the build configuration is `https://ai-khata.onrender.com`; it will only work after a service is actually deployed at that address. A successful APK compilation by itself does not mean the web backend or AI provider has been deployed.

## University presentation

See [docs/PRESENTATION_GUIDE.md](docs/PRESENTATION_GUIDE.md) for the problem statement, architecture, presentation flow and plain-language explanations of the technologies.

Student names, registration numbers, university and supervisor details are left as placeholders until the team confirms the correct details.

## Credits

Student developers: To be added after confirmation.

University / department: To be added after confirmation.

© 2026 AI Khata. All rights reserved by the project team, subject to the final ownership agreement.
