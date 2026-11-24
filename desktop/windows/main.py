from __future__ import annotations

import hashlib
import hmac
import json
import os
import re
import secrets
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Any, Callable

from PySide6.QtCore import QObject, QRunnable, Qt, QThreadPool, QTimer, Signal
from PySide6.QtGui import QFont, QKeySequence, QPdfWriter, QPainter, QPageSize, QShortcut, QTextDocument
from PySide6.QtPrintSupport import QPrintDialog, QPrinter
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QComboBox, QDialog, QDialogButtonBox, QDoubleSpinBox,
    QFileDialog, QFormLayout, QFrame, QGridLayout, QGroupBox, QHBoxLayout,
    QHeaderView, QInputDialog, QLabel, QLineEdit, QMainWindow, QMessageBox,
    QPushButton, QRadioButton, QScrollArea, QSplitter, QStackedWidget, QStyle,
    QSystemTrayIcon, QTableWidget, QTableWidgetItem, QTextEdit, QVBoxLayout,
    QWidget, QDateEdit, QProgressBar
)

from store import Store, iso_today
from ai_services import (
    DEFAULT_MODELS, ask_bookkeeper, protect_api_key, read_ledger_photo, reveal_api_key
)

APP_NAME = "AI Khata"
APP_VERSION = "1.0.0"
APP_DIR = Path(os.getenv("APPDATA", str(Path.home()))) / "AI Khata Desktop"
APP_DIR.mkdir(parents=True, exist_ok=True)
SHOP_REGISTRY = APP_DIR / "shops.json"


def money(paisa: int | float) -> str:
    return f"Rs. {float(paisa or 0) / 100:,.2f}"


def pin_hash(pin: str, salt: bytes | None = None) -> tuple[str, str]:
    salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", pin.encode("utf-8"), salt, 240_000)
    return salt.hex(), digest.hex()


def verify_pin(pin: str, salt_hex: str, digest_hex: str) -> bool:
    try:
        return hmac.compare_digest(pin_hash(pin, bytes.fromhex(salt_hex))[1], digest_hex)
    except (ValueError, TypeError):
        return False


def ask_text(parent: QWidget, title: str, label: str, secret: bool = False,
             default: str = "") -> str | None:
    mode = QLineEdit.EchoMode.Password if secret else QLineEdit.EchoMode.Normal
    value, accepted = QInputDialog.getText(parent, title, label, mode, default)
    return value.strip() if accepted else None


def inform(parent: QWidget, message: str, error: bool = False) -> None:
    method = QMessageBox.critical if error else QMessageBox.information
    method(parent, APP_NAME, message)


def confirm(parent: QWidget, message: str) -> bool:
    return QMessageBox.question(parent, APP_NAME, message,
        QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        QMessageBox.StandardButton.No) == QMessageBox.StandardButton.Yes


class LoginDialog(QDialog):
    def __init__(self, shop_name: str, parent: QWidget | None = None):
        super().__init__(parent)
        self.setWindowTitle("Unlock shop")
        self.setMinimumWidth(360)
        layout = QVBoxLayout(self)
        title = QLabel(shop_name)
        title.setObjectName("dialogTitle")
        layout.addWidget(title)
        layout.addWidget(QLabel("Enter your owner or staff PIN to continue."))
        self.pin = QLineEdit()
        self.pin.setEchoMode(QLineEdit.EchoMode.Password)
        self.pin.setMaxLength(12)
        self.pin.setPlaceholderText("PIN")
        self.pin.returnPressed.connect(self.accept)
        layout.addWidget(self.pin)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def value(self) -> str:
        return self.pin.text()


class JobSignals(QObject):
    completed = Signal(object)
    failed = Signal(str)


class BackgroundJob(QRunnable):
    def __init__(self, operation: Callable[[], Any]):
        super().__init__()
        self.operation = operation
        self.signals = JobSignals()

    def run(self) -> None:
        try:
            self.signals.completed.emit(self.operation())
        except Exception as exc:
            self.signals.failed.emit(str(exc))


class ScanReviewDialog(QDialog):
    """A human review step between OCR extraction and ledger insertion."""

    def __init__(self, entries: list[dict[str, Any]], parent: QWidget | None = None):
        super().__init__(parent)
        self.setWindowTitle("Review scanned ledger entries")
        self.resize(980, 520)
        layout = QVBoxLayout(self)
        layout.addWidget(QLabel("Check each entry you have verified. Unclear values should be corrected before saving."))
        self.table = QTableWidget(len(entries), 7)
        self.table.setHorizontalHeaderLabels(["Save", "Customer", "Date", "Type", "Amount (Rs.)", "Description", "Confidence"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setAlternatingRowColors(True)
        for row, entry in enumerate(entries):
            check = QTableWidgetItem()
            check.setFlags(Qt.ItemFlag.ItemIsEnabled | Qt.ItemFlag.ItemIsUserCheckable)
            check.setCheckState(Qt.CheckState.Unchecked if entry.get("needs_review") else Qt.CheckState.Checked)
            self.table.setItem(row, 0, check)
            values = [entry.get("customer_name", ""), entry.get("date") or iso_today(),
                      entry.get("type", "credit"), "" if entry.get("amount") is None else str(entry["amount"]),
                      entry.get("description", ""), f"{float(entry.get('confidence') or 0):.0%}"]
            for col, value in enumerate(values, 1):
                self.table.setItem(row, col, QTableWidgetItem(str(value)))
        layout.addWidget(self.table)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def approved_entries(self) -> list[dict[str, Any]]:
        approved = []
        for row in range(self.table.rowCount()):
            check = self.table.item(row, 0)
            if not check or check.checkState() != Qt.CheckState.Checked:
                continue
            try:
                amount = float(self.table.item(row, 4).text().replace(",", ""))
            except (ValueError, AttributeError):
                continue
            approved.append({
                "customer_name": self.table.item(row, 1).text().strip(),
                "date": self.table.item(row, 2).text().strip(),
                "type": self.table.item(row, 3).text().strip().lower(),
                "amount": amount,
                "description": self.table.item(row, 5).text().strip(),
            })
        return approved


class MainWindow(QMainWindow):
    def __init__(self, store: Store, shop_id: str, shop_name: str,
                 shops: list[dict[str, str]], staff_mode: bool = False):
        super().__init__()
        self.store = store
        self.shop_id = shop_id
        self.shop_name = shop_name
        self.shops = shops
        self.staff_mode = staff_mode
        self.pool = QThreadPool.globalInstance()
        self.current_customer_id: int | None = None
        self.setWindowTitle(f"{APP_NAME}  |  {shop_name}")
        self.setMinimumSize(1120, 720)
        self.resize(1360, 860)
        self._build_shell()
        self._build_pages()
        self.navigate("Dashboard")
        self._apply_theme(self.store.setting("theme", "Liquid Glass"))
        self._apply_access_rules()
        self.refresh_all()
        self._setup_shortcuts()
        self._setup_notifications()

    # Main layout and visual system
    def _build_shell(self) -> None:
        root = QWidget()
        root_layout = QHBoxLayout(root)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)
        self.setCentralWidget(root)

        self.sidebar = QFrame()
        self.sidebar.setObjectName("sidebar")
        self.sidebar.setFixedWidth(230)
        side = QVBoxLayout(self.sidebar)
        side.setContentsMargins(18, 24, 18, 20)
        side.setSpacing(9)
        brand = QLabel("AI KHATA")
        brand.setObjectName("brand")
        side.addWidget(brand)
        side.addWidget(QLabel("Business management"))
        side.addSpacing(20)

        self.nav_buttons: dict[str, QPushButton] = {}
        nav_items = [
            ("Dashboard", "Overview"), ("Customers", "Customer Khata"),
            ("Suppliers", "Supplier Khata"), ("Inventory", "Inventory"),
            ("Expenses", "Expenses"), ("Reports", "Reports"),
            ("AI Bookkeeper", "AI Bookkeeper"), ("Settings", "Settings")
        ]
        for key, label in nav_items:
            button = QPushButton(label)
            button.setObjectName("navButton")
            button.setCheckable(True)
            button.clicked.connect(lambda checked=False, name=key: self.navigate(name))
            side.addWidget(button)
            self.nav_buttons[key] = button
        side.addStretch(1)
        self.role_label = QLabel("OWNER ACCESS" if not self.staff_mode else "STAFF ACCESS")
        self.role_label.setObjectName("roleTag")
        side.addWidget(self.role_label)
        side.addWidget(QLabel(f"Version {APP_VERSION}"))
        root_layout.addWidget(self.sidebar)

        right = QWidget()
        right_layout = QVBoxLayout(right)
        right_layout.setContentsMargins(26, 18, 26, 22)
        right_layout.setSpacing(16)
        top = QHBoxLayout()
        self.page_heading = QLabel("Overview")
        self.page_heading.setObjectName("pageHeading")
        top.addWidget(self.page_heading)
        top.addStretch()
        self.global_search = QLineEdit()
        self.global_search.setPlaceholderText("Search customers, products, suppliers, entries…  Ctrl+F")
        self.global_search.setMinimumWidth(340)
        self.global_search.returnPressed.connect(self.open_global_search)
        top.addWidget(self.global_search)
        self.shop_selector = QComboBox()
        for item in self.shops:
            self.shop_selector.addItem(item["name"], item["id"])
        current_index = self.shop_selector.findData(self.shop_id)
        self.shop_selector.setCurrentIndex(max(0, current_index))
        self.shop_selector.currentIndexChanged.connect(self.switch_shop)
        self.shop_selector.setMinimumWidth(180)
        top.addWidget(self.shop_selector)
        right_layout.addLayout(top)
        self.pages = QStackedWidget()
        right_layout.addWidget(self.pages, 1)
        root_layout.addWidget(right, 1)

    def _page(self, key: str, title: str) -> tuple[QWidget, QVBoxLayout]:
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(0, 4, 0, 0)
        layout.setSpacing(14)
        self.pages.addWidget(page)
        self.page_index[key] = self.pages.count() - 1
        return page, layout

    def _build_pages(self) -> None:
        self.page_index: dict[str, int] = {}
        self._build_dashboard()
        self._build_customers()
        self._build_suppliers()
        self._build_inventory()
        self._build_expenses()
        self._build_reports()
        self._build_ai()
        self._build_settings()

    def navigate(self, key: str) -> None:
        if key not in self.page_index:
            return
        button = self.nav_buttons.get(key)
        if button and not button.isVisible():
            return
        self.pages.setCurrentIndex(self.page_index[key])
        self.page_heading.setText(key)
        for name, item in self.nav_buttons.items():
            item.setChecked(name == key)

    @staticmethod
    def _table(headers: list[str]) -> QTableWidget:
        table = QTableWidget(0, len(headers))
        table.setHorizontalHeaderLabels(headers)
        table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        table.setSelectionMode(QTableWidget.SelectionMode.SingleSelection)
        table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        table.setAlternatingRowColors(True)
        table.verticalHeader().setVisible(False)
        return table

    @staticmethod
    def _button(text: str, callback: Callable[[], None], primary: bool = False) -> QPushButton:
        button = QPushButton(text)
        if primary:
            button.setObjectName("primaryButton")
        button.setMinimumHeight(38)
        button.clicked.connect(callback)
        return button

    def _build_dashboard(self) -> None:
        page, layout = self._page("Dashboard", "Overview")
        hero = QFrame()
        hero.setObjectName("hero")
        h = QVBoxLayout(hero)
        h.setContentsMargins(26, 22, 26, 22)
        self.hero_greeting = QLabel(f"Welcome to {self.shop_name}")
        self.hero_greeting.setObjectName("heroTitle")
        h.addWidget(self.hero_greeting)
        h.addWidget(QLabel("Your shop accounts, stock and daily performance in one place."))
        layout.addWidget(hero)
        cards = QGridLayout()
        self.stat_customers = self._stat_card("CUSTOMERS", "0")
        self.stat_due = self._stat_card("CUSTOMER BALANCE DUE", "Rs. 0.00")
        self.stat_sales = self._stat_card("TODAY'S CREDIT", "Rs. 0.00")
        self.stat_receipts = self._stat_card("RECEIVED TODAY", "Rs. 0.00")
        for i, card in enumerate([self.stat_customers, self.stat_due, self.stat_sales, self.stat_receipts]):
            cards.addWidget(card, 0, i)
        layout.addLayout(cards)
        quick = QGroupBox("Quick actions")
        q = QHBoxLayout(quick)
        q.addWidget(self._button("＋ New customer", self.add_customer, True))
        q.addWidget(self._button("＋ Record credit", lambda: self.add_entry("credit")))
        q.addWidget(self._button("＋ Receive payment", lambda: self.add_entry("payment")))
        self.dashboard_scan_button = self._button("Scan ledger photo", self.scan_ledger)
        q.addWidget(self.dashboard_scan_button)
        layout.addWidget(quick)
        lower = QHBoxLayout()
        recent_box = QGroupBox("Recent customer accounts")
        rb = QVBoxLayout(recent_box)
        self.recent_customers = self._table(["Customer", "Phone", "Balance"])
        rb.addWidget(self.recent_customers)
        lower.addWidget(recent_box, 3)
        side_box = QGroupBox("Business snapshot")
        sb = QVBoxLayout(side_box)
        self.daily_summary_label = QLabel()
        self.daily_summary_label.setObjectName("summaryText")
        sb.addWidget(self.daily_summary_label)
        self.overdue_label = QLabel()
        self.overdue_label.setWordWrap(True)
        sb.addWidget(self.overdue_label)
        sb.addStretch()
        lower.addWidget(side_box, 2)
        layout.addLayout(lower, 1)
        self.pages.setCurrentWidget(page)

    def _stat_card(self, caption: str, value: str) -> QFrame:
        frame = QFrame()
        frame.setObjectName("statCard")
        box = QVBoxLayout(frame)
        label = QLabel(caption)
        label.setObjectName("statCaption")
        value_label = QLabel(value)
        value_label.setObjectName("statValue")
        box.addWidget(label)
        box.addWidget(value_label)
        frame.value_label = value_label  # type: ignore[attr-defined]
        return frame

    def _build_customers(self) -> None:
        page, layout = self._page("Customers", "Customer Khata")
        toolbar = QHBoxLayout()
        self.customer_search = QLineEdit()
        self.customer_search.setPlaceholderText("Filter customers by name, phone or tag…")
        self.customer_search.textChanged.connect(self.refresh_customers)
        toolbar.addWidget(self.customer_search, 1)
        toolbar.addWidget(self._button("Add customer", self.add_customer, True))
        toolbar.addWidget(self._button("Edit profile", self.edit_customer))
        toolbar.addWidget(self._button("Delete account", self.delete_customer))
        layout.addLayout(toolbar)
        split = QSplitter(Qt.Orientation.Horizontal)
        left = QWidget()
        ll = QVBoxLayout(left)
        self.customer_table = self._table(["ID", "Customer", "Phone", "Balance"])
        self.customer_table.setColumnHidden(0, True)
        self.customer_table.itemSelectionChanged.connect(self.refresh_ledger)
        ll.addWidget(self.customer_table)
        split.addWidget(left)

        right = QWidget()
        rl = QVBoxLayout(right)
        rl.addWidget(QLabel("Account activity"))
        self.ledger_table = self._table(["ID", "Date", "Type", "Description", "Amount"])
        self.ledger_table.setColumnHidden(0, True)
        rl.addWidget(self.ledger_table, 1)
        txbar = QHBoxLayout()
        txbar.addWidget(self._button("New credit", lambda: self.add_entry("credit"), True))
        txbar.addWidget(self._button("Receive payment", lambda: self.add_entry("payment")))
        self.edit_entry_button = self._button("Edit entry", self.edit_entry)
        self.delete_entry_button = self._button("Delete entry", self.delete_entry)
        txbar.addWidget(self.edit_entry_button)
        txbar.addWidget(self.delete_entry_button)
        rl.addLayout(txbar)
        profile = QGroupBox("Customer information")
        pf = QFormLayout(profile)
        self.customer_tags = QLineEdit()
        self.customer_notes = QTextEdit()
        self.customer_notes.setMaximumHeight(74)
        self.share_consent = QCheckBox("Customer has agreed to receive a statement")
        pf.addRow("Tags", self.customer_tags)
        pf.addRow("Notes", self.customer_notes)
        pf.addRow(self.share_consent)
        pf.addRow(self._button("Save information", self.save_customer_meta))
        rl.addWidget(profile)
        docs = QHBoxLayout()
        docs.addWidget(self._button("Save statement as PDF", self.statement_pdf))
        docs.addWidget(self._button("Print statement", self.print_statement))
        rl.addLayout(docs)
        split.addWidget(right)
        split.setSizes([430, 700])
        layout.addWidget(split, 1)
        self.customer_actions = [toolbar.itemAt(i).widget() for i in (2, 3)]
        self.customer_owner_actions = [rl.itemAt(i).widget() for i in []]
        self.customer_meta_group = profile
        self.customer_document_buttons = [docs.itemAt(i).widget() for i in range(2)]

    def _build_suppliers(self) -> None:
        page, layout = self._page("Suppliers", "Supplier Khata")
        layout.addWidget(QLabel("Track amounts owed to suppliers and payments made."))
        self.supplier_table = self._table(["ID", "Supplier", "Phone", "Balance payable"])
        self.supplier_table.setColumnHidden(0, True)
        layout.addWidget(self.supplier_table, 1)
        form = QGroupBox("New supplier / account entry")
        f = QFormLayout(form)
        self.supplier_name = QLineEdit()
        self.supplier_phone = QLineEdit()
        self.supplier_type = QComboBox()
        self.supplier_type.addItem("New amount payable", "payable")
        self.supplier_type.addItem("Payment made", "payment")
        self.supplier_amount = QDoubleSpinBox()
        self.supplier_amount.setRange(0, 1_000_000_000)
        self.supplier_amount.setDecimals(2)
        self.supplier_amount.setPrefix("Rs. ")
        self.supplier_desc = QLineEdit()
        f.addRow("Supplier name", self.supplier_name)
        f.addRow("Phone", self.supplier_phone)
        f.addRow("Entry type", self.supplier_type)
        f.addRow("Amount", self.supplier_amount)
        f.addRow("Description", self.supplier_desc)
        f.addRow(self._button("Save supplier entry", self.save_supplier, True))
        layout.addWidget(form)

    def _build_inventory(self) -> None:
        page, layout = self._page("Inventory", "Inventory")
        layout.addWidget(QLabel("Manage products, stock levels and customer product sales."))
        self.product_table = self._table(["ID", "Product", "SKU", "Purchase", "Selling", "Stock", "Reorder at"])
        self.product_table.setColumnHidden(0, True)
        layout.addWidget(self.product_table, 1)
        row = QHBoxLayout()
        self.product_add_button = self._button("Add product", self.add_product, True)
        self.product_edit_button = self._button("Edit product", self.edit_product)
        self.product_adjust_button = self._button("Adjust stock", self.adjust_stock)
        self.product_delete_button = self._button("Delete product", self.delete_product)
        for button in [self.product_add_button, self.product_edit_button,
                       self.product_adjust_button, self.product_delete_button]:
            row.addWidget(button)
        layout.addLayout(row)
        sale = QGroupBox("Record a product sale")
        sf = QFormLayout(sale)
        self.sale_customer = QComboBox()
        self.sale_product = QComboBox()
        self.sale_quantity = QDoubleSpinBox()
        self.sale_quantity.setRange(0.001, 1_000_000)
        self.sale_quantity.setDecimals(3)
        self.sale_quantity.setValue(1)
        sf.addRow("Customer", self.sale_customer)
        sf.addRow("Product", self.sale_product)
        sf.addRow("Quantity", self.sale_quantity)
        sf.addRow(self._button("Review and record sale", self.record_sale, True))
        layout.addWidget(sale)

    def _build_expenses(self) -> None:
        page, layout = self._page("Expenses", "Expenses")
        self.expense_table = self._table(["ID", "Date", "Category", "Description", "Amount"])
        self.expense_table.setColumnHidden(0, True)
        layout.addWidget(self.expense_table, 1)
        form = QGroupBox("Record a business expense")
        f = QFormLayout(form)
        self.expense_category = QComboBox()
        self.expense_category.addItems(["Rent", "Utilities", "Salary", "Transport", "Supplies", "Repairs", "Other"])
        self.expense_description = QLineEdit()
        self.expense_amount = QDoubleSpinBox()
        self.expense_amount.setRange(0, 1_000_000_000)
        self.expense_amount.setDecimals(2)
        self.expense_amount.setPrefix("Rs. ")
        f.addRow("Category", self.expense_category)
        f.addRow("Description", self.expense_description)
        f.addRow("Amount", self.expense_amount)
        buttons = QHBoxLayout()
        buttons.addWidget(self._button("Save expense", self.save_expense, True))
        buttons.addWidget(self._button("Delete selected", self.delete_expense))
        f.addRow(buttons)
        layout.addWidget(form)

    def _build_reports(self) -> None:
        page, layout = self._page("Reports", "Reports")
        filters = QHBoxLayout()
        self.report_month = QLineEdit(date.today().strftime("%Y-%m"))
        self.report_month.setMaximumWidth(120)
        filters.addWidget(QLabel("Reporting month (YYYY-MM)"))
        filters.addWidget(self.report_month)
        filters.addWidget(self._button("Refresh report", self.refresh_reports, True))
        filters.addStretch()
        layout.addLayout(filters)
        self.report_summary = QTextEdit()
        self.report_summary.setReadOnly(True)
        self.report_summary.setObjectName("reportPanel")
        layout.addWidget(self.report_summary, 1)
        self.integrity_button = self._button("Run database integrity check", self.run_integrity_check)
        layout.addWidget(self.integrity_button)

    def _build_ai(self) -> None:
        page, layout = self._page("AI Bookkeeper", "AI Bookkeeper")
        intro = QFrame()
        intro.setObjectName("subtlePanel")
        il = QVBoxLayout(intro)
        il.addWidget(QLabel("Ask questions about a specific customer ledger or prepare a transaction draft."))
        il.addWidget(QLabel("The assistant is read-only. It cannot write to the books until you review and approve a draft."))
        layout.addWidget(intro)
        context_row = QHBoxLayout()
        context_row.addWidget(QLabel("Customer context"))
        self.ai_customer = QComboBox()
        self.ai_customer.addItem("No customer selected", None)
        context_row.addWidget(self.ai_customer, 1)
        layout.addLayout(context_row)
        self.ai_conversation = QTextEdit()
        self.ai_conversation.setReadOnly(True)
        self.ai_conversation.setObjectName("conversation")
        self.ai_conversation.setPlaceholderText("Your bookkeeping conversation will appear here.")
        layout.addWidget(self.ai_conversation, 1)
        askrow = QHBoxLayout()
        self.ai_question = QLineEdit()
        self.ai_question.setPlaceholderText("Ask about a balance, recent activity or a transaction…")
        self.ai_question.returnPressed.connect(self.ask_ai)
        askrow.addWidget(self.ai_question, 1)
        self.ai_ask_button = self._button("Ask assistant", self.ask_ai, True)
        askrow.addWidget(self.ai_ask_button)
        layout.addLayout(askrow)
        scanrow = QHBoxLayout()
        scanrow.addWidget(QLabel("Handwritten ledger"))
        scanrow.addWidget(self._button("Choose photo and review extracted entries", self.scan_ledger))
        scanrow.addStretch()
        layout.addLayout(scanrow)
        self.ai_status = QLabel("Configure an AI service key in Settings to enable these optional tools.")
        layout.addWidget(self.ai_status)

    def _build_settings(self) -> None:
        page, layout = self._page("Settings", "Business settings")
        identity = QGroupBox("Business profile")
        ident = QFormLayout(identity)
        self.business_name_label = QLabel(self.shop_name)
        ident.addRow("Shop name", self.business_name_label)
        ident.addRow(self._button("Create another shop", self.create_shop))
        layout.addWidget(identity)
        access = QGroupBox("Security and staff access")
        af = QFormLayout(access)
        self.staff_pin_input = QLineEdit()
        self.staff_pin_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.staff_pin_input.setPlaceholderText("At least 6 digits")
        af.addRow("New staff PIN", self.staff_pin_input)
        af.addRow(self._button("Save staff PIN", self.save_staff_pin))
        layout.addWidget(access)
        business = QGroupBox("Business preferences")
        bf = QFormLayout(business)
        self.wallet_jazzcash = QLineEdit(self.store.setting("wallet_jazzcash"))
        self.wallet_easypaisa = QLineEdit(self.store.setting("wallet_easypaisa"))
        self.daily_time = QLineEdit(self.store.setting("daily_summary_time", "20:00"))
        self.reminders_enabled = QCheckBox("Show weekly overdue reminders")
        self.reminders_enabled.setChecked(self.store.setting("weekly_reminder_enabled", "1") == "1")
        self.theme_picker = QComboBox()
        self.theme_picker.addItems(["Liquid Glass", "Glassmorphism", "Soft UI"])
        self.theme_picker.setCurrentText(self.store.setting("theme", "Liquid Glass"))
        self.printer_com = QLineEdit(self.store.setting("printer_com"))
        bf.addRow("JazzCash number", self.wallet_jazzcash)
        bf.addRow("Easypaisa number", self.wallet_easypaisa)
        bf.addRow("Daily summary time", self.daily_time)
        bf.addRow(self.reminders_enabled)
        bf.addRow("Appearance", self.theme_picker)
        bf.addRow("Thermal printer COM port (optional)", self.printer_com)
        bf.addRow(self._button("Save preferences", self.save_preferences, True))
        layout.addWidget(business)
        ai = QGroupBox("Optional AI services")
        aif = QFormLayout(ai)
        self.ai_key_input = QLineEdit()
        self.ai_key_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.ai_key_input.setPlaceholderText("Paste one or more Gemini API keys, separated by semicolons")
        self.ai_models_input = QLineEdit(self.store.setting("ai_models", ",".join(DEFAULT_MODELS)))
        aif.addRow("Gemini API key", self.ai_key_input)
        aif.addRow("Model fallback order", self.ai_models_input)
        aif.addRow(self._button("Save protected AI key and models", self.save_ai_settings))
        aif.addRow(self._button("Remove saved AI key", self.remove_ai_key))
        layout.addWidget(ai)
        data = QGroupBox("Data protection")
        dl = QHBoxLayout(data)
        dl.addWidget(self._button("Export encrypted backup", self.export_backup, True))
        dl.addWidget(self._button("Restore encrypted backup", self.restore_backup))
        dl.addWidget(self._button("Open data folder", self.open_data_folder))
        layout.addWidget(data)
        layout.addStretch()

    # Navigation and shortcuts
    def _setup_shortcuts(self) -> None:
        shortcut = QShortcut(QKeySequence("Ctrl+N"), self)
        shortcut.activated.connect(self.add_customer)
        shortcut = QShortcut(QKeySequence("Ctrl+F"), self)
        shortcut.activated.connect(self.global_search.setFocus)
        shortcut = QShortcut(QKeySequence("Ctrl+Shift+S"), self)
        shortcut.activated.connect(self.export_backup)
        shortcut = QShortcut(QKeySequence("F5"), self)
        shortcut.activated.connect(self.refresh_all)

    def _apply_theme(self, theme: str) -> None:
        themes = {
            "Liquid Glass": """
                QWidget { background:#101827; color:#eaf0fa; font-family:'Segoe UI'; font-size:10pt; }
                #sidebar { background:#0b1220; border-right:1px solid #26364d; }
                #brand { color:#78c5ff; font-size:22pt; font-weight:800; letter-spacing:2px; }
                #pageHeading { font-size:20pt; font-weight:700; color:#f5f8ff; }
                #hero { background:qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #19375b,stop:1 #254f69); border:1px solid #41617d; border-radius:18px; }
                #heroTitle { font-size:22pt; font-weight:700; color:#ffffff; }
                #statCard, #subtlePanel, QGroupBox { background:#172337; border:1px solid #2d415b; border-radius:12px; padding:9px; }
                #statCaption { color:#9eb4cf; font-size:8pt; font-weight:700; }
                #statValue { color:#ffffff; font-size:19pt; font-weight:700; }
                #navButton { text-align:left; padding:12px 14px; border:0; border-radius:9px; color:#b9c9dc; background:transparent; }
                #navButton:checked { background:#203c5d; color:#8bd0ff; font-weight:700; }
                #primaryButton { background:#287dbb; color:white; border:0; border-radius:8px; font-weight:700; }
                QPushButton { background:#263750; border:1px solid #3a526f; border-radius:8px; padding:7px 12px; }
                QPushButton:hover { background:#334c6e; }
                QLineEdit, QTextEdit, QComboBox, QDoubleSpinBox, QDateEdit { background:#0f1a2a; border:1px solid #344964; border-radius:7px; padding:8px; selection-background-color:#287dbb; }
                QTableWidget { background:#121e2f; alternate-background-color:#17263b; gridline-color:#283c55; border:1px solid #2d405a; border-radius:9px; }
                QHeaderView::section { background:#1c2d43; color:#afc3db; border:0; padding:9px; font-weight:700; }
                QGroupBox { margin-top:12px; padding-top:14px; font-weight:700; }
                QGroupBox::title { subcontrol-origin:margin; left:12px; padding:0 5px; color:#8fcfff; }
                #summaryText { font-size:12pt; line-height:1.5; }
                #roleTag { color:#7fd4a5; font-weight:800; }
                #reportPanel, #conversation { background:#111d2e; border:1px solid #314862; border-radius:10px; padding:14px; }
            """,
            "Glassmorphism": """
                QWidget { background:#e9f0f8; color:#1d2d40; font-family:'Segoe UI'; font-size:10pt; }
                #sidebar { background:#d7e5f3; border-right:1px solid #b7c9dc; }
                #brand { color:#1c6097; font-size:22pt; font-weight:800; letter-spacing:2px; }
                #pageHeading { font-size:20pt; font-weight:700; color:#1b324b; }
                #hero { background:qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #3f83b7,stop:1 #65aaa8); border:1px solid #8ab7d0; border-radius:18px; color:white; }
                #heroTitle { font-size:22pt; font-weight:700; color:white; }
                #statCard, #subtlePanel, QGroupBox { background:#f7fbff; border:1px solid #cbd9e8; border-radius:12px; padding:9px; }
                #statCaption { color:#66809a; font-size:8pt; font-weight:700; }
                #statValue { color:#1c3854; font-size:19pt; font-weight:700; }
                #navButton { text-align:left; padding:12px 14px; border:0; border-radius:9px; color:#37536f; background:transparent; }
                #navButton:checked { background:#c0d9ec; color:#155f97; font-weight:700; }
                #primaryButton { background:#287dbb; color:white; border:0; border-radius:8px; font-weight:700; }
                QPushButton { background:#f8fbff; border:1px solid #bdcfdf; border-radius:8px; padding:7px 12px; }
                QPushButton:hover { background:#dceaf5; }
                QLineEdit, QTextEdit, QComboBox, QDoubleSpinBox, QDateEdit { background:white; border:1px solid #bfd0e0; border-radius:7px; padding:8px; }
                QTableWidget { background:white; alternate-background-color:#f0f6fb; gridline-color:#d9e3ec; border:1px solid #c7d6e4; border-radius:9px; }
                QHeaderView::section { background:#dfeaf4; color:#36536d; border:0; padding:9px; font-weight:700; }
                QGroupBox { margin-top:12px; padding-top:14px; font-weight:700; }
                QGroupBox::title { subcontrol-origin:margin; left:12px; padding:0 5px; color:#286b9c; }
                #roleTag { color:#26734d; font-weight:800; }
                #reportPanel, #conversation { background:white; border:1px solid #cbd9e8; border-radius:10px; padding:14px; }
            """,
            "Soft UI": """
                QWidget { background:#e7e9ef; color:#343b4b; font-family:'Segoe UI'; font-size:10pt; }
                #sidebar { background:#dfe2e9; border-right:1px solid #d1d5df; }
                #brand { color:#5c6b8a; font-size:22pt; font-weight:800; letter-spacing:2px; }
                #pageHeading { font-size:20pt; font-weight:700; color:#39465d; }
                #hero { background:#dce4f0; border:1px solid #cbd4e2; border-radius:18px; }
                #heroTitle { font-size:22pt; font-weight:700; color:#354b6a; }
                #statCard, #subtlePanel, QGroupBox { background:#eef0f5; border:1px solid #d4d8e2; border-radius:14px; padding:9px; }
                #statCaption { color:#758099; font-size:8pt; font-weight:700; }
                #statValue { color:#3e4c67; font-size:19pt; font-weight:700; }
                #navButton { text-align:left; padding:12px 14px; border:0; border-radius:9px; color:#59657c; background:transparent; }
                #navButton:checked { background:#cbd3e1; color:#344c71; font-weight:700; }
                #primaryButton { background:#6f86aa; color:white; border:0; border-radius:8px; font-weight:700; }
                QPushButton { background:#e9ebf1; border:1px solid #d1d6e0; border-radius:8px; padding:7px 12px; }
                QPushButton:hover { background:#dce1eb; }
                QLineEdit, QTextEdit, QComboBox, QDoubleSpinBox, QDateEdit { background:#f4f5f8; border:1px solid #d2d7e0; border-radius:7px; padding:8px; }
                QTableWidget { background:#f1f2f6; alternate-background-color:#e9ecf2; gridline-color:#d8dce5; border:1px solid #d0d5e0; border-radius:9px; }
                QHeaderView::section { background:#dfe3eb; color:#4c5c79; border:0; padding:9px; font-weight:700; }
                QGroupBox { margin-top:12px; padding-top:14px; font-weight:700; }
                QGroupBox::title { subcontrol-origin:margin; left:12px; padding:0 5px; color:#65799e; }
                #roleTag { color:#397553; font-weight:800; }
                #reportPanel, #conversation { background:#f1f2f6; border:1px solid #d0d5e0; border-radius:10px; padding:14px; }
            """
        }
        self.setStyleSheet(themes.get(theme, themes["Liquid Glass"]))

    def _apply_access_rules(self) -> None:
        owner_only = ["Suppliers", "Expenses", "Reports", "AI Bookkeeper", "Settings"]
        for key, button in self.nav_buttons.items():
            button.setVisible(not (self.staff_mode and key in owner_only))
        self.role_label.setText("STAFF ACCESS" if self.staff_mode else "OWNER ACCESS")
        for button in getattr(self, "customer_actions", []):
            button.setVisible(not self.staff_mode)
        if hasattr(self, "customer_meta_group"):
            self.customer_meta_group.setVisible(not self.staff_mode)
        if hasattr(self, "edit_entry_button"):
            self.edit_entry_button.setVisible(not self.staff_mode)
            self.delete_entry_button.setVisible(not self.staff_mode)
        if hasattr(self, "dashboard_scan_button"):
            self.dashboard_scan_button.setVisible(not self.staff_mode)
        for button in getattr(self, "customer_document_buttons", []):
            button.setVisible(not self.staff_mode)
        for button in [getattr(self, "product_add_button", None),
                       getattr(self, "product_edit_button", None),
                       getattr(self, "product_adjust_button", None),
                       getattr(self, "product_delete_button", None)]:
            if button:
                button.setVisible(not self.staff_mode)
        if hasattr(self, "ledger_table"):
            self.ledger_table.setColumnHidden(0, True)

    def _setup_notifications(self) -> None:
        self.tray = QSystemTrayIcon(QApplication.style().standardIcon(QStyle.StandardPixmap.SP_ComputerIcon), self)
        self.tray.setToolTip(APP_NAME)
        self.tray.show()
        self.notification_timer = QTimer(self)
        self.notification_timer.timeout.connect(self.check_scheduled_notifications)
        self.notification_timer.start(60_000)

    # Data refresh methods
    def refresh_all(self) -> None:
        self.refresh_dashboard()
        self.refresh_customers()
        self.refresh_suppliers()
        self.refresh_products()
        self.refresh_expenses()
        self.refresh_reports()
        self.refresh_customer_combos()
        self.refresh_ai_customer_combo()

    def refresh_dashboard(self) -> None:
        if not hasattr(self, "stat_customers"):
            return
        customers = self.store.customers()
        due = sum(max(0, int(r["balance_paisa"])) for r in customers)
        daily = self.store.daily_summary()
        self.stat_customers.value_label.setText(str(len(customers)))
        self.stat_due.value_label.setText(money(due))
        self.stat_sales.value_label.setText(money(daily["sales_paisa"]))
        self.stat_receipts.value_label.setText(money(daily["receipts_paisa"]))
        self.recent_customers.setRowCount(0)
        for row in customers[:8]:
            index = self.recent_customers.rowCount()
            self.recent_customers.insertRow(index)
            for col, value in enumerate([row["name"], row["phone"], money(row["balance_paisa"])]):
                self.recent_customers.setItem(index, col, QTableWidgetItem(str(value)))
        overdue = self.store.overdue_customers(30)
        overdue_total = sum(item["overdue_paisa"] for item in overdue)
        self.daily_summary_label.setText(
            f"Credit sales today\n{money(daily['sales_paisa'])}\n\n"
            f"Payments received\n{money(daily['receipts_paisa'])}\n\n"
            f"Net due movement\n{money(daily['net_due_change_paisa'])}"
        )
        self.overdue_label.setText(
            f"Overdue accounts (30+ days): {len(overdue)}\n"
            f"Overdue amount: {money(overdue_total)}"
        )

    def refresh_customers(self) -> None:
        if not hasattr(self, "customer_table"):
            return
        selected = self.current_customer_id
        rows = self.store.customers(self.customer_search.text() if hasattr(self, "customer_search") else "")
        self.customer_table.setRowCount(0)
        new_row = -1
        for row in rows:
            idx = self.customer_table.rowCount()
            self.customer_table.insertRow(idx)
            values = [row["id"], row["name"], row["phone"], money(row["balance_paisa"])]
            for col, value in enumerate(values):
                self.customer_table.setItem(idx, col, QTableWidgetItem(str(value)))
            if row["id"] == selected:
                new_row = idx
        if new_row >= 0:
            self.customer_table.selectRow(new_row)
        elif self.customer_table.rowCount():
            self.customer_table.selectRow(0)
        else:
            self.current_customer_id = None
            self.ledger_table.setRowCount(0)

    def refresh_ledger(self) -> None:
        if not hasattr(self, "ledger_table"):
            return
        row_index = self.customer_table.currentRow()
        if row_index < 0:
            return
        item = self.customer_table.item(row_index, 0)
        if not item:
            return
        self.current_customer_id = int(item.text())
        rows = self.store.customer_transactions(self.current_customer_id)
        self.ledger_table.setRowCount(0)
        for row in rows:
            idx = self.ledger_table.rowCount()
            self.ledger_table.insertRow(idx)
            values = [row["id"], row["transaction_date"], row["transaction_type"].title(),
                      row["description"], money(row["amount_paisa"])]
            for col, value in enumerate(values):
                self.ledger_table.setItem(idx, col, QTableWidgetItem(str(value)))
        meta = self.store.db.execute(
            "SELECT tags,notes,share_opt_in FROM customer_meta WHERE customer_id=?",
            (self.current_customer_id,)
        ).fetchone()
        self.customer_tags.setText(meta["tags"] if meta else "")
        self.customer_notes.setPlainText(meta["notes"] if meta else "")
        self.share_consent.setChecked(bool(meta["share_opt_in"]) if meta else False)

    def refresh_suppliers(self) -> None:
        if not hasattr(self, "supplier_table"):
            return
        rows = self.store.suppliers()
        self.supplier_table.setRowCount(0)
        for row in rows:
            idx = self.supplier_table.rowCount()
            self.supplier_table.insertRow(idx)
            for col, value in enumerate([row["id"], row["name"], row["phone"], money(row["balance_paisa"])]):
                self.supplier_table.setItem(idx, col, QTableWidgetItem(str(value)))

    def refresh_products(self) -> None:
        if not hasattr(self, "product_table"):
            return
        rows = self.store.products()
        self.product_table.setRowCount(0)
        for row in rows:
            idx = self.product_table.rowCount()
            self.product_table.insertRow(idx)
            values = [row["id"], row["name"], row["sku"], money(row["purchase_price_paisa"]),
                      money(row["selling_price_paisa"]), f"{row['stock_milli']/1000:g}",
                      f"{row['low_stock_milli']/1000:g}"]
            for col, value in enumerate(values):
                self.product_table.setItem(idx, col, QTableWidgetItem(str(value)))
        self.product_table.setColumnHidden(3, self.staff_mode)
        self.refresh_customer_combos()

    def refresh_expenses(self) -> None:
        if not hasattr(self, "expense_table"):
            return
        rows = self.store.expenses()
        self.expense_table.setRowCount(0)
        for row in rows:
            idx = self.expense_table.rowCount()
            self.expense_table.insertRow(idx)
            for col, value in enumerate([row["id"], row["expense_date"], row["category"],
                                         row["description"], money(row["amount_paisa"])]):
                self.expense_table.setItem(idx, col, QTableWidgetItem(str(value)))

    def refresh_reports(self) -> None:
        if not hasattr(self, "report_summary") or self.staff_mode:
            return
        month = self.report_month.text().strip() if hasattr(self, "report_month") else date.today().strftime("%Y-%m")
        if not re.fullmatch(r"\d{4}-\d{2}", month):
            self.report_summary.setPlainText("Enter the reporting month in YYYY-MM format.")
            return
        report = self.store.profit_loss(month)
        daily = self.store.daily_summary()
        product_count = self.store.db.execute("SELECT COUNT(*) FROM products").fetchone()[0]
        low_stock = self.store.db.execute(
            "SELECT COUNT(*) FROM products WHERE stock_milli<=low_stock_milli").fetchone()[0]
        self.report_summary.setHtml(
            f"<h2>Business performance · {month}</h2>"
            f"<h3>Sales and profitability</h3>"
            f"<p>Tracked credit revenue: <b>{money(report['revenue_paisa'])}</b></p>"
            f"<p>Cost of goods sold: <b>{money(report['cogs_paisa'])}</b></p>"
            f"<p>Gross profit: <b>{money(report['gross_profit_paisa'])}</b></p>"
            f"<p>Operating expenses: <b>{money(report['expenses_paisa'])}</b></p>"
            f"<p><b>Estimated net profit: {money(report['net_estimate_paisa'])}</b></p>"
            f"<h3>Operations</h3><p>Current inventory value: {money(report['inventory_value_paisa'])}</p>"
            f"<p>Products tracked: {product_count} · Low-stock products: {low_stock}</p>"
            f"<p>Today's new credit: {money(daily['sales_paisa'])} · "
            f"Payments received: {money(daily['receipts_paisa'])}</p>"
            "<hr><p>Profit estimates use saved product-cost snapshots and recorded expenses. "
            "Manual credit entries without product cost data are excluded from cost-of-goods calculations. "
            "Customer repayments settle balances and are not counted as new sales.</p>"
        )

    def refresh_customer_combos(self) -> None:
        if not hasattr(self, "sale_customer"):
            return
        current = self.sale_customer.currentData()
        self.sale_customer.clear()
        for customer in self.store.customers():
            self.sale_customer.addItem(customer["name"], customer["id"])
        if current is not None:
            idx = self.sale_customer.findData(current)
            if idx >= 0:
                self.sale_customer.setCurrentIndex(idx)
        self.sale_product.clear()
        for product in self.store.products():
            self.sale_product.addItem(product["name"], product["id"])

    def refresh_ai_customer_combo(self) -> None:
        if not hasattr(self, "ai_customer") or self.staff_mode:
            return
        current = self.ai_customer.currentData()
        self.ai_customer.clear()
        self.ai_customer.addItem("No customer selected", None)
        for customer in self.store.customers():
            self.ai_customer.addItem(customer["name"], customer["id"])
        if current is not None:
            idx = self.ai_customer.findData(current)
            if idx >= 0:
                self.ai_customer.setCurrentIndex(idx)

    # Customer workflows
    def add_customer(self) -> None:
        name = ask_text(self, "New customer", "Customer name")
        if name is None:
            return
        phone = ask_text(self, "New customer", "Phone number") or ""
        try:
            self.current_customer_id = self.store.add_customer(name, phone)
            self.refresh_all()
            self.navigate("Customers")
        except Exception as exc:
            inform(self, str(exc), True)

    def edit_customer(self) -> None:
        if self.staff_mode:
            return
        cid = self.current_customer_id
        customer = self.store.customer(cid) if cid else None
        if not customer:
            inform(self, "Select a customer first.")
            return
        name = ask_text(self, "Edit customer", "Customer name", default=customer["name"])
        if name is None:
            return
        phone = ask_text(self, "Edit customer", "Phone number", default=customer["phone"])
        if phone is None:
            return
        try:
            self.store.update_customer(cid, name, phone)
            self.refresh_all()
        except Exception as exc:
            inform(self, str(exc), True)

    def delete_customer(self) -> None:
        if self.staff_mode:
            return
        cid = self.current_customer_id
        customer = self.store.customer(cid) if cid else None
        if not customer:
            inform(self, "Select a customer first.")
            return
        if confirm(self, f"Delete {customer['name']} and all their ledger entries? Product stock from linked sales will be restored."):
            try:
                self.store.delete_customer(cid)
                self.current_customer_id = None
                self.refresh_all()
            except Exception as exc:
                inform(self, str(exc), True)

    def add_entry(self, kind: str) -> None:
        cid = self.current_customer_id
        if not cid:
            inform(self, "Select a customer first.")
            self.navigate("Customers")
            return
        amount, accepted = QInputDialog.getDouble(self, "Ledger entry",
            "Amount (Rs.)", 0.0, 0.01, 100_000_000, 2)
        if not accepted:
            return
        description = ask_text(self, "Ledger entry", "Description") or ""
        try:
            self.store.add_transaction(cid, kind, amount, description)
            self.refresh_all()
            self.navigate("Customers")
        except Exception as exc:
            inform(self, str(exc), True)

    def edit_entry(self) -> None:
        if self.staff_mode:
            return
        row = self.ledger_table.currentRow()
        if row < 0:
            inform(self, "Select a ledger entry first.")
            return
        txid = int(self.ledger_table.item(row, 0).text())
        record = self.store.db.execute("SELECT * FROM transactions WHERE id=?", (txid,)).fetchone()
        if not record:
            return
        if record["product_id"] is not None:
            inform(self, "Product-sale records are kept as stock and cost snapshots. Delete and re-record a correction instead.")
            return
        kind, ok = QInputDialog.getItem(self, "Edit entry", "Entry type",
            ["Credit", "Payment"], 0 if record["transaction_type"] == "credit" else 1, False)
        if not ok:
            return
        amount, ok = QInputDialog.getDouble(self, "Edit entry", "Amount (Rs.)",
            record["amount_paisa"] / 100, 0.01, 100_000_000, 2)
        if not ok:
            return
        desc = ask_text(self, "Edit entry", "Description", default=record["description"])
        if desc is None:
            return
        try:
            self.store.edit_transaction(txid, kind.lower(), amount, desc, record["transaction_date"])
            self.refresh_all()
        except Exception as exc:
            inform(self, str(exc), True)

    def delete_entry(self) -> None:
        if self.staff_mode:
            return
        row = self.ledger_table.currentRow()
        if row < 0:
            inform(self, "Select a ledger entry first.")
            return
        txid = int(self.ledger_table.item(row, 0).text())
        if confirm(self, "Delete this ledger entry? If it was a product sale, the stock quantity will be returned."):
            try:
                self.store.delete_transaction(txid)
                self.refresh_all()
            except Exception as exc:
                inform(self, str(exc), True)

    def save_customer_meta(self) -> None:
        if self.staff_mode or not self.current_customer_id:
            return
        try:
            self.store.save_customer_meta(self.current_customer_id, self.customer_tags.text(),
                self.customer_notes.toPlainText(), self.share_consent.isChecked())
            inform(self, "Customer information saved.")
            self.refresh_customers()
        except Exception as exc:
            inform(self, str(exc), True)

    def _statement_text(self, customer_id: int) -> tuple[str, list[Any]]:
        customer = self.store.customer(customer_id)
        if not customer:
            raise ValueError("Customer no longer exists.")
        rows = self.store.db.execute(
            "SELECT * FROM transactions WHERE customer_id=? ORDER BY transaction_date,id",
            (customer_id,)
        ).fetchall()
        balance = 0
        lines = []
        for row in rows:
            balance += row["amount_paisa"] if row["transaction_type"] == "credit" else -row["amount_paisa"]
            lines.append(row)
        return customer["name"], lines

    def _check_statement_consent(self, cid: int) -> bool:
        meta = self.store.db.execute(
            "SELECT share_opt_in FROM customer_meta WHERE customer_id=?", (cid,)
        ).fetchone()
        if not meta or not meta["share_opt_in"]:
            inform(self, "This customer has not granted permission for statement sharing. Record consent in the customer profile first.", True)
            return False
        return True

    def statement_pdf(self) -> None:
        if self.staff_mode:
            return
        cid = self.current_customer_id
        if not cid or not self._check_statement_consent(cid):
            return
        customer = self.store.customer(cid)
        path, _ = QFileDialog.getSaveFileName(self, "Save customer statement",
            f"{customer['name']}-statement.pdf", "PDF files (*.pdf)")
        if not path:
            return
        writer = QPdfWriter(path)
        writer.setPageSize(QPageSize(QPageSize.PageSizeId.A4))
        writer.setResolution(96)
        painter = QPainter(writer)
        try:
            painter.setFont(QFont("Segoe UI", 18, QFont.Weight.Bold))
            painter.drawText(55, 70, APP_NAME)
            painter.setFont(QFont("Segoe UI", 10))
            painter.drawText(55, 105, f"Customer statement · {customer['name']} · {customer['phone']}")
            painter.drawText(55, 130, f"JazzCash: {self.store.setting('wallet_jazzcash','—')}    Easypaisa: {self.store.setting('wallet_easypaisa','—')}")
            y = 185
            painter.setFont(QFont("Segoe UI", 9, QFont.Weight.Bold))
            painter.drawText(55, y, "DATE")
            painter.drawText(170, y, "TYPE")
            painter.drawText(280, y, "DESCRIPTION")
            painter.drawText(650, y, "AMOUNT")
            y += 25
            painter.setFont(QFont("Segoe UI", 9))
            balance = 0
            for row in self.store.db.execute(
                "SELECT * FROM transactions WHERE customer_id=? ORDER BY transaction_date,id", (cid,)
            ):
                delta = row["amount_paisa"] if row["transaction_type"] == "credit" else -row["amount_paisa"]
                balance += delta
                painter.drawText(55, y, row["transaction_date"])
                painter.drawText(170, y, row["transaction_type"].title())
                painter.drawText(280, y, row["description"][:55])
                painter.drawText(650, y, money(row["amount_paisa"]))
                y += 22
                if y > writer.height() - 100:
                    writer.newPage()
                    y = 75
            painter.drawText(55, y + 22, f"Outstanding balance: {money(balance)}")
        finally:
            painter.end()
        inform(self, "The customer statement PDF has been saved.")

    def print_statement(self) -> None:
        if self.staff_mode:
            return
        cid = self.current_customer_id
        if not cid:
            inform(self, "Select a customer first.")
            return
        if not self._check_statement_consent(cid):
            return
        customer = self.store.customer(cid)
        com_port = self.store.setting("printer_com").strip()
        if com_port:
            try:
                import serial
                lines = [APP_NAME, f"Statement: {customer['name']}", customer["phone"],
                         "-" * 32, "DATE       TYPE     AMOUNT"]
                for row in self.store.db.execute(
                    "SELECT * FROM transactions WHERE customer_id=? ORDER BY transaction_date,id", (cid,)
                ):
                    lines.append(f"{row['transaction_date']} {row['transaction_type'][:7]} {row['amount_paisa']/100:.2f}")
                    lines.append(row["description"][:30])
                lines.extend([ "-" * 32,
                    f"JazzCash: {self.store.setting('wallet_jazzcash','—')}",
                    f"Easypaisa: {self.store.setting('wallet_easypaisa','—')}", "", "", ""])
                with serial.Serial(com_port, 9600, timeout=3, write_timeout=3) as port:
                    port.write(b"\x1b\x40")
                    port.write(("\n".join(lines)).encode("cp437", "replace"))
                    port.write(b"\n\n\n\x1d\x56\x00")
                inform(self, f"Statement sent to thermal printer on {com_port}.")
                return
            except Exception as exc:
                inform(self, f"Could not print through {com_port}: {exc}", True)
                return
        document = QTextDocument()
        html = (f"<h2>{APP_NAME} — Customer Statement</h2>"
                f"<p><b>{customer['name']}</b> · {customer['phone']}</p>"
                f"<p>JazzCash: {self.store.setting('wallet_jazzcash','—')} · "
                f"Easypaisa: {self.store.setting('wallet_easypaisa','—')}</p>"
                "<table width='100%' border='1' cellspacing='0' cellpadding='5'>"
                "<tr><th>Date</th><th>Type</th><th>Description</th><th>Amount</th></tr>")
        from html import escape
        for row in self.store.db.execute(
            "SELECT * FROM transactions WHERE customer_id=? ORDER BY transaction_date,id", (cid,)
        ):
            html += (f"<tr><td>{escape(row['transaction_date'])}</td>"
                     f"<td>{escape(row['transaction_type'])}</td>"
                     f"<td>{escape(row['description'])}</td>"
                     f"<td>{money(row['amount_paisa'])}</td></tr>")
        html += "</table>"
        document.setHtml(html)
        printer = QPrinter(QPrinter.PrinterMode.HighResolution)
        dialog = QPrintDialog(printer, self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            document.print_(printer)

    # Suppliers and stock
    def save_supplier(self) -> None:
        if self.staff_mode:
            return
        name = self.supplier_name.text().strip()
        if not name:
            inform(self, "Enter the supplier name.", True)
            return
        try:
            existing = next((row for row in self.store.suppliers()
                             if row["name"].casefold() == name.casefold()), None)
            supplier_id = existing["id"] if existing else self.store.add_supplier(name, self.supplier_phone.text())
            if self.supplier_amount.value() > 0:
                self.store.add_supplier_entry(supplier_id, self.supplier_type.currentData(),
                    self.supplier_amount.value(), self.supplier_desc.text())
            self.supplier_name.clear()
            self.supplier_phone.clear()
            self.supplier_amount.setValue(0)
            self.supplier_desc.clear()
            self.refresh_all()
        except Exception as exc:
            inform(self, str(exc), True)

    def _selected_product(self) -> int | None:
        row = self.product_table.currentRow()
        return int(self.product_table.item(row, 0).text()) if row >= 0 else None

    def add_product(self) -> None:
        if self.staff_mode:
            return
        name = ask_text(self, "New product", "Product name")
        if not name:
            return
        sku = ask_text(self, "New product", "SKU / code") or ""
        unit = ask_text(self, "New product", "Unit (pcs, kg, litre…)") or "pcs"
        buy, ok = QInputDialog.getDouble(self, "New product", "Purchase price (Rs.)", 0, 0, 100_000_000, 2)
        if not ok:
            return
        sell, ok = QInputDialog.getDouble(self, "New product", "Selling price (Rs.)", 0, 0.01, 100_000_000, 2)
        if not ok:
            return
        stock, ok = QInputDialog.getDouble(self, "New product", "Opening stock", 0, 0, 1_000_000, 3)
        if not ok:
            return
        low, ok = QInputDialog.getDouble(self, "New product", "Low-stock threshold", 5, 0, 1_000_000, 3)
        if not ok:
            return
        try:
            self.store.add_product(name, sku, unit, buy, sell, stock, low)
            self.refresh_all()
        except Exception as exc:
            inform(self, str(exc), True)

    def edit_product(self) -> None:
        if self.staff_mode:
            return
        pid = self._selected_product()
        product = self.store.db.execute("SELECT * FROM products WHERE id=?", (pid,)).fetchone() if pid else None
        if not product:
            inform(self, "Select a product first.")
            return
        name = ask_text(self, "Edit product", "Product name", default=product["name"])
        if name is None:
            return
        sku = ask_text(self, "Edit product", "SKU", default=product["sku"])
        if sku is None:
            return
        unit = ask_text(self, "Edit product", "Unit", default=product["unit"])
        if unit is None:
            return
        buy, ok = QInputDialog.getDouble(self, "Edit product", "Purchase price",
            product["purchase_price_paisa"]/100, 0, 100_000_000, 2)
        if not ok:
            return
        sell, ok = QInputDialog.getDouble(self, "Edit product", "Selling price",
            product["selling_price_paisa"]/100, 0.01, 100_000_000, 2)
        if not ok:
            return
        low, ok = QInputDialog.getDouble(self, "Edit product", "Low-stock threshold",
            product["low_stock_milli"]/1000, 0, 1_000_000, 3)
        if not ok:
            return
        try:
            self.store.update_product(pid, name, sku, unit, buy, sell, low)
            self.refresh_all()
        except Exception as exc:
            inform(self, str(exc), True)

    def adjust_stock(self) -> None:
        if self.staff_mode:
            return
        pid = self._selected_product()
        if not pid:
            inform(self, "Select a product first.")
            return
        delta, ok = QInputDialog.getDouble(self, "Adjust stock",
            "Enter positive quantity to add or negative quantity to remove", 0, -1_000_000, 1_000_000, 3)
        if ok:
            try:
                self.store.adjust_stock(pid, delta)
                self.refresh_all()
            except Exception as exc:
                inform(self, str(exc), True)

    def delete_product(self) -> None:
        if self.staff_mode:
            return
        pid = self._selected_product()
        if not pid:
            inform(self, "Select a product first.")
            return
        if confirm(self, "Remove this product from the active inventory? Historical sale details will remain in the ledger."):
            self.store.delete_product(pid)
            self.refresh_all()

    def record_sale(self) -> None:
        customer_id = self.sale_customer.currentData()
        product_id = self.sale_product.currentData()
        if customer_id is None or product_id is None:
            inform(self, "Add at least one customer and one product before recording a sale.", True)
            return
        product = self.store.db.execute("SELECT * FROM products WHERE id=?", (product_id,)).fetchone()
        qty = self.sale_quantity.value()
        total = round(qty * product["selling_price_paisa"])
        if not confirm(self, f"Record {qty:g} {product['unit']} of {product['name']} as customer credit for {money(total)}? Stock will be reduced."):
            return
        try:
            self.store.record_product_sale(int(customer_id), int(product_id), qty)
            self.refresh_all()
            inform(self, "Sale recorded and stock updated.")
        except Exception as exc:
            inform(self, str(exc), True)

    # Expenses and reports
    def save_expense(self) -> None:
        if self.staff_mode:
            return
        try:
            self.store.add_expense(self.expense_category.currentText(),
                self.expense_description.text(), self.expense_amount.value())
            self.expense_description.clear()
            self.expense_amount.setValue(0)
            self.refresh_all()
        except Exception as exc:
            inform(self, str(exc), True)

    def delete_expense(self) -> None:
        if self.staff_mode:
            return
        row = self.expense_table.currentRow()
        if row < 0:
            inform(self, "Select an expense first.")
            return
        expense_id = int(self.expense_table.item(row, 0).text())
        if confirm(self, "Delete this expense entry?"):
            self.store.delete_expense(expense_id)
            self.refresh_all()

    def run_integrity_check(self) -> None:
        if self.staff_mode:
            return
        ok, message = self.store.integrity_check()
        inform(self, message, not ok)

    # AI bookkeeper and photo review
    def _ai_credentials(self) -> tuple[list[str], tuple[str, ...]]:
        if self.staff_mode:
            raise ValueError("AI features are available to the shop owner only.")
        try:
            encrypted = json.loads(self.store.setting("ai_keys", "[]"))
        except json.JSONDecodeError:
            encrypted = []
        if not encrypted:
            legacy = self.store.setting("ai_key")
            encrypted = [legacy] if legacy else []
        keys = [reveal_api_key(item) for item in encrypted if item]
        if not keys:
            raise ValueError("Add one or more Gemini API keys in Settings to use the assistant.")
        models = tuple(x.strip() for x in self.store.setting("ai_models", ",".join(DEFAULT_MODELS)).split(",") if x.strip())
        return keys, models or DEFAULT_MODELS

    def _selected_ai_context(self) -> dict[str, Any]:
        cid = self.ai_customer.currentData()
        if cid is None:
            return {"customer": None, "notice": "No customer ledger was supplied. Do not infer private ledger facts."}
        customer = self.store.customer(int(cid))
        if not customer:
            return {"customer": None}
        rows = self.store.customer_transactions(int(cid))[:20]
        return {
            "customer": {"customer_id": int(cid), "name": customer["name"], "phone": customer["phone"],
                         "balance_paisa": self.store.customer_balance(int(cid))},
            "recent_transactions": [
                {"date": row["transaction_date"], "type": row["transaction_type"],
                 "description": row["description"], "amount_paisa": row["amount_paisa"]}
                for row in rows
            ]
        }

    def ask_ai(self) -> None:
        question = self.ai_question.text().strip()
        if not question:
            return
        try:
            key, models = self._ai_credentials()
            context = self._selected_ai_context()
        except Exception as exc:
            inform(self, str(exc), True)
            return
        self.ai_question.clear()
        from html import escape
        self.ai_conversation.append(f"<p><b>You</b><br>{escape(question).replace(chr(10), '<br>')}</p>")
        self.ai_status.setText("Preparing an answer…")
        self.ai_ask_button.setEnabled(False)
        job = BackgroundJob(lambda: ask_bookkeeper(key, question, context, models))
        job.signals.completed.connect(lambda result: self._ai_answer_ready(result, context))
        job.signals.failed.connect(self._ai_failed)
        self.pool.start(job)

    def _ai_answer_ready(self, result: dict[str, Any], context: dict[str, Any]) -> None:
        self.ai_ask_button.setEnabled(True)
        self.ai_status.setText("Answer received. Ledger changes still require your approval.")
        from html import escape
        self.ai_conversation.append(f"<p><b>Bookkeeper</b><br>{escape(result['answer']).replace(chr(10), '<br>')}</p>")
        draft = result.get("draft")
        if not draft:
            return
        selected = context.get("customer") or {}
        customer_id = draft.get("customer_id") or selected.get("customer_id")
        try:
            customer_id = int(customer_id) if customer_id is not None else None
        except (TypeError, ValueError):
            customer_id = None
        if customer_id is None or (selected and customer_id != int(selected["customer_id"])):
            self.ai_conversation.append("<p><i>Draft not offered: select the customer whose ledger should receive the entry.</i></p>")
            return
        customer = self.store.customer(customer_id)
        if not customer:
            return
        text = (f"Customer: {customer['name']}\nType: {draft['type']}\n"
                f"Amount: {money(round(draft['amount']*100))}\nDescription: {draft.get('description','')}\n\n"
                "Save this transaction to the local ledger?")
        if confirm(self, text):
            try:
                tx_date = draft.get("date") or iso_today()
                datetime.strptime(tx_date, "%Y-%m-%d")
                self.store.add_transaction(customer_id, draft["type"],
                    float(draft["amount"]), str(draft.get("description", "")), tx_date)
                self.ai_conversation.append("<p><b>Saved:</b> The approved entry has been added to the selected ledger.</p>")
                self.refresh_all()
            except Exception as exc:
                inform(self, f"Draft was not saved: {exc}", True)
        else:
            self.ai_conversation.append("<p>Draft discarded. No ledger change was made.</p>")

    def _ai_failed(self, message: str) -> None:
        self.ai_ask_button.setEnabled(True)
        self.ai_status.setText("The AI request did not complete.")
        inform(self, message, True)

    def scan_ledger(self) -> None:
        if self.staff_mode:
            return
        path, _ = QFileDialog.getOpenFileName(self, "Choose a handwritten ledger photo", "",
            "Images (*.jpg *.jpeg *.png *.webp)")
        if not path:
            return
        try:
            key, models = self._ai_credentials()
        except Exception as exc:
            inform(self, str(exc), True)
            return
        self.ai_status.setText("Reading the image. No entries will be saved until you review them.")
        job = BackgroundJob(lambda: read_ledger_photo(key, path, models))
        job.signals.completed.connect(self._scan_ready)
        job.signals.failed.connect(lambda message: (self.ai_status.setText("Image review failed."), inform(self, message, True)))
        self.pool.start(job)

    def _scan_ready(self, result: dict[str, Any]) -> None:
        entries = result.get("entries", [])
        self.ai_status.setText(f"Found {len(entries)} possible entries. Review each row before saving.")
        if not entries:
            inform(self, "No ledger entries could be extracted from this image.")
            return
        dialog = ScanReviewDialog(entries, self)
        if dialog.exec() != QDialog.DialogCode.Accepted:
            inform(self, "The scan was discarded. No records were created.")
            return
        approved = dialog.approved_entries()
        if not approved:
            inform(self, "No entries were selected for saving.")
            return
        saved, unresolved = 0, []
        for entry in approved:
            customer = next((c for c in self.store.customers()
                if c["name"].casefold() == entry["customer_name"].casefold()), None)
            if not customer:
                unresolved.append(entry["customer_name"] or "(blank customer)")
                continue
            try:
                datetime.strptime(entry["date"], "%Y-%m-%d")
                self.store.add_transaction(customer["id"], entry["type"], entry["amount"],
                    entry["description"], entry["date"])
                saved += 1
            except Exception:
                unresolved.append(entry["customer_name"] or "(invalid entry)")
        self.refresh_all()
        message = f"{saved} reviewed entries saved."
        if unresolved:
            message += "\n\nNot saved because the customer could not be matched or the entry was invalid:\n"
            message += ", ".join(sorted(set(unresolved)))
        inform(self, message)

    # Settings and multi-shop management
    def save_staff_pin(self) -> None:
        if self.staff_mode:
            return
        pin = self.staff_pin_input.text().strip()
        if len(pin) < 6 or not pin.isdigit():
            inform(self, "Use a staff PIN containing at least six digits.", True)
            return
        salt, digest = pin_hash(pin)
        self.store.set_setting("staff_pin_salt", salt)
        self.store.set_setting("staff_pin_hash", digest)
        self.store.set_setting("staff_pin_enabled", "1")
        self.staff_pin_input.clear()
        inform(self, "Staff PIN saved. Staff accounts cannot access financial reports, settings or destructive actions.")

    def save_preferences(self) -> None:
        if self.staff_mode:
            return
        daily_time = self.daily_time.text().strip()
        if not re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", daily_time):
            inform(self, "Enter daily summary time in 24-hour HH:MM format.", True)
            return
        self.store.set_setting("wallet_jazzcash", self.wallet_jazzcash.text().strip())
        self.store.set_setting("wallet_easypaisa", self.wallet_easypaisa.text().strip())
        self.store.set_setting("daily_summary_time", daily_time)
        self.store.set_setting("weekly_reminder_enabled", int(self.reminders_enabled.isChecked()))
        self.store.set_setting("printer_com", self.printer_com.text().strip().upper())
        self.store.set_setting("theme", self.theme_picker.currentText())
        self._apply_theme(self.theme_picker.currentText())
        inform(self, "Business preferences saved.")

    def save_ai_settings(self) -> None:
        if self.staff_mode:
            return
        raw_keys = self.ai_key_input.text().replace("\\n", ";")
        keys = [x.strip() for x in raw_keys.split(";") if x.strip()]
        try:
            if keys:
                protected = [protect_api_key(key) for key in keys[:5]]
                self.store.set_setting("ai_keys", json.dumps(protected))
                self.store.set_setting("ai_key", "")
            models = [x.strip() for x in self.ai_models_input.text().split(",") if x.strip()]
            if not models:
                raise ValueError("Enter at least one AI model name.")
            self.store.set_setting("ai_models", ",".join(models[:5]))
            self.ai_key_input.clear()
            inform(self, "AI settings saved. The API key is protected using the Windows user profile.")
            self.ai_status.setText("AI services are configured.")
        except Exception as exc:
            inform(self, str(exc), True)

    def remove_ai_key(self) -> None:
        if self.staff_mode:
            return
        if confirm(self, "Remove the saved AI service keys from this shop?"):
            self.store.set_setting("ai_keys", "[]")
            self.store.set_setting("ai_key", "")
            self.ai_key_input.clear()
            self.ai_status.setText("No AI key is configured.")
            inform(self, "Saved AI key removed.")

    def export_backup(self) -> None:
        if self.staff_mode:
            return
        password, ok = QInputDialog.getText(self, "Protect backup",
            "Create a backup password (at least 8 characters):", QLineEdit.EchoMode.Password)
        if not ok:
            return
        path, _ = QFileDialog.getSaveFileName(self, "Export encrypted backup",
            f"AI-Khata-{self.shop_id}-{iso_today()}.json", "Encrypted backup (*.json)")
        if not path:
            return
        try:
            self.store.export_backup(path, password)
            inform(self, "Encrypted backup exported. Keep the password safe; it cannot be recovered.")
        except Exception as exc:
            inform(self, str(exc), True)

    def restore_backup(self) -> None:
        if self.staff_mode:
            return
        path, _ = QFileDialog.getOpenFileName(self, "Choose encrypted AI Khata backup", "",
            "Encrypted backup (*.json)")
        if not path:
            return
        password, ok = QInputDialog.getText(self, "Unlock backup",
            "Enter the backup password:", QLineEdit.EchoMode.Password)
        if not ok:
            return
        if not confirm(self, "Restore replaces this shop's customer, supplier, stock, expense and settings records. The current shop name and owner PIN are kept. Export a backup first. Continue?"):
            return
        try:
            self.store.restore_backup(path, password)
            self.refresh_all()
            inform(self, "Backup restored and database integrity checked.")
        except Exception as exc:
            inform(self, str(exc), True)

    def open_data_folder(self) -> None:
        if self.staff_mode:
            return
        import subprocess
        subprocess.Popen(["explorer", str(APP_DIR)])

    def create_shop(self) -> None:
        if self.staff_mode:
            return
        name = ask_text(self, "Create shop", "New shop name")
        if not name:
            return
        pin = ask_text(self, "Create shop", "Owner PIN (at least 6 digits)", True)
        if not pin or len(pin) < 6 or not pin.isdigit():
            inform(self, "Use an owner PIN with at least six digits.", True)
            return
        shop_id = secrets.token_hex(8)
        path = APP_DIR / f"shop-{shop_id}.db"
        salt, digest = pin_hash(pin)
        new_store = Store(path)
        new_store.db.execute("INSERT INTO owner(id,shop_name,pin_salt,pin_hash,created_at) VALUES(1,?,?,?,?)",
                             (name.strip(), salt, digest, iso_today()))
        new_store.db.commit()
        new_store.close()
        item = {"id": shop_id, "name": name.strip(), "path": str(path)}
        self.shops.append(item)
        SHOP_REGISTRY.write_text(json.dumps(self.shops, indent=2), encoding="utf-8")
        self.shop_selector.addItem(item["name"], item["id"])
        inform(self, "New shop created. Choose it from the shop selector to open it.")

    def switch_shop(self, index: int) -> None:
        if index < 0:
            return
        shop_id = self.shop_selector.itemData(index)
        if shop_id == self.shop_id:
            return
        item = next((x for x in self.shops if x["id"] == shop_id), None)
        if not item:
            return
        candidate = Store(item["path"])
        owner = candidate.db.execute("SELECT * FROM owner WHERE id=1").fetchone()
        if not owner:
            candidate.close()
            inform(self, "This shop profile is damaged or incomplete.", True)
            self._reset_shop_selector()
            return
        dialog = LoginDialog(item["name"], self)
        if dialog.exec() != QDialog.DialogCode.Accepted:
            candidate.close()
            self._reset_shop_selector()
            return
        pin = dialog.value()
        staff = candidate.setting("staff_pin_enabled") == "1" and verify_pin(
            pin, candidate.setting("staff_pin_salt"), candidate.setting("staff_pin_hash"))
        owner_ok = verify_pin(pin, owner["pin_salt"], owner["pin_hash"])
        if not staff and not owner_ok:
            candidate.close()
            inform(self, "Incorrect PIN. The shop was not switched.", True)
            self._reset_shop_selector()
            return
        self.store.close()
        self.store = candidate
        self.shop_id = item["id"]
        self.shop_name = item["name"]
        self.staff_mode = bool(staff and not owner_ok)
        self.current_customer_id = None
        self.setWindowTitle(f"{APP_NAME}  |  {self.shop_name}")
        self.business_name_label.setText(self.shop_name)
        self.hero_greeting.setText(f"Welcome to {self.shop_name}")
        self._apply_access_rules()
        self.refresh_all()
        self.navigate("Dashboard")

    def _reset_shop_selector(self) -> None:
        self.shop_selector.blockSignals(True)
        self.shop_selector.setCurrentIndex(max(0, self.shop_selector.findData(self.shop_id)))
        self.shop_selector.blockSignals(False)

    # Global search
    def open_global_search(self) -> None:
        query = self.global_search.text().strip()
        if not query:
            return
        results = self.store.universal_search(query)
        if self.staff_mode:
            results = [item for item in results if item["type"] not in ("Supplier", "Supplier entry")]
        dialog = QDialog(self)
        dialog.setWindowTitle("Search results")
        dialog.resize(780, 460)
        layout = QVBoxLayout(dialog)
        layout.addWidget(QLabel(f"{len(results)} result(s) for “{query}”"))
        table = self._table(["Type", "Name / account", "Details"])
        for result in results:
            row = table.rowCount()
            table.insertRow(row)
            for col, value in enumerate([result["type"], result["title"], result["detail"]]):
                table.setItem(row, col, QTableWidgetItem(str(value)))
        layout.addWidget(table)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        buttons.rejected.connect(dialog.reject)
        buttons.accepted.connect(dialog.accept)
        layout.addWidget(buttons)
        dialog.exec()

    # Windows notifications
    def check_scheduled_notifications(self) -> None:
        if self.staff_mode or not hasattr(self, "tray"):
            return
        now = datetime.now()
        day_key = now.strftime("%Y-%m-%d")
        daily_time = self.store.setting("daily_summary_time", "20:00")
        if now.strftime("%H:%M") >= daily_time and self.store.setting("last_daily_notice") != day_key:
            summary = self.store.daily_summary()
            self.tray.showMessage(APP_NAME, f"Daily sales: {money(summary['sales_paisa'])} · "
                f"Payments: {money(summary['receipts_paisa'])} · Net due: {money(summary['net_due_change_paisa'])}",
                QSystemTrayIcon.MessageIcon.Information, 12_000)
            self.store.set_setting("last_daily_notice", day_key)
        if self.store.setting("weekly_reminder_enabled", "1") == "1" and now.weekday() == 0:
            if self.store.setting("last_overdue_notice") != day_key:
                overdue = self.store.overdue_customers(30)
                if overdue:
                    total = sum(row["overdue_paisa"] for row in overdue)
                    self.tray.showMessage(APP_NAME,
                        f"{len(overdue)} customer account(s) have overdue balances totalling {money(total)}.",
                        QSystemTrayIcon.MessageIcon.Warning, 12_000)
                self.store.set_setting("last_overdue_notice", day_key)

    def closeEvent(self, event: Any) -> None:
        try:
            self.store.close()
        finally:
            event.accept()


def first_setup() -> list[dict[str, str]]:
    shops = []
    if SHOP_REGISTRY.exists():
        try:
            loaded = json.loads(SHOP_REGISTRY.read_text(encoding="utf-8"))
            if isinstance(loaded, list):
                shops = [item for item in loaded if isinstance(item, dict)
                         and {"id", "name", "path"}.issubset(item)]
        except (OSError, json.JSONDecodeError):
            shops = []
    if shops:
        return shops
    name, ok = QInputDialog.getText(None, "Welcome to AI Khata", "Enter your shop or business name:")
    if not ok or not name.strip():
        return []
    pin, ok = QInputDialog.getText(None, "Secure your business", "Create an owner PIN (at least 6 digits):",
                                   QLineEdit.EchoMode.Password)
    if not ok:
        return []
    if len(pin) < 6 or not pin.isdigit():
        QMessageBox.critical(None, APP_NAME, "Use an owner PIN containing at least six digits.")
        return []
    salt, digest = pin_hash(pin)
    shop_id = "default"
    path = APP_DIR / "shop-default.db"
    store = Store(path)
    store.db.execute("INSERT OR REPLACE INTO owner(id,shop_name,pin_salt,pin_hash,created_at) VALUES(1,?,?,?,?)",
                     (name.strip(), salt, digest, iso_today()))
    store.db.commit()
    store.close()
    shops = [{"id": shop_id, "name": name.strip(), "path": str(path)}]
    SHOP_REGISTRY.write_text(json.dumps(shops, indent=2), encoding="utf-8")
    return shops


def start() -> int:
    app = QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setOrganizationName("AI Khata")
    app.setFont(QFont("Segoe UI", 10))
    shops = first_setup()
    if not shops:
        return 0
    first = shops[0]
    store = Store(first["path"])
    owner = store.db.execute("SELECT * FROM owner WHERE id=1").fetchone()
    if not owner:
        store.close()
        QMessageBox.critical(None, APP_NAME, "The selected shop has no valid owner profile.")
        return 1
    dialog = LoginDialog(first["name"])
    if dialog.exec() != QDialog.DialogCode.Accepted:
        store.close()
        return 0
    pin = dialog.value()
    staff = store.setting("staff_pin_enabled") == "1" and verify_pin(
        pin, store.setting("staff_pin_salt"), store.setting("staff_pin_hash"))
    owner_ok = verify_pin(pin, owner["pin_salt"], owner["pin_hash"])
    if not staff and not owner_ok:
        store.close()
        QMessageBox.critical(None, APP_NAME, "The PIN is incorrect.")
        return 1
    window = MainWindow(store, first["id"], first["name"], shops, bool(staff and not owner_ok))
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(start())
