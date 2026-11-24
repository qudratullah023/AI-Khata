"""Domain layer for AI Khata Desktop.

All monetary values are stored as integer paisa and stock quantities as milli-units.
The database is local to the Windows user profile; no remote service is required.
"""
from __future__ import annotations

import base64
import json
import os
import secrets
import sqlite3
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

BACKUP_MAGIC = "AI-KHATA-DESKTOP-ENCRYPTED"
BACKUP_VERSION = 1
TABLES = (
    "customers", "transactions", "customer_meta", "products",
    "expenses", "suppliers", "supplier_entries", "settings",
)
TABLE_COLUMNS = {
    "customers": ("id", "name", "phone", "created_at"),
    "transactions": ("id", "customer_id", "transaction_date", "transaction_type",
                     "description", "amount_paisa", "created_at", "product_id",
                     "quantity_milli", "unit_cost_paisa"),
    "customer_meta": ("customer_id", "tags", "notes", "share_opt_in"),
    "products": ("id", "name", "sku", "unit", "purchase_price_paisa",
                 "selling_price_paisa", "stock_milli", "low_stock_milli", "created_at"),
    "expenses": ("id", "expense_date", "category", "description", "amount_paisa", "created_at"),
    "suppliers": ("id", "name", "phone", "created_at"),
    "supplier_entries": ("id", "supplier_id", "entry_date", "entry_type",
                         "description", "amount_paisa", "created_at"),
    "settings": ("setting_key", "setting_value"),
}


def iso_today() -> str:
    return date.today().isoformat()


def to_paisa(amount: float | int) -> int:
    try:
        numeric = float(amount)
    except (TypeError, ValueError) as exc:
        raise ValueError("Enter a valid monetary amount.") from exc
    if not 0 < numeric <= 1_000_000_000:
        raise ValueError("Amount must be greater than zero and within the supported limit.")
    return round(numeric * 100)


def _valid_date(value: str) -> str:
    try:
        return datetime.strptime(value, "%Y-%m-%d").date().isoformat()
    except (TypeError, ValueError) as exc:
        raise ValueError("Date must use YYYY-MM-DD format.") from exc


def _derive_key(password: str, salt: bytes) -> bytes:
    if len(password) < 8:
        raise ValueError("Backup password must contain at least 8 characters.")
    return Scrypt(salt=salt, length=32, n=2**14, r=8, p=1).derive(password.encode("utf-8"))


class Store:
    """One isolated shop database."""

    def __init__(self, path: str | Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(str(self.path), timeout=15)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA busy_timeout=15000")
        self._initialize()

    def _initialize(self) -> None:
        self.db.executescript("""
        CREATE TABLE IF NOT EXISTS owner(
            id INTEGER PRIMARY KEY CHECK(id=1), shop_name TEXT NOT NULL,
            pin_salt TEXT NOT NULL, pin_hash TEXT NOT NULL, created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS customers(
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            phone TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS transactions(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
            transaction_date TEXT NOT NULL,
            transaction_type TEXT NOT NULL CHECK(transaction_type IN ('credit','payment')),
            description TEXT NOT NULL DEFAULT '',
            amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0),
            created_at TEXT NOT NULL,
            product_id INTEGER,
            quantity_milli INTEGER,
            unit_cost_paisa INTEGER
        );
        CREATE INDEX IF NOT EXISTS idx_transactions_customer_date
            ON transactions(customer_id, transaction_date, id);
        CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
        CREATE TABLE IF NOT EXISTS customer_meta(
            customer_id INTEGER PRIMARY KEY REFERENCES customers(id) ON DELETE CASCADE,
            tags TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '',
            share_opt_in INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE IF NOT EXISTS products(
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            sku TEXT NOT NULL DEFAULT '', unit TEXT NOT NULL DEFAULT 'pcs',
            purchase_price_paisa INTEGER NOT NULL DEFAULT 0,
            selling_price_paisa INTEGER NOT NULL,
            stock_milli INTEGER NOT NULL DEFAULT 0,
            low_stock_milli INTEGER NOT NULL DEFAULT 5000,
            created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS expenses(
            id INTEGER PRIMARY KEY AUTOINCREMENT, expense_date TEXT NOT NULL,
            category TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
            amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS suppliers(
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            phone TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS supplier_entries(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
            entry_date TEXT NOT NULL,
            entry_type TEXT NOT NULL CHECK(entry_type IN ('payable','payment')),
            description TEXT NOT NULL DEFAULT '',
            amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS settings(
            setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL
        );
        """)
        self.db.commit()

    def close(self) -> None:
        if self.db:
            self.db.close()

    def setting(self, key: str, default: str = "") -> str:
        row = self.db.execute("SELECT setting_value FROM settings WHERE setting_key=?", (key,)).fetchone()
        return str(row[0]) if row else default

    def set_setting(self, key: str, value: Any) -> None:
        self.db.execute(
            "INSERT INTO settings(setting_key,setting_value) VALUES(?,?) "
            "ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
            (key, str(value)),
        )
        self.db.commit()

    # Customer ledgers
    def customers(self, query: str = "") -> list[sqlite3.Row]:
        q = f"%{query.strip()}%"
        return self.db.execute("""
            SELECT c.id,c.name,c.phone,
              COALESCE(SUM(CASE WHEN t.transaction_type='credit'
                                THEN t.amount_paisa ELSE -t.amount_paisa END),0) AS balance_paisa,
              COALESCE(m.tags,'') AS tags
            FROM customers c
            LEFT JOIN transactions t ON t.customer_id=c.id
            LEFT JOIN customer_meta m ON m.customer_id=c.id
            WHERE c.name LIKE ? OR c.phone LIKE ? OR m.tags LIKE ?
            GROUP BY c.id ORDER BY c.name COLLATE NOCASE
        """, (q, q, q)).fetchall()

    def customer(self, customer_id: int) -> sqlite3.Row | None:
        return self.db.execute("SELECT * FROM customers WHERE id=?", (customer_id,)).fetchone()

    def add_customer(self, name: str, phone: str = "") -> int:
        name = name.strip()
        if not name:
            raise ValueError("Customer name is required.")
        cur = self.db.execute(
            "INSERT INTO customers(name,phone,created_at) VALUES(?,?,?)",
            (name, phone.strip(), iso_today()),
        )
        self.db.commit()
        return int(cur.lastrowid)

    def update_customer(self, customer_id: int, name: str, phone: str) -> None:
        if not name.strip():
            raise ValueError("Customer name is required.")
        cur = self.db.execute(
            "UPDATE customers SET name=?,phone=? WHERE id=?",
            (name.strip(), phone.strip(), customer_id),
        )
        if not cur.rowcount:
            raise ValueError("Customer no longer exists.")
        self.db.commit()

    def delete_customer(self, customer_id: int) -> None:
        self.db.execute("BEGIN IMMEDIATE")
        try:
            sold = self.db.execute(
                "SELECT product_id,quantity_milli FROM transactions "
                "WHERE customer_id=? AND product_id IS NOT NULL", (customer_id,)
            ).fetchall()
            for row in sold:
                if row["quantity_milli"]:
                    self.db.execute(
                        "UPDATE products SET stock_milli=stock_milli+? WHERE id=?",
                        (row["quantity_milli"], row["product_id"]),
                    )
            cur = self.db.execute("DELETE FROM customers WHERE id=?", (customer_id,))
            if not cur.rowcount:
                raise ValueError("Customer no longer exists.")
            self.db.commit()
        except Exception:
            self.db.rollback()
            raise

    def save_customer_meta(self, customer_id: int, tags: str, notes: str, share_opt_in: bool) -> None:
        if not self.customer(customer_id):
            raise ValueError("Customer no longer exists.")
        self.db.execute("""
            INSERT INTO customer_meta(customer_id,tags,notes,share_opt_in) VALUES(?,?,?,?)
            ON CONFLICT(customer_id) DO UPDATE SET tags=excluded.tags,notes=excluded.notes,
                share_opt_in=excluded.share_opt_in
        """, (customer_id, tags.strip(), notes.strip(), int(share_opt_in)))
        self.db.commit()

    def customer_transactions(self, customer_id: int) -> list[sqlite3.Row]:
        return self.db.execute(
            "SELECT * FROM transactions WHERE customer_id=? ORDER BY transaction_date DESC,id DESC",
            (customer_id,),
        ).fetchall()

    def customer_balance(self, customer_id: int) -> int:
        row = self.db.execute("""
            SELECT COALESCE(SUM(CASE WHEN transaction_type='credit'
                THEN amount_paisa ELSE -amount_paisa END),0)
            FROM transactions WHERE customer_id=?
        """, (customer_id,)).fetchone()
        return int(row[0])

    def add_transaction(self, customer_id: int, kind: str, amount: float,
                        description: str = "", transaction_date: str | None = None) -> int:
        if kind not in ("credit", "payment"):
            raise ValueError("Transaction type must be credit or payment.")
        if not self.customer(customer_id):
            raise ValueError("Select a valid customer.")
        entry_date = _valid_date(transaction_date or iso_today())
        cur = self.db.execute("""
            INSERT INTO transactions(customer_id,transaction_date,transaction_type,
                description,amount_paisa,created_at)
            VALUES(?,?,?,?,?,?)
        """, (customer_id, entry_date, kind, description.strip(),
              to_paisa(amount), datetime.now().isoformat(timespec="seconds")))
        self.db.commit()
        return int(cur.lastrowid)

    def edit_transaction(self, transaction_id: int, kind: str, amount: float,
                          description: str, transaction_date: str) -> None:
        if kind not in ("credit", "payment"):
            raise ValueError("Transaction type must be credit or payment.")
        transaction_date = _valid_date(transaction_date)
        cur = self.db.execute("""
            UPDATE transactions SET transaction_type=?,amount_paisa=?,description=?,transaction_date=?
            WHERE id=? AND product_id IS NULL
        """, (kind, to_paisa(amount), description.strip(), transaction_date, transaction_id))
        if not cur.rowcount:
            raise ValueError("This entry cannot be edited or no longer exists.")
        self.db.commit()

    def delete_transaction(self, transaction_id: int) -> None:
        self.db.execute("BEGIN IMMEDIATE")
        try:
            row = self.db.execute(
                "SELECT product_id,quantity_milli FROM transactions WHERE id=?", (transaction_id,)
            ).fetchone()
            if not row:
                raise ValueError("Entry no longer exists.")
            if row["product_id"] is not None and row["quantity_milli"]:
                self.db.execute(
                    "UPDATE products SET stock_milli=stock_milli+? WHERE id=?",
                    (row["quantity_milli"], row["product_id"]),
                )
            self.db.execute("DELETE FROM transactions WHERE id=?", (transaction_id,))
            self.db.commit()
        except Exception:
            self.db.rollback()
            raise

    # Supplier ledger
    def suppliers(self, query: str = "") -> list[sqlite3.Row]:
        q = f"%{query.strip()}%"
        return self.db.execute("""
            SELECT s.id,s.name,s.phone,
              COALESCE(SUM(CASE WHEN e.entry_type='payable'
                THEN e.amount_paisa ELSE -e.amount_paisa END),0) balance_paisa
            FROM suppliers s LEFT JOIN supplier_entries e ON e.supplier_id=s.id
            WHERE s.name LIKE ? OR s.phone LIKE ?
            GROUP BY s.id ORDER BY s.name COLLATE NOCASE
        """, (q, q)).fetchall()

    def add_supplier(self, name: str, phone: str = "") -> int:
        if not name.strip():
            raise ValueError("Supplier name is required.")
        cur = self.db.execute(
            "INSERT INTO suppliers(name,phone,created_at) VALUES(?,?,?)",
            (name.strip(), phone.strip(), iso_today()),
        )
        self.db.commit()
        return int(cur.lastrowid)

    def add_supplier_entry(self, supplier_id: int, kind: str, amount: float,
                           description: str = "", entry_date: str | None = None) -> int:
        if kind not in ("payable", "payment"):
            raise ValueError("Entry must be payable or payment.")
        if not self.db.execute("SELECT 1 FROM suppliers WHERE id=?", (supplier_id,)).fetchone():
            raise ValueError("Supplier no longer exists.")
        entry_date = _valid_date(entry_date or iso_today())
        cur = self.db.execute("""
            INSERT INTO supplier_entries(supplier_id,entry_date,entry_type,description,amount_paisa,created_at)
            VALUES(?,?,?,?,?,?)
        """, (supplier_id, entry_date, kind, description.strip(), to_paisa(amount),
              datetime.now().isoformat(timespec="seconds")))
        self.db.commit()
        return int(cur.lastrowid)

    def supplier_transactions(self, supplier_id: int) -> list[sqlite3.Row]:
        return self.db.execute(
            "SELECT * FROM supplier_entries WHERE supplier_id=? ORDER BY entry_date DESC,id DESC",
            (supplier_id,),
        ).fetchall()

    # Inventory, sales and expenses
    def products(self, query: str = "") -> list[sqlite3.Row]:
        q = f"%{query.strip()}%"
        return self.db.execute("SELECT * FROM products WHERE name LIKE ? OR sku LIKE ? ORDER BY name",
                               (q, q)).fetchall()

    def add_product(self, name: str, sku: str, unit: str, purchase_price: float,
                    selling_price: float, stock: float, low_stock: float) -> int:
        if not name.strip() or selling_price <= 0 or purchase_price < 0 or stock < 0 or low_stock < 0:
            raise ValueError("Enter a product name, valid prices and non-negative stock values.")
        cur = self.db.execute("""
            INSERT INTO products(name,sku,unit,purchase_price_paisa,selling_price_paisa,
                                 stock_milli,low_stock_milli,created_at)
            VALUES(?,?,?,?,?,?,?,?)
        """, (name.strip(), sku.strip(), unit.strip() or "pcs", round(purchase_price*100),
              to_paisa(selling_price), round(stock*1000), round(low_stock*1000), iso_today()))
        self.db.commit()
        return int(cur.lastrowid)

    def update_product(self, product_id: int, name: str, sku: str, unit: str,
                       purchase_price: float, selling_price: float, low_stock: float) -> None:
        if not name.strip() or selling_price <= 0 or purchase_price < 0 or low_stock < 0:
            raise ValueError("Product values are invalid.")
        cur = self.db.execute("""
            UPDATE products SET name=?,sku=?,unit=?,purchase_price_paisa=?,
                selling_price_paisa=?,low_stock_milli=? WHERE id=?
        """, (name.strip(), sku.strip(), unit.strip() or "pcs", round(purchase_price*100),
              to_paisa(selling_price), round(low_stock*1000), product_id))
        if not cur.rowcount:
            raise ValueError("Product no longer exists.")
        self.db.commit()

    def adjust_stock(self, product_id: int, delta: float, note: str = "") -> None:
        self.db.execute("BEGIN IMMEDIATE")
        try:
            row = self.db.execute("SELECT stock_milli FROM products WHERE id=?", (product_id,)).fetchone()
            if not row:
                raise ValueError("Product no longer exists.")
            updated = int(row[0]) + round(delta * 1000)
            if updated < 0:
                raise ValueError("Stock cannot become negative.")
            self.db.execute("UPDATE products SET stock_milli=? WHERE id=?", (updated, product_id))
            self.db.commit()
        except Exception:
            self.db.rollback()
            raise

    def delete_product(self, product_id: int) -> None:
        # Old product-sale rows retain their cost/quantity snapshots for reports.
        self.db.execute("DELETE FROM products WHERE id=?", (product_id,))
        self.db.commit()

    def record_product_sale(self, customer_id: int, product_id: int, quantity: float,
                            transaction_date: str | None = None) -> int:
        if quantity <= 0:
            raise ValueError("Quantity must be greater than zero.")
        self.db.execute("BEGIN IMMEDIATE")
        try:
            product = self.db.execute("SELECT * FROM products WHERE id=?", (product_id,)).fetchone()
            if not product or round(quantity*1000) > product["stock_milli"]:
                raise ValueError("Product is unavailable or stock is insufficient.")
            if not self.customer(customer_id):
                raise ValueError("Select a valid customer.")
            qty = round(quantity * 1000)
            total_paisa = round(qty * product["selling_price_paisa"] / 1000)
            cur = self.db.execute("""
                INSERT INTO transactions(customer_id,transaction_date,transaction_type,description,
                    amount_paisa,created_at,product_id,quantity_milli,unit_cost_paisa)
                VALUES(?,?,'credit',?,?,?,?,?,?)
            """, (customer_id, transaction_date or iso_today(), product["name"], total_paisa,
                  datetime.now().isoformat(timespec="seconds"), product_id, qty,
                  product["purchase_price_paisa"]))
            self.db.execute("UPDATE products SET stock_milli=stock_milli-? WHERE id=?",
                            (qty, product_id))
            self.db.commit()
            return int(cur.lastrowid)
        except Exception:
            self.db.rollback()
            raise

    def expenses(self, month: str | None = None) -> list[sqlite3.Row]:
        if month:
            return self.db.execute("SELECT * FROM expenses WHERE expense_date LIKE ? ORDER BY expense_date DESC,id DESC",
                                   (month + "%",)).fetchall()
        return self.db.execute("SELECT * FROM expenses ORDER BY expense_date DESC,id DESC").fetchall()

    def add_expense(self, category: str, description: str, amount: float,
                    expense_date: str | None = None) -> int:
        if not category.strip():
            raise ValueError("Expense category is required.")
        expense_date = _valid_date(expense_date or iso_today())
        cur = self.db.execute("""
            INSERT INTO expenses(expense_date,category,description,amount_paisa,created_at)
            VALUES(?,?,?,?,?)
        """, (expense_date, category.strip(), description.strip(), to_paisa(amount),
              datetime.now().isoformat(timespec="seconds")))
        self.db.commit()
        return int(cur.lastrowid)

    def delete_expense(self, expense_id: int) -> None:
        self.db.execute("DELETE FROM expenses WHERE id=?", (expense_id,))
        self.db.commit()

    # Reporting, search and integrity
    def daily_summary(self, day: str | None = None) -> dict[str, int]:
        day = day or iso_today()
        t = self.db.execute("""
            SELECT COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount_paisa ELSE 0 END),0) sales,
                   COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount_paisa ELSE 0 END),0) receipts
            FROM transactions WHERE transaction_date=?
        """, (day,)).fetchone()
        new_credit = int(self.db.execute(
            "SELECT COALESCE(SUM(amount_paisa),0) FROM transactions WHERE transaction_date=? AND transaction_type='credit'",
            (day,)).fetchone()[0])
        return {"sales_paisa": int(t["sales"]), "receipts_paisa": int(t["receipts"]),
                "new_credit_paisa": new_credit, "net_due_change_paisa": int(t["sales"])-int(t["receipts"])}

    def profit_loss(self, month: str | None = None) -> dict[str, int]:
        month = month or date.today().strftime("%Y-%m")
        sales = self.db.execute("""
            SELECT COALESCE(SUM(amount_paisa),0) revenue,
                   COALESCE(SUM(CASE WHEN product_id IS NOT NULL AND unit_cost_paisa IS NOT NULL
                     THEN (quantity_milli*unit_cost_paisa)/1000 ELSE 0 END),0) cogs
            FROM transactions
            WHERE transaction_type='credit' AND product_id IS NOT NULL AND transaction_date LIKE ?
        """, (month + "%",)).fetchone()
        expense = self.db.execute(
            "SELECT COALESCE(SUM(amount_paisa),0) FROM expenses WHERE expense_date LIKE ?",
            (month + "%",)).fetchone()[0]
        inventory = self.db.execute(
            "SELECT COALESCE(SUM(stock_milli*purchase_price_paisa)/1000,0) FROM products").fetchone()[0]
        return {"revenue_paisa": int(sales["revenue"]), "cogs_paisa": int(sales["cogs"]),
                "gross_profit_paisa": int(sales["revenue"])-int(sales["cogs"]),
                "expenses_paisa": int(expense),
                "net_estimate_paisa": int(sales["revenue"])-int(sales["cogs"])-int(expense),
                "inventory_value_paisa": int(inventory)}

    def overdue_customers(self, days: int = 30) -> list[dict[str, Any]]:
        cutoff = (date.today() - timedelta(days=days)).isoformat()
        result = []
        for customer in self.db.execute("SELECT id,name,phone FROM customers ORDER BY name"):
            entries = self.db.execute("""
                SELECT transaction_date,transaction_type,amount_paisa
                FROM transactions WHERE customer_id=? ORDER BY transaction_date,id
            """, (customer["id"],)).fetchall()
            unpaid: list[list[Any]] = []
            advance = 0
            for row in entries:
                amount = int(row["amount_paisa"])
                if row["transaction_type"] == "credit":
                    # Earlier advance payments are applied to later credit entries first.
                    used = min(amount, advance)
                    amount -= used
                    advance -= used
                    if amount:
                        unpaid.append([row["transaction_date"], amount])
                else:
                    remaining = amount
                    for item in unpaid:
                        used = min(item[1], remaining)
                        item[1] -= used
                        remaining -= used
                        if remaining == 0:
                            break
                    advance += remaining
            due = sum(item[1] for item in unpaid)
            overdue = sum(item[1] for item in unpaid if item[0] <= cutoff)
            if due > 0 and overdue > 0:
                result.append({"id": customer["id"], "name": customer["name"],
                               "phone": customer["phone"], "due_paisa": due,
                               "overdue_paisa": overdue})
        return result

    def universal_search(self, query: str) -> list[dict[str, Any]]:
        q = query.strip()
        if not q:
            return []
        like = f"%{q}%"
        results: list[dict[str, Any]] = []
        for row in self.db.execute("""
            SELECT id,name,phone FROM customers WHERE name LIKE ? OR phone LIKE ?
            ORDER BY name LIMIT 25
        """, (like, like)):
            results.append({"type": "Customer", "id": row["id"], "title": row["name"], "detail": row["phone"]})
        for row in self.db.execute("SELECT id,name,sku FROM products WHERE name LIKE ? OR sku LIKE ? LIMIT 20",
                                   (like, like)):
            results.append({"type": "Product", "id": row["id"], "title": row["name"], "detail": row["sku"]})
        for row in self.db.execute("SELECT id,name,phone FROM suppliers WHERE name LIKE ? OR phone LIKE ? LIMIT 20",
                                   (like, like)):
            results.append({"type": "Supplier", "id": row["id"], "title": row["name"], "detail": row["phone"]})
        # Amount, date and free-text transaction search.
        amount_query = None
        try:
            amount_query = to_paisa(float(q.replace(",", "").replace("Rs.", "").strip()))
        except (ValueError, TypeError):
            pass
        tx_rows = self.db.execute("""
            SELECT t.id,t.transaction_date,t.description,t.amount_paisa,t.transaction_type,c.name
            FROM transactions t JOIN customers c ON c.id=t.customer_id
            WHERE t.description LIKE ? OR t.transaction_date LIKE ? OR c.name LIKE ?
              OR (? IS NOT NULL AND t.amount_paisa=?)
            ORDER BY t.transaction_date DESC LIMIT 25
        """, (like, like, like, amount_query, amount_query)).fetchall()
        for row in tx_rows:
            results.append({"type": "Ledger entry", "id": row["id"], "title": row["name"],
                            "detail": f"{row['transaction_date']} · {row['transaction_type']} · {row['description']} · {row['amount_paisa']/100:.2f}"})
        supplier_rows = self.db.execute("""
            SELECT e.id,e.entry_date,e.entry_type,e.description,e.amount_paisa,s.name
            FROM supplier_entries e JOIN suppliers s ON s.id=e.supplier_id
            WHERE e.description LIKE ? OR e.entry_date LIKE ? OR s.name LIKE ?
            ORDER BY e.entry_date DESC LIMIT 20
        """, (like, like, like)).fetchall()
        for row in supplier_rows:
            results.append({"type": "Supplier entry", "id": row["id"], "title": row["name"],
                            "detail": f"{row['entry_date']} · {row['entry_type']} · {row['description']} · {row['amount_paisa']/100:.2f}"})
        return results[:75]

    def integrity_check(self) -> tuple[bool, str]:
        quick = self.db.execute("PRAGMA quick_check(1)").fetchone()[0]
        fk = self.db.execute("PRAGMA foreign_key_check").fetchall()
        ok = quick == "ok" and not fk
        return ok, "Database integrity is healthy." if ok else f"Integrity check failed: {quick}; foreign-key issues: {len(fk)}"

    # Password-protected backup and safe restore
    def export_backup(self, path: str | Path, password: str) -> None:
        tables: dict[str, list[dict[str, Any]]] = {}
        for table in TABLES:
            tables[table] = [dict(row) for row in self.db.execute(f"SELECT * FROM {table}")]
        # API credentials are protected for one Windows user profile and are not portable backups.
        tables["settings"] = [row for row in tables["settings"]
                              if row["setting_key"] not in ("ai_key", "ai_keys")]
        payload = json.dumps({"format": BACKUP_MAGIC, "version": BACKUP_VERSION,
                              "exported_at": datetime.now().isoformat(timespec="seconds"),
                              "tables": tables}, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        salt, nonce = secrets.token_bytes(16), secrets.token_bytes(12)
        encrypted = AESGCM(_derive_key(password, salt)).encrypt(nonce, payload, BACKUP_MAGIC.encode())
        envelope = {"magic": BACKUP_MAGIC, "version": BACKUP_VERSION,
                    "salt": base64.b64encode(salt).decode(),
                    "nonce": base64.b64encode(nonce).decode(),
                    "ciphertext": base64.b64encode(encrypted).decode()}
        Path(path).write_text(json.dumps(envelope, indent=2), encoding="utf-8")

    def restore_backup(self, path: str | Path, password: str) -> None:
        backup_path = Path(path)
        if not backup_path.is_file() or backup_path.stat().st_size > 64 * 1024 * 1024:
            raise ValueError("Backup file is missing or exceeds the 64 MB size limit.")
        raw = json.loads(backup_path.read_text(encoding="utf-8"))
        if raw.get("magic") != BACKUP_MAGIC or raw.get("version") != BACKUP_VERSION:
            raise ValueError("This file is not a supported AI Khata Desktop backup.")
        try:
            salt = base64.b64decode(raw["salt"], validate=True)
            nonce = base64.b64decode(raw["nonce"], validate=True)
            ciphertext = base64.b64decode(raw["ciphertext"], validate=True)
            plain = AESGCM(_derive_key(password, salt)).decrypt(nonce, ciphertext, BACKUP_MAGIC.encode())
            data = json.loads(plain.decode("utf-8"))
        except Exception as exc:
            raise ValueError("Backup password is incorrect or the file is damaged.") from exc
        tables = data.get("tables")
        if data.get("format") != BACKUP_MAGIC or data.get("version") != BACKUP_VERSION or not isinstance(tables, dict):
            raise ValueError("Backup contents are invalid.")
        if set(tables) != set(TABLES):
            raise ValueError("Backup is missing required tables or contains unknown tables.")
        for table, rows in tables.items():
            if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
                raise ValueError(f"Invalid data in backup table: {table}.")
            allowed = set(TABLE_COLUMNS[table])
            if any(set(row) != allowed for row in rows):
                raise ValueError(f"Unexpected columns in backup table: {table}.")
        current_owner = self.db.execute("SELECT * FROM owner WHERE id=1").fetchone()
        current_ai_keys = self.db.execute("SELECT setting_key,setting_value FROM settings WHERE setting_key IN ('ai_key','ai_keys')").fetchall()
        self.db.execute("BEGIN IMMEDIATE")
        try:
            # The destination shop identity and owner PIN are deliberately retained.
            for table in reversed(TABLES):
                self.db.execute(f"DELETE FROM {table}")
            for table in TABLES:
                columns = TABLE_COLUMNS[table]
                if table == "settings":
                    self.db.executemany("INSERT INTO settings(setting_key,setting_value) VALUES(?,?)",
                                        [(r["setting_key"], r["setting_value"]) for r in tables[table]])
                elif rows := tables[table]:
                    marks = ",".join("?" for _ in columns)
                    names = ",".join(columns)
                    self.db.executemany(f"INSERT INTO {table}({names}) VALUES({marks})",
                                        [tuple(row[col] for col in columns) for row in rows])
            for row in current_ai_keys:
                self.db.execute("INSERT INTO settings(setting_key,setting_value) VALUES(?,?) "
                                "ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",
                                (row["setting_key"], row["setting_value"]))
            quick = self.db.execute("PRAGMA quick_check(1)").fetchone()[0]
            foreign_key_errors = self.db.execute("PRAGMA foreign_key_check").fetchall()
            if quick != "ok" or foreign_key_errors:
                raise ValueError("Restored database failed its integrity check.")
            self.db.execute("DELETE FROM owner")
            if current_owner:
                self.db.execute("INSERT INTO owner(id,shop_name,pin_salt,pin_hash,created_at) VALUES(?,?,?,?,?)",
                                tuple(current_owner))
            self.db.commit()
        except Exception:
            self.db.rollback()
            raise
