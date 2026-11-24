# AI Khata Desktop

AI Khata Desktop is a Windows 10 business-ledger application for small shops and traders. It provides customer and supplier accounts, inventory, expenses, reporting, secure local storage and optional AI-assisted bookkeeping.

## Main workspace

- Customer Khata: customer profiles, phone numbers, credit entries, payments, dated history, tags, notes, balance calculations and editable manual entries.
- Supplier Khata: supplier accounts, payables, payments and outstanding balances.
- Inventory: product catalogue, SKU, unit, purchase and selling prices, stock adjustments, reorder thresholds and customer-linked product sales.
- Sales integrity: product sales create a customer credit entry and reduce stock in one database transaction. Deleting a sale or customer restores linked stock.
- Expenses and reports: dated expense records, monthly revenue, cost of goods sold, gross profit, operating expenses, estimated net profit and stock valuation.
- Shop management: separate shop databases with owner PIN and restricted staff PIN access.
- Customer statements: consent-gated PDF statements and Windows printing, with JazzCash and Easypaisa details.
- Thermal printing: Windows printer-driver support and optional ESC/POS output through a configured COM port.
- Search and quick actions: universal search across customers, products, suppliers and ledger entries; dashboard actions and keyboard shortcuts.
- Reminders: scheduled daily summaries and weekly overdue-account notifications while the desktop application is running.
- AI Bookkeeper: optional Gemini chat with read-only customer context and owner-approved transaction drafts.
- Ledger photo reading: optional handwriting extraction from a selected image, followed by a row-by-row review before any entry is saved.
- Encrypted backups: password-protected export and validated restore, preserving the destination shop identity and owner PIN.
- Appearance: Liquid Glass, Glassmorphism and Soft UI visual themes.

## Windows installation

The CI workflow named **AI Khata Windows desktop build** creates `AI-Khata-Setup.exe` for 64-bit Windows 10/11. The installer includes the Python runtime and required application libraries.

The installed program stores its data in the current Windows user's `%APPDATA%\AI Khata Desktop` folder. Each shop has its own SQLite database. Ordinary bookkeeping, reporting, search and backups work offline. Optional AI features need internet access and a Gemini API key entered by the shop owner in Settings.

## Build from source

Requirements: Windows 10/11 x64 and Python 3.12.

```powershell
python -m pip install -r desktop/windows/requirements.txt
python -m py_compile desktop/windows/*.py
python -m unittest discover -s desktop/windows -p "test_*.py" -v
pyinstaller --noconfirm --clean --onefile --windowed --collect-all PySide6 --name AI-Khata --distpath desktop/windows/dist --workpath desktop/windows/build --specpath desktop/windows desktop/windows/main.py
```

Install Inno Setup, then run:

```powershell
iscc desktop/windows/installer.iss
```

The setup file is produced at `desktop/windows/dist/AI-Khata-Setup.exe`.

## Security and data

PINs are stored as salted PBKDF2-HMAC-SHA256 hashes. On Windows, optional AI API keys are protected using Windows DPAPI for the current user profile. Backup files use Scrypt key derivation and AES-256-GCM authenticated encryption. Keep the backup password separately; it cannot be recovered.

The application does not require an online account, hosted database or Android companion. Local records remain on this Windows profile unless the owner exports and moves an encrypted backup. AI requests send only the selected customer context and any image explicitly chosen by the owner. AI suggestions are drafts; the owner must approve them before they are saved.

## Project structure

- `desktop/windows/main.py`: desktop interface, navigation and Windows workflows.
- `desktop/windows/store.py`: SQLite data layer, accounting rules, reports and backup validation.
- `desktop/windows/ai_services.py`: optional AI API and Windows key protection.
- `desktop/windows/test_store.py`: regression tests for ledger, stock, reporting and backup operations.
- `.github/workflows/windows-desktop.yml`: Windows tests, packaging and installer creation.

Third-party packages and their licences remain subject to their respective licence terms. The software should be reviewed and tested in the intended shop environment before being used for official financial records.
