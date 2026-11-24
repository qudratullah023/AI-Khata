import json
import tempfile
import unittest
from datetime import date, timedelta
from pathlib import Path

from store import Store


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.store = Store(self.root / "shop.db")
        self.customer_id = self.store.add_customer("Ali Khan", "03001234567")

    def tearDown(self):
        self.store.close()
        self.temp.cleanup()

    def test_credit_payment_and_customer_balance(self):
        self.store.add_transaction(self.customer_id, "credit", 1200, "Rice")
        self.store.add_transaction(self.customer_id, "payment", 450, "Cash received")
        self.assertEqual(self.store.customer_balance(self.customer_id), 75000)
        self.assertEqual(self.store.customers()[0]["balance_paisa"], 75000)

    def test_customer_profile_and_universal_search(self):
        self.store.save_customer_meta(self.customer_id, "wholesale, neighbour", "Weekly buyer", True)
        self.store.add_transaction(self.customer_id, "credit", 725, "Cooking oil")
        self.assertTrue(any(x["type"] == "Customer" for x in self.store.universal_search("Ali")))
        self.assertTrue(any(x["type"] == "Ledger entry" for x in self.store.universal_search("Cooking oil")))
        self.assertTrue(any(x["type"] == "Customer" for x in self.store.universal_search("wholesale")))

    def test_transaction_edit_and_delete(self):
        tx = self.store.add_transaction(self.customer_id, "credit", 100, "Old")
        self.store.edit_transaction(tx, "credit", 250, "Corrected", date.today().isoformat())
        self.assertEqual(self.store.customer_balance(self.customer_id), 25000)
        self.store.delete_transaction(tx)
        self.assertEqual(self.store.customer_balance(self.customer_id), 0)

    def test_inventory_sale_is_atomic_and_deletion_restores_stock(self):
        pid = self.store.add_product("Cooking oil", "OIL-1", "bottle", 250, 320, 10, 2)
        tx = self.store.record_product_sale(self.customer_id, pid, 2)
        self.assertEqual(self.store.products()[0]["stock_milli"], 8000)
        self.assertEqual(self.store.customer_balance(self.customer_id), 64000)
        self.store.delete_transaction(tx)
        self.assertEqual(self.store.products()[0]["stock_milli"], 10000)
        self.assertEqual(self.store.customer_balance(self.customer_id), 0)

    def test_overstock_sale_does_not_write_ledger(self):
        pid = self.store.add_product("Milk", "MILK", "pack", 80, 100, 1, 1)
        with self.assertRaises(ValueError):
            self.store.record_product_sale(self.customer_id, pid, 2)
        self.assertEqual(self.store.customer_balance(self.customer_id), 0)
        self.assertEqual(self.store.products()[0]["stock_milli"], 1000)

    def test_deleting_customer_restores_all_product_sales(self):
        pid = self.store.add_product("Tea", "TEA", "kg", 900, 1200, 5, 1)
        self.store.record_product_sale(self.customer_id, pid, 1.5)
        self.store.delete_customer(self.customer_id)
        self.assertEqual(self.store.products()[0]["stock_milli"], 5000)
        self.assertEqual(self.store.customers(), [])

    def test_supplier_payable_and_payment_balance(self):
        sid = self.store.add_supplier("Wholesale Depot", "03009999999")
        self.store.add_supplier_entry(sid, "payable", 2000, "Stock purchase")
        self.store.add_supplier_entry(sid, "payment", 500, "Bank transfer")
        self.assertEqual(self.store.suppliers()[0]["balance_paisa"], 150000)
        self.assertTrue(any(x["type"] == "Supplier entry"
                            for x in self.store.universal_search("Stock purchase")))

    def test_daily_summary_separates_credit_and_receipts(self):
        self.store.add_transaction(self.customer_id, "credit", 800, "Sale")
        self.store.add_transaction(self.customer_id, "payment", 300, "Received")
        summary = self.store.daily_summary()
        self.assertEqual(summary["sales_paisa"], 80000)
        self.assertEqual(summary["receipts_paisa"], 30000)
        self.assertEqual(summary["net_due_change_paisa"], 50000)

    def test_profit_loss_uses_cost_snapshots_and_expenses(self):
        pid = self.store.add_product("Flour", "FLR", "bag", 700, 900, 10, 2)
        self.store.record_product_sale(self.customer_id, pid, 2)
        self.store.add_transaction(self.customer_id, "credit", 5000, "Manual entry")
        self.store.add_expense("Utilities", "Electricity", 100)
        report = self.store.profit_loss()
        self.assertEqual(report["revenue_paisa"], 180000)
        self.assertEqual(report["cogs_paisa"], 140000)
        self.assertEqual(report["gross_profit_paisa"], 40000)
        self.assertEqual(report["expenses_paisa"], 10000)
        self.assertEqual(report["net_estimate_paisa"], 30000)

    def test_overdue_allocation_uses_oldest_unpaid_credit(self):
        old = (date.today() - timedelta(days=45)).isoformat()
        self.store.add_transaction(self.customer_id, "credit", 800, "Old invoice", old)
        self.store.add_transaction(self.customer_id, "credit", 300, "Recent invoice")
        self.store.add_transaction(self.customer_id, "payment", 600, "Part payment")
        overdue = self.store.overdue_customers(30)
        self.assertEqual(overdue[0]["overdue_paisa"], 20000)
        self.assertEqual(overdue[0]["due_paisa"], 50000)


    def test_advance_payment_covers_future_credit_for_overdue_ageing(self):
        old = (date.today() - timedelta(days=45)).isoformat()
        self.store.add_transaction(self.customer_id, "payment", 500, "Advance", old)
        self.store.add_transaction(self.customer_id, "credit", 500, "Later goods", old)
        self.assertEqual(self.store.overdue_customers(30), [])

    def test_encrypted_backup_round_trip_and_owner_pin_preservation(self):
        self.store.db.execute("INSERT INTO owner VALUES(1,?,?,?,?)",
                              ("My Shop", "owner-salt", "owner-digest", date.today().isoformat()))
        self.store.db.commit()
        self.store.save_customer_meta(self.customer_id, "regular", "Good payer", True)
        self.store.add_transaction(self.customer_id, "credit", 525, "Notebook")
        backup = self.root / "backup.json"
        self.store.export_backup(backup, "correct-horse-123")
        envelope = json.loads(backup.read_text(encoding="utf-8"))
        self.assertEqual(envelope["magic"], "AI-KHATA-DESKTOP-ENCRYPTED")
        self.assertNotIn("Notebook", backup.read_text(encoding="utf-8"))
        other = Store(self.root / "restored.db")
        other.db.execute("INSERT INTO owner VALUES(1,?,?,?,?)",
                         ("Destination Shop", "destination-salt", "destination-hash", date.today().isoformat()))
        other.db.commit()
        other.restore_backup(backup, "correct-horse-123")
        self.assertEqual(other.customer_balance(self.customer_id), 52500)
        self.assertEqual(other.db.execute("SELECT shop_name FROM owner").fetchone()[0], "Destination Shop")
        self.assertEqual(other.db.execute("SELECT tags FROM customer_meta").fetchone()[0], "regular")
        other.close()

    def test_wrong_backup_password_leaves_database_unchanged(self):
        self.store.add_transaction(self.customer_id, "credit", 99, "Existing")
        backup = self.root / "backup.json"
        self.store.export_backup(backup, "correct-password")
        with self.assertRaises(ValueError):
            self.store.restore_backup(backup, "wrong-password")
        self.assertEqual(self.store.customer_balance(self.customer_id), 9900)

    def test_integrity_check(self):
        ok, message = self.store.integrity_check()
        self.assertTrue(ok, message)


if __name__ == "__main__":
    unittest.main()
