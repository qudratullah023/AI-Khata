# AI Khata for Windows

Windows 10/11 desktop client. Build the installable setup executable with the
**AI Khata Windows desktop build** GitHub Actions workflow.

## Build locally (Windows)
1. Install Python 3.12.
2. Run `python -m pip install PySide6 pyinstaller`.
3. Run `pyinstaller --noconfirm --clean --onefile --windowed --name AI-Khata desktop/windows/main.py`.
4. Install Inno Setup and run `iscc desktop/windows/installer.iss`.
5. Installer output: `desktop/windows/dist/AI-Khata-Setup.exe`.

## Current desktop foundation
- Local SQLite shop database and owner PIN protection.
- Customer accounts, credit/payment entries, search, tags and notes.
- Supplier Khata, inventory, stock-aware product sales, and expenses.
- Daily summary, monthly expense snapshot, low-stock count.
- JSON backup export, customer statement PDF, wallet reference settings.
- Multiple isolated shop database profiles and staff PIN login.

The Windows client is an initial desktop implementation, not yet full parity with
Android 3.2.0. In particular, Android backup import compatibility, full profit/loss
reports, AI handwriting scan, scheduled Windows notifications, full staff permission
audit, raw Bluetooth ESC/POS support, and Android home-screen widget equivalents
remain follow-up work. Do not assume Android and Windows databases sync automatically:
use verified cross-platform backup/restore once that feature is released.
