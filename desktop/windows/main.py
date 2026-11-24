import json, os, sqlite3, sys, hashlib, secrets, hmac, shutil
from pathlib import Path
from datetime import date, datetime, timedelta
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QAction, QPdfWriter, QPainter, QFont
from PySide6.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QFormLayout, QLineEdit, QPushButton, QLabel, QMessageBox, QTabWidget, QTableWidget,
    QTableWidgetItem, QHeaderView, QComboBox, QDoubleSpinBox, QTextEdit, QFileDialog,
    QInputDialog, QDialog, QDialogButtonBox, QSplitter, QGroupBox, QCheckBox)

APP_NAME = "AI Khata"
APP_DIR = Path(os.getenv("APPDATA", str(Path.home()))) / "AI Khata"
APP_DIR.mkdir(parents=True, exist_ok=True)
REGISTRY = APP_DIR / "shops.json"

def money(v): return f"Rs. {float(v or 0):,.2f}"
def today(): return date.today().isoformat()
def pin_hash(pin, salt=None):
    salt = salt or secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", pin.encode(), salt, 210000)
    return salt.hex(), digest.hex()
def verify_pin(pin, salt, digest):
    try: return hmac.compare_digest(pin_hash(pin, bytes.fromhex(salt))[1], digest)
    except Exception: return False

class Store:
    def __init__(self, path):
        self.path = Path(path)
        self.db = sqlite3.connect(self.path)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys=ON")
        self.init()
    def init(self):
        self.db.executescript("""
        CREATE TABLE IF NOT EXISTS owner(id INTEGER PRIMARY KEY CHECK(id=1), shop_name TEXT NOT NULL, pin_salt TEXT NOT NULL, pin_hash TEXT NOT NULL, created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,transaction_date TEXT NOT NULL,transaction_type TEXT NOT NULL CHECK(transaction_type IN ('credit','payment')),description TEXT NOT NULL DEFAULT '',amount_paisa INTEGER NOT NULL CHECK(amount_paisa>0),created_at TEXT NOT NULL,product_id INTEGER,quantity_milli INTEGER,unit_cost_paisa INTEGER);
        CREATE INDEX IF NOT EXISTS idx_tx_customer_date ON transactions(customer_id,transaction_date);
        CREATE TABLE IF NOT EXISTS products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,sku TEXT NOT NULL DEFAULT '',unit TEXT NOT NULL DEFAULT 'pcs',purchase_price_paisa INTEGER NOT NULL DEFAULT 0,selling_price_paisa INTEGER NOT NULL,stock_milli INTEGER NOT NULL DEFAULT 0,low_stock_milli INTEGER NOT NULL DEFAULT 5000,created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,expense_date TEXT NOT NULL,category TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',amount_paisa INTEGER NOT NULL,created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS customer_meta(customer_id INTEGER PRIMARY KEY,tags TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',share_opt_in INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE IF NOT EXISTS suppliers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS supplier_entries(id INTEGER PRIMARY KEY AUTOINCREMENT,supplier_id INTEGER NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,entry_date TEXT NOT NULL,entry_type TEXT NOT NULL CHECK(entry_type IN ('payable','payment')),description TEXT NOT NULL DEFAULT '',amount_paisa INTEGER NOT NULL,created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS settings(setting_key TEXT PRIMARY KEY,setting_value TEXT NOT NULL);
        """)
        self.db.commit()
    def setting(self,k,d=""):
        r=self.db.execute("SELECT setting_value FROM settings WHERE setting_key=?",(k,)).fetchone()
        return r[0] if r else d
    def set_setting(self,k,v):
        self.db.execute("INSERT INTO settings VALUES(?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value",(k,str(v))); self.db.commit()
    def customers(self,q=""):
        return self.db.execute("""SELECT c.id,c.name,c.phone,
        COALESCE(SUM(CASE WHEN t.transaction_type='credit' THEN t.amount_paisa ELSE -t.amount_paisa END),0)/100.0 balance
        FROM customers c LEFT JOIN transactions t ON t.customer_id=c.id
        WHERE c.name LIKE ? OR c.phone LIKE ? GROUP BY c.id ORDER BY c.name COLLATE NOCASE""",(f"%{q}%",f"%{q}%")).fetchall()
    def add_customer(self,n,p):
        self.db.execute("INSERT INTO customers(name,phone,created_at) VALUES(?,?,?)",(n.strip(),p.strip(),today())); self.db.commit()
    def add_tx(self,cid,typ,amount,desc,dt=None):
        if amount<=0: raise ValueError("Amount must be greater than zero.")
        self.db.execute("INSERT INTO transactions(customer_id,transaction_date,transaction_type,description,amount_paisa,created_at) VALUES(?,?,?,?,?,?)",(cid,dt or today(),typ,desc.strip(),round(amount*100),datetime.now().isoformat())); self.db.commit()
    def txs(self,cid):
        return self.db.execute("SELECT * FROM transactions WHERE customer_id=? ORDER BY transaction_date DESC,id DESC",(cid,)).fetchall()
    def suppliers(self):
        return self.db.execute("""SELECT s.id,s.name,s.phone,COALESCE(SUM(CASE WHEN e.entry_type='payable' THEN e.amount_paisa ELSE -e.amount_paisa END),0)/100.0 balance FROM suppliers s LEFT JOIN supplier_entries e ON e.supplier_id=s.id GROUP BY s.id ORDER BY s.name""").fetchall()
    def add_supplier(self,n,p):
        self.db.execute("INSERT INTO suppliers(name,phone,created_at) VALUES(?,?,?)",(n,p,today())); self.db.commit()
    def supplier_entry(self,sid,typ,amt,desc):
        if amt<=0: raise ValueError("Amount must be greater than zero.")
        self.db.execute("INSERT INTO supplier_entries(supplier_id,entry_date,entry_type,description,amount_paisa,created_at) VALUES(?,?,?,?,?,?)",(sid,today(),typ,desc,round(amt*100),datetime.now().isoformat())); self.db.commit()
    def products(self):
        return self.db.execute("SELECT * FROM products ORDER BY name").fetchall()
    def add_product(self,n,sku,buy,sell,stock,low):
        if sell<=0 or stock<0: raise ValueError("Selling price must be positive and stock cannot be negative.")
        self.db.execute("INSERT INTO products(name,sku,purchase_price_paisa,selling_price_paisa,stock_milli,low_stock_milli,created_at) VALUES(?,?,?,?,?,?,?)",(n,sku,round(buy*100),round(sell*100),round(stock*1000),round(low*1000),today())); self.db.commit()
    def record_sale(self,cid,pid,qty):
        p=self.db.execute("SELECT * FROM products WHERE id=?",(pid,)).fetchone()
        if not p or qty<=0 or round(qty*1000)>p["stock_milli"]: raise ValueError("Product unavailable or insufficient stock.")
        total=qty*p["selling_price_paisa"]/100
        self.db.execute("BEGIN")
        try:
            self.db.execute("INSERT INTO transactions(customer_id,transaction_date,transaction_type,description,amount_paisa,created_at,product_id,quantity_milli,unit_cost_paisa) VALUES(?,?,?,?,?,?,?,?,?)",(cid,today(),"credit",p["name"],round(total*100),datetime.now().isoformat(),pid,round(qty*1000),p["purchase_price_paisa"]))
            self.db.execute("UPDATE products SET stock_milli=stock_milli-? WHERE id=?",(round(qty*1000),pid)); self.db.commit()
        except: self.db.rollback(); raise
    def add_expense(self,cat,desc,amt):
        if amt<=0: raise ValueError("Amount must be greater than zero.")
        self.db.execute("INSERT INTO expenses(expense_date,category,description,amount_paisa,created_at) VALUES(?,?,?,?,?)",(today(),cat,desc,round(amt*100),datetime.now().isoformat())); self.db.commit()
    def daily(self):
        r=self.db.execute("""SELECT COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount_paisa END),0)/100.0 sales,COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount_paisa END),0)/100.0 payments FROM transactions WHERE transaction_date=?""",(today(),)).fetchone()
        return r["sales"],r["payments"],r["sales"]-r["payments"]
    def backup(self,path):
        data={}
        for t in ["owner","customers","transactions","products","expenses","customer_meta","suppliers","supplier_entries","settings"]:
            data[t]=[dict(r) for r in self.db.execute(f"SELECT * FROM {t}")]
        Path(path).write_text(json.dumps({"format":"ai-khata-desktop-v1","exported_at":datetime.now().isoformat(),"tables":data},ensure_ascii=False,indent=2),encoding="utf-8")
    def restore(self,path):
        data=json.loads(Path(path).read_text(encoding="utf-8"))
        if not isinstance(data.get("tables"),dict): raise ValueError("This is not a valid AI Khata backup.")
        allowed={"owner","customers","transactions","products","expenses","customer_meta","suppliers","supplier_entries","settings"}
        if not set(data["tables"]).issubset(allowed): raise ValueError("Backup contains unknown tables.")
        raise ValueError("Restore is not enabled in this initial Windows build. Export a backup safely; import compatibility is being completed.")
    def close(self): self.db.close()

def ask_text(parent,title,label,secret=False):
    v,ok=QInputDialog.getText(parent,title,label,QLineEdit.EchoMode.Password if secret else QLineEdit.EchoMode.Normal)
    return v.strip() if ok else None
def alert(parent,msg,error=False):
    (QMessageBox.critical if error else QMessageBox.information)(parent,APP_NAME,msg)

class LoginDialog(QDialog):
    def __init__(self, shop_name, owner=False, staff=False, parent=None):
        super().__init__(parent); self.setWindowTitle(APP_NAME+" · Unlock"); self.pin=""
        lay=QVBoxLayout(self); lay.addWidget(QLabel(f"<h2>{shop_name}</h2><p>Enter your PIN to continue</p>"))
        self.edit=QLineEdit(); self.edit.setEchoMode(QLineEdit.EchoMode.Password); self.edit.setMaxLength(12); self.edit.setPlaceholderText("Shop PIN"); lay.addWidget(self.edit)
        buttons=QDialogButtonBox(QDialogButtonBox.StandardButton.Ok|QDialogButtonBox.StandardButton.Cancel); buttons.accepted.connect(self.accept); buttons.rejected.connect(self.reject); lay.addWidget(buttons)
    def value(self): return self.edit.text()

class Main(QMainWindow):
    def __init__(self, store, shop_id, shop_name, shops):
        super().__init__(); self.store=store; self.shop_id=shop_id; self.shop_name=shop_name; self.shops=shops; self.staff=False
        self.setWindowTitle(f"AI Khata — {shop_name}"); self.resize(1180,760)
        root=QWidget(); self.setCentralWidget(root); outer=QVBoxLayout(root)
        top=QHBoxLayout(); title=QLabel(f"<h1>AI Khata</h1><span>Business ledger · {shop_name}</span>"); top.addWidget(title); top.addStretch()
        self.shop_pick=QComboBox(); self.shop_pick.addItems([x["name"] for x in shops]); self.shop_pick.setCurrentIndex(next((i for i,x in enumerate(shops) if x["id"]==shop_id),0)); self.shop_pick.currentIndexChanged.connect(self.switch_shop); top.addWidget(self.shop_pick)
        outer.addLayout(top)
        self.tabs=QTabWidget(); outer.addWidget(self.tabs)
        self.build_dashboard(); self.build_customers(); self.build_suppliers(); self.build_inventory(); self.build_expenses(); self.build_reports(); self.build_settings()
        self.refresh_all()
        self.timer=QTimer(self); self.timer.timeout.connect(self.check_reminders); self.timer.start(60000)
    def table(self,headers):
        t=QTableWidget(0,len(headers)); t.setHorizontalHeaderLabels(headers); t.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch); t.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows); t.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers); return t
    def build_dashboard(self):
        w=QWidget(); l=QVBoxLayout(w); l.addWidget(QLabel("<h2>Today at a glance</h2>"))
        self.daily_label=QLabel(); self.daily_label.setStyleSheet("font-size:20px;padding:24px;background:#17263e;color:#e8f3ff;border-radius:14px"); l.addWidget(self.daily_label)
        row=QHBoxLayout()
        for label,slot in [("Add customer",self.quick_customer),("Record payment",self.quick_payment),("New credit",self.quick_credit),("Export backup",self.export_backup)]:
            b=QPushButton(label); b.setMinimumHeight(46); b.clicked.connect(slot); row.addWidget(b)
        l.addLayout(row); l.addWidget(QLabel("<h3>Recent customers</h3>")); self.recent=self.table(["Customer","Phone","Outstanding"]); l.addWidget(self.recent); self.tabs.addTab(w,"Dashboard")
    def build_customers(self):
        w=QWidget(); l=QVBoxLayout(w); bar=QHBoxLayout(); self.search=QLineEdit(); self.search.setPlaceholderText("Search customers by name or phone…"); self.search.textChanged.connect(self.refresh_customers); bar.addWidget(self.search); b=QPushButton("Add customer"); b.clicked.connect(self.quick_customer); bar.addWidget(b); l.addLayout(bar)
        split=QSplitter(); self.customer_table=self.table(["ID","Customer","Phone","Balance"]); self.customer_table.itemSelectionChanged.connect(self.refresh_ledger); split.addWidget(self.customer_table)
        pane=QWidget(); p=QVBoxLayout(pane); self.ledger= self.table(["Date","Type","Description","Amount"]); p.addWidget(QLabel("<b>Selected customer ledger</b>")); p.addWidget(self.ledger)
        form=QFormLayout(); self.tx_type=QComboBox(); self.tx_type.addItems(["Credit","Payment"]); self.tx_amount=QDoubleSpinBox(); self.tx_amount.setMaximum(1e9); self.tx_amount.setDecimals(2); self.tx_amount.setPrefix("Rs. "); self.tx_desc=QLineEdit(); form.addRow("Entry type",self.tx_type); form.addRow("Amount",self.tx_amount); form.addRow("Description",self.tx_desc); p.addLayout(form)
        add=QPushButton("Save ledger entry"); add.clicked.connect(self.save_tx); p.addWidget(add)
        meta=QGroupBox("Customer profile"); mf=QFormLayout(meta); self.tags=QLineEdit(); self.notes=QLineEdit(); self.share=QCheckBox("Customer agreed to receive a read-only statement"); mf.addRow("Tags",self.tags); mf.addRow("Notes",self.notes); mf.addRow(self.share); save=QPushButton("Save profile"); save.clicked.connect(self.save_meta); mf.addRow(save); p.addWidget(meta)
        actions=QHBoxLayout(); pdf=QPushButton("Export statement PDF"); pdf.clicked.connect(self.statement_pdf); actions.addWidget(pdf); pr=QPushButton("Print statement"); pr.clicked.connect(self.statement_pdf); actions.addWidget(pr); p.addLayout(actions)
        split.addWidget(pane); split.setSizes([400,700]); l.addWidget(split); self.tabs.addTab(w,"Customers")
    def build_suppliers(self):
        w=QWidget(); l=QVBoxLayout(w); self.supplier_table=self.table(["ID","Supplier","Phone","Outstanding"]); l.addWidget(self.supplier_table)
        f=QFormLayout(); self.sname=QLineEdit(); self.sphone=QLineEdit(); self.stype=QComboBox(); self.stype.addItems(["Payable","Payment"]); self.samount=QDoubleSpinBox(); self.samount.setMaximum(1e9); self.samount.setPrefix("Rs. "); self.sdesc=QLineEdit(); f.addRow("Supplier",self.sname); f.addRow("Phone",self.sphone); f.addRow("Entry",self.stype); f.addRow("Amount",self.samount); f.addRow("Description",self.sdesc); l.addLayout(f); b=QPushButton("Save supplier / entry"); b.clicked.connect(self.save_supplier); l.addWidget(b); self.tabs.addTab(w,"Supplier Khata")
    def build_inventory(self):
        w=QWidget(); l=QVBoxLayout(w); self.product_table=self.table(["ID","Product","SKU","Buy","Sell","Stock","Low stock"]); l.addWidget(self.product_table)
        f=QFormLayout(); self.pname=QLineEdit(); self.psku=QLineEdit(); self.pbuy=QDoubleSpinBox(); self.pbuy.setMaximum(1e9); self.psell=QDoubleSpinBox(); self.psell.setMaximum(1e9); self.pstock=QDoubleSpinBox(); self.pstock.setMaximum(1e9); self.plow=QDoubleSpinBox(); self.plow.setValue(5); self.plow.setMaximum(1e9)
        for label,field in [("Product",self.pname),("SKU",self.psku),("Purchase price",self.pbuy),("Selling price",self.psell),("Opening stock",self.pstock),("Low-stock alert",self.plow)]: f.addRow(label,field)
        l.addLayout(f); b=QPushButton("Add product"); b.clicked.connect(self.save_product); l.addWidget(b); l.addWidget(QLabel("Product sales are recorded as customer credit and stock is reduced atomically. Use the Android app for camera scanning.")); self.tabs.addTab(w,"Inventory")
    def build_expenses(self):
        w=QWidget(); l=QVBoxLayout(w); self.exp_table=self.table(["Date","Category","Description","Amount"]); l.addWidget(self.exp_table)
        f=QFormLayout(); self.ecat=QComboBox(); self.ecat.addItems(["Rent","Utilities","Salary","Transport","Supplies","Other"]); self.edesc=QLineEdit(); self.eamount=QDoubleSpinBox(); self.eamount.setMaximum(1e9); self.eamount.setPrefix("Rs. "); f.addRow("Category",self.ecat); f.addRow("Description",self.edesc); f.addRow("Amount",self.eamount); l.addLayout(f); b=QPushButton("Record expense"); b.clicked.connect(self.save_expense); l.addWidget(b); self.tabs.addTab(w,"Expenses")
    def build_reports(self):
        w=QWidget(); l=QVBoxLayout(w); self.report=QLabel(); self.report.setStyleSheet("font-size:17px;padding:24px"); l.addWidget(self.report); b=QPushButton("Refresh report"); b.clicked.connect(self.refresh_reports); l.addWidget(b); self.tabs.addTab(w,"Reports")
    def build_settings(self):
        w=QWidget(); l=QVBoxLayout(w); l.addWidget(QLabel("<h2>Business settings</h2>"))
        f=QFormLayout(); self.jazz=QLineEdit(self.store.setting("wallet_jazzcash")); self.easy=QLineEdit(self.store.setting("wallet_easypaisa")); self.daily_time=QLineEdit(self.store.setting("daily_summary_time","20:00")); self.reminder=QCheckBox("Enable overdue reminders"); self.reminder.setChecked(self.store.setting("weekly_reminder_enabled","1")=="1"); f.addRow("JazzCash number",self.jazz); f.addRow("Easypaisa number",self.easy); f.addRow("Daily summary time (HH:MM)",self.daily_time); f.addRow(self.reminder); l.addLayout(f)
        save=QPushButton("Save settings"); save.clicked.connect(self.save_settings); l.addWidget(save)
        row=QHBoxLayout(); backup=QPushButton("Export backup"); backup.clicked.connect(self.export_backup); row.addWidget(backup); restore=QPushButton("Import backup"); restore.clicked.connect(self.import_backup); row.addWidget(restore); staff=QPushButton("Set staff PIN"); staff.clicked.connect(self.set_staff_pin); row.addWidget(staff); shop=QPushButton("Create shop"); shop.clicked.connect(self.create_shop); row.addWidget(shop); l.addLayout(row)
        l.addWidget(QLabel("Bluetooth thermal printers connected to Windows can be selected through the Windows print dialog. Statement PDF export is available in Customers.")); self.tabs.addTab(w,"Settings")
    def selected_customer(self):
        rows=self.customer_table.selectionModel().selectedRows()
        return int(self.customer_table.item(rows[0].row(),0).text()) if rows else None
    def refresh_all(self):
        sales,payments,due=self.store.daily(); self.daily_label.setText(f"<b>Sales today:</b> {money(sales)}   ·   <b>Payments:</b> {money(payments)}   ·   <b>Net due:</b> {money(due)}")
        self.refresh_customers(); self.refresh_suppliers(); self.refresh_products(); self.refresh_expenses(); self.refresh_reports()
    def refresh_customers(self):
        if not hasattr(self,"customer_table"): return
        rows=self.store.customers(self.search.text() if hasattr(self,"search") else ""); self.customer_table.setRowCount(0)
        for r in rows:
            i=self.customer_table.rowCount(); self.customer_table.insertRow(i)
            for c,v in enumerate([r["id"],r["name"],r["phone"],money(r["balance"])]): self.customer_table.setItem(i,c,QTableWidgetItem(str(v)))
        self.recent.setRowCount(0)
        for r in rows[:8]:
            i=self.recent.rowCount(); self.recent.insertRow(i)
            for c,v in enumerate([r["name"],r["phone"],money(r["balance"])]): self.recent.setItem(i,c,QTableWidgetItem(str(v)))
        self.refresh_ledger()
    def refresh_ledger(self):
        if not hasattr(self,"ledger"): return
        cid=self.selected_customer(); self.ledger.setRowCount(0)
        if not cid: return
        for r in self.store.txs(cid):
            i=self.ledger.rowCount(); self.ledger.insertRow(i)
            for c,v in enumerate([r["transaction_date"],r["transaction_type"],r["description"],money(r["amount_paisa"]/100)]): self.ledger.setItem(i,c,QTableWidgetItem(str(v)))
        m=self.store.db.execute("SELECT tags,notes,share_opt_in FROM customer_meta WHERE customer_id=?",(cid,)).fetchone()
        self.tags.setText(m["tags"] if m else ""); self.notes.setText(m["notes"] if m else ""); self.share.setChecked(bool(m["share_opt_in"]) if m else False)
    def refresh_suppliers(self):
        self.supplier_table.setRowCount(0)
        for r in self.store.suppliers():
            i=self.supplier_table.rowCount(); self.supplier_table.insertRow(i)
            for c,v in enumerate([r["id"],r["name"],r["phone"],money(r["balance"])]): self.supplier_table.setItem(i,c,QTableWidgetItem(str(v)))
    def refresh_products(self):
        self.product_table.setRowCount(0)
        for r in self.store.products():
            i=self.product_table.rowCount(); self.product_table.insertRow(i)
            for c,v in enumerate([r["id"],r["name"],r["sku"],money(r["purchase_price_paisa"]/100),money(r["selling_price_paisa"]/100),f'{r["stock_milli"]/1000:g}',f'{r["low_stock_milli"]/1000:g}']): self.product_table.setItem(i,c,QTableWidgetItem(str(v)))
    def refresh_expenses(self):
        self.exp_table.setRowCount(0)
        for r in self.store.db.execute("SELECT * FROM expenses ORDER BY expense_date DESC,id DESC LIMIT 100"):
            i=self.exp_table.rowCount(); self.exp_table.insertRow(i)
            for c,v in enumerate([r["expense_date"],r["category"],r["description"],money(r["amount_paisa"]/100)]): self.exp_table.setItem(i,c,QTableWidgetItem(str(v)))
    def refresh_reports(self):
        sales,payments,due=self.store.daily()
        month=date.today().strftime("%Y-%m")
        expenses=self.store.db.execute("SELECT COALESCE(SUM(amount_paisa),0)/100.0 FROM expenses WHERE expense_date LIKE ?",(month+"%",)).fetchone()[0]
        products=self.store.db.execute("SELECT COUNT(*),COALESCE(SUM(CASE WHEN stock_milli<=low_stock_milli THEN 1 ELSE 0 END),0) FROM products").fetchone()
        self.report.setText(f"<h2>Business summary · {month}</h2><p>Today's credit sales: <b>{money(sales)}</b></p><p>Payments received today: <b>{money(payments)}</b></p><p>Today's net due movement: <b>{money(due)}</b></p><p>Recorded expenses this month: <b>{money(expenses)}</b></p><p>Products tracked: {products[0]} · Low-stock items: {products[1]}</p><p>Customer accounts: {len(self.store.customers())} · Suppliers: {len(self.store.suppliers())}</p><p><i>Profit and loss totals are estimates based on recorded product-cost snapshots; untracked manual credit is not included.</i></p>")
    def run_action(self,fn):
        try: fn()
        except Exception as e: alert(self,str(e),True)
    def quick_customer(self):
        n=ask_text(self,"New customer","Customer name")
        if n is None:return
        if not n: return alert(self,"Enter a customer name.",True)
        p=ask_text(self,"New customer","Phone number") or ""
        self.store.add_customer(n,p); self.refresh_customers()
    def quick_credit(self): self.quick_tx("credit")
    def quick_payment(self): self.quick_tx("payment")
    def quick_tx(self,typ):
        cid=self.selected_customer()
        if not cid:return alert(self,"Select a customer first.")
        a,ok=QInputDialog.getDouble(self,"Ledger entry","Amount (Rs.)",0.0,0.01,100000000,2)
        if ok:
            d=ask_text(self,"Ledger entry","Description") or ""
            self.store.add_tx(cid,typ,a,d); self.refresh_all()
    def save_tx(self):
        cid=self.selected_customer()
        if not cid:return alert(self,"Select a customer first.")
        self.run_action(lambda:(self.store.add_tx(cid,"credit" if self.tx_type.currentIndex()==0 else "payment",self.tx_amount.value(),self.tx_desc.text()),self.tx_amount.setValue(0),self.tx_desc.clear(),self.refresh_all()))
    def save_meta(self):
        cid=self.selected_customer()
        if cid:self.store.db.execute("INSERT INTO customer_meta VALUES(?,?,?,?) ON CONFLICT(customer_id) DO UPDATE SET tags=excluded.tags,notes=excluded.notes,share_opt_in=excluded.share_opt_in",(cid,self.tags.text(),self.notes.text(),int(self.share.isChecked()))); self.store.db.commit(); alert(self,"Customer profile saved.")
    def save_supplier(self):
        n=self.sname.text().strip()
        if not n:return alert(self,"Enter supplier name.",True)
        if not any(r["name"].casefold()==n.casefold() for r in self.store.suppliers()): self.store.add_supplier(n,self.sphone.text())
        r=next(r for r in self.store.suppliers() if r["name"].casefold()==n.casefold())
        if self.samount.value()>0:self.store.supplier_entry(r["id"],"payable" if self.stype.currentIndex()==0 else "payment",self.samount.value(),self.sdesc.text())
        self.refresh_suppliers(); self.samount.setValue(0); self.sdesc.clear()
    def save_product(self):
        if not self.pname.text().strip():return alert(self,"Enter product name.",True)
        self.run_action(lambda:(self.store.add_product(self.pname.text(),self.psku.text(),self.pbuy.value(),self.psell.value(),self.pstock.value(),self.plow.value()),self.refresh_products()))
    def save_expense(self):
        if self.eamount.value()<=0:return alert(self,"Enter an expense amount.",True)
        self.store.add_expense(self.ecat.currentText(),self.edesc.text(),self.eamount.value()); self.refresh_all()
    def save_settings(self):
        self.store.set_setting("wallet_jazzcash",self.jazz.text()); self.store.set_setting("wallet_easypaisa",self.easy.text()); self.store.set_setting("daily_summary_time",self.daily_time.text()); self.store.set_setting("weekly_reminder_enabled","1" if self.reminder.isChecked() else "0"); alert(self,"Settings saved.")
    def export_backup(self):
        p,_=QFileDialog.getSaveFileName(self,"Export AI Khata backup",f"AI-Khata-{self.shop_id}-{today()}.json","JSON backup (*.json)")
        if p:self.store.backup(p); alert(self,"Backup exported.")
    def import_backup(self): alert(self,"Backup import is being completed so Android and Windows backups can be exchanged safely.")
    def statement_pdf(self):
        cid=self.selected_customer()
        if not cid:return alert(self,"Select a customer first.")
        cust=self.store.db.execute("SELECT * FROM customers WHERE id=?",(cid,)).fetchone()
        if not cust:return
        if not self.share.isChecked():return alert(self,"Enable customer statement sharing only after receiving the customer's consent.",True)
        p,_=QFileDialog.getSaveFileName(self,"Save statement PDF",f"{cust['name']}-statement.pdf","PDF (*.pdf)")
        if not p:return
        writer=QPdfWriter(p); writer.setPageSize(QPdfWriter.PageSize.A4); painter=QPainter(writer); painter.setFont(QFont("Arial",14)); y=80
        painter.drawText(60,y,"AI Khata — Customer Statement"); y+=55; painter.setFont(QFont("Arial",10)); painter.drawText(60,y,f"Customer: {cust['name']}  |  Phone: {cust['phone']}"); y+=35
        for r in self.store.txs(cid):
            painter.drawText(60,y,f"{r['transaction_date']}   {r['transaction_type']}   {r['description'][:48]}   Rs. {r['amount_paisa']/100:,.2f}"); y+=25
            if y>writer.height()-80: writer.newPage(); y=80
        painter.end(); alert(self,"Statement PDF saved.")
    def set_staff_pin(self):
        pin=ask_text(self,"Staff access","Set a staff PIN (at least 6 digits)",True)
        if pin and len(pin)>=6 and pin.isdigit():
            salt,digest=pin_hash(pin); self.store.set_setting("staff_pin_salt",salt); self.store.set_setting("staff_pin_hash",digest); self.store.set_setting("staff_pin_enabled","1"); alert(self,"Staff PIN saved.")
        elif pin is not None: alert(self,"Use at least 6 digits.",True)
    def create_shop(self):
        n=ask_text(self,"Create shop","Shop name")
        if not n:return
        pin=ask_text(self,"Create shop","Owner PIN (at least 6 digits)",True)
        if not pin or len(pin)<6 or not pin.isdigit():return alert(self,"Use a PIN with at least 6 digits.",True)
        salt,digest=pin_hash(pin); sid=secrets.token_hex(8); path=APP_DIR/f"shop-{sid}.db"; s=Store(path); s.db.execute("INSERT INTO owner VALUES(1,?,?,?,?)",(n,salt,digest,today())); s.db.commit(); s.close()
        self.shops.append({"id":sid,"name":n,"path":str(path)}); REGISTRY.write_text(json.dumps(self.shops,indent=2)); self.shop_pick.addItem(n); alert(self,"Shop created. Select it from the shop switcher.")
    def switch_shop(self,index):
        if index<0 or index>=len(self.shops):return
        shop=self.shops[index]
        if shop["id"]==self.shop_id:return
        QMessageBox.information(self,"Switch shop","Close AI Khata and reopen it to unlock the selected shop. (Shop data remains separate.)")
    def check_reminders(self):
        if self.store.setting("weekly_reminder_enabled","1")=="1":
            last=self.store.setting("last_overdue_notice")
            if last!=today() and datetime.now().weekday()==0:
                cutoff=(date.today()-timedelta(days=30)).isoformat()
                count=self.store.db.execute("SELECT COUNT(*) FROM customers c WHERE (SELECT COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount_paisa ELSE -amount_paisa END),0) FROM transactions WHERE customer_id=c.id)>0 AND (SELECT MIN(transaction_date) FROM transactions WHERE customer_id=c.id AND transaction_type='credit')<=?",(cutoff,)).fetchone()[0]
                if count: QMessageBox.information(self,"AI Khata overdue reminder",f"{count} customer accounts may be overdue. Review their khata balances.")
                self.store.set_setting("last_overdue_notice",today())

def start():
    app=QApplication(sys.argv); app.setApplicationName(APP_NAME)
    shops=json.loads(REGISTRY.read_text()) if REGISTRY.exists() else []
    if not shops:
        n,ok=QInputDialog.getText(None,APP_NAME+" · First setup","Enter your shop name:")
        if not ok or not n.strip(): return 0
        pin,ok=QInputDialog.getText(None,APP_NAME+" · First setup","Create owner PIN (at least 6 digits):",QLineEdit.EchoMode.Password)
        if not ok or len(pin)<6 or not pin.isdigit(): QMessageBox.critical(None,APP_NAME,"Use at least 6 digits for the owner PIN."); return 1
        salt,digest=pin_hash(pin); sid="default"; path=APP_DIR/"shop-default.db"; s=Store(path); s.db.execute("INSERT OR REPLACE INTO owner VALUES(1,?,?,?,?)",(n.strip(),salt,digest,today())); s.db.commit(); s.close()
        shops=[{"id":sid,"name":n.strip(),"path":str(path)}]; REGISTRY.write_text(json.dumps(shops,indent=2))
    shop=shops[0]; s=Store(shop["path"]); owner=s.db.execute("SELECT * FROM owner WHERE id=1").fetchone()
    dlg=LoginDialog(shop["name"]); 
    if dlg.exec()!=QDialog.DialogCode.Accepted:return 0
    pin=dlg.value(); staff=s.setting("staff_pin_enabled")=="1" and verify_pin(pin,s.setting("staff_pin_salt"),s.setting("staff_pin_hash"))
    if not staff and not verify_pin(pin,owner["pin_salt"],owner["pin_hash"]): QMessageBox.critical(None,APP_NAME,"Incorrect PIN."); s.close(); return 1
    win=Main(s,shop["id"],shop["name"],shops); win.staff=staff
    if staff:
        for idx in [5,6]: win.tabs.setTabEnabled(idx,False)
        win.setWindowTitle(f"AI Khata — {shop['name']} · Staff")
    win.show(); return app.exec()

if __name__=="__main__": sys.exit(start())
