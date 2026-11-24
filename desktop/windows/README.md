# AI Khata Desktop for Windows

A standalone desktop program for Windows 10/11 x64. It uses a local SQLite database and does not need an Android app, web service or cloud account.

## Build

1. Install Python 3.12 on Windows.
2. Run `python -m pip install -r desktop/windows/requirements.txt`.
3. Run `python -m py_compile desktop/windows/*.py`.
4. Run `python -m unittest discover -s desktop/windows -p "test_*.py" -v`.
5. Build the executable with:

```powershell
pyinstaller --noconfirm --clean --onefile --windowed --collect-all PySide6 --name AI-Khata --distpath desktop/windows/dist --workpath desktop/windows/build --specpath desktop/windows desktop/windows/main.py
```

6. Install Inno Setup and run `iscc desktop/windows/installer.iss`.

The installer is written to `desktop/windows/dist/AI-Khata-Setup.exe`.

## Included workflows

Customer and supplier ledgers, credit and payment entries, profile tags and notes, consent-gated customer statements, inventory and stock-aware product sales, stock correction, expenses, monthly profit-and-loss estimates, universal search, multiple shop profiles, owner/staff PIN access, daily and overdue reminders, encrypted backup and restore, Windows printing, optional ESC/POS COM printing, quick actions, keyboard shortcuts, selectable themes, Gemini Bookkeeper and reviewed handwriting extraction.

## Local data and security

Data is stored under `%APPDATA%\AI Khata Desktop`. Each shop has a separate SQLite file. PINs use salted PBKDF2-HMAC-SHA256. AI API keys use Windows DPAPI. Exported backups are encrypted with Scrypt and AES-256-GCM. AI features are optional and require the owner to configure one or more API keys.

Staff access is intentionally restricted: staff can create customers and record customer entries or product sales, but cannot edit or delete ledger records, change business settings, view profit reports, manage suppliers or use owner-only AI/data tools.

## Validation

The automated suite covers customer balances, transaction edits and deletion, product-sale stock handling, supplier balances, daily summaries, costing and profit estimates, oldest-first overdue allocation, universal search, encrypted backup restoration and SQLite integrity checks. Hardware-specific printer behaviour and Windows notification delivery still require testing on the actual target PC and printer.
