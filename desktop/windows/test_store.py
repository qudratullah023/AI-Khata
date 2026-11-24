import json
import tempfile
import unittest
from pathlib import Path

from main import Store, pin_hash, verify_pin


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.temp.name) / "test.db")
        self.store.add_customer("Ali", "03001234567")
        self.customer_id = self.store.customers()[0]["id"]

    def tearDown(self):
        self.store.close()
        self.temp.cleanup()

    def test_credit_and_payment_balance(self):
        self.store.add_tx(self.customer_id, "credit", 1200, "Rice")
        self.store.add_tx(self.customer_id, "payment", 450, "Cash received")
        self.assertEqual(self.store.customers()[0]["balance"], 750.0)

    def test_inventory_sale_is_atomic_and_reduces_stock(self):
        self.store.add_product("Cooking oil", "OIL-1", 250, 320, 10, 2)
        product = self.store.products()[0]
        self.store.record_sale(self.customer_id, product["id"], 2)
        self.assertEqual(self.store.products()[0]["stock_milli"], 8000)
        self.assertEqual(self.store.customers()[0]["balance"], 640.0)
        tx = self.store.txs(self.customer_id)[0]
        self.assertEqual(tx["quantity_milli"], 2000)
        self.assertEqual(tx["unit_cost_paisa"], 25000)

    def test_supplier_payable_and_payment_balance(self):
        self.store.add_supplier("Wholesale Depot", "03009999999")
        supplier = self.store.suppliers()[0]
        self.store.supplier_entry(supplier["id"], "payable", 2000, "Stock purchase")
        self.store.supplier_entry(supplier["id"], "payment", 500, "Bank transfer")
        self.assertEqual(self.store.suppliers()[0]["balance"], 1500.0)

    def test_daily_summary_separates_credit_from_payments(self):
        self.store.add_tx(self.customer_id, "credit", 800, "Sale")
        self.store.add_tx(self.customer_id, "payment", 300, "Received")
        self.assertEqual(self.store.daily(), (800.0, 300.0, 500.0))

    def test_pin_hash_uses_salt_and_verifies(self):
        salt, digest = pin_hash("123456")
        self.assertTrue(verify_pin("123456", salt, digest))
        self.assertFalse(verify_pin("654321", salt, digest))

    def test_backup_export_contains_expected_tables(self):
        self.store.add_tx(self.customer_id, "credit", 250, "Test")
        path = Path(self.temp.name) / "backup.json"
        self.store.backup(path)
        data = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(data["format"], "ai-khata-desktop-v1")
        self.assertIn("customers", data["tables"])
        self.assertIn("transactions", data["tables"])
        self.assertEqual(len(data["tables"]["transactions"]), 1)


if __name__ == "__main__":
    unittest.main()
