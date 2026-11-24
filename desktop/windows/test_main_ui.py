import os
import tempfile
import unittest
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtWidgets import QApplication

from main import MainWindow, pin_hash
from store import Store


class MainWindowSmokeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.store = Store(self.root / "shop.db")
        salt, digest = pin_hash("123456")
        self.store.db.execute(
            "INSERT INTO owner(id,shop_name,pin_salt,pin_hash,created_at) VALUES(1,?,?,?,?)",
            ("Demonstration Shop", salt, digest, "2026-11-24"),
        )
        self.store.db.commit()
        self.shop = {"id": "smoke", "name": "Demonstration Shop",
                     "path": str(self.root / "shop.db")}

    def tearDown(self):
        if hasattr(self, "window"):
            self.window.close()
            self.app.processEvents()
        else:
            self.store.close()
        self.temp.cleanup()

    def test_owner_window_initializes_dashboard_and_all_pages(self):
        self.window = MainWindow(self.store, "smoke", "Demonstration Shop", [self.shop])
        self.assertEqual(self.window.pages.currentIndex(), self.window.page_index["Dashboard"])
        self.assertEqual(self.window.pages.count(), 8)
        self.assertFalse(self.window.nav_buttons["Customers"].isHidden())
        self.assertFalse(self.window.nav_buttons["Reports"].isHidden())

    def test_staff_window_hides_owner_pages_and_cost_controls(self):
        self.store.add_customer("Ali", "03001234567")
        self.window = MainWindow(self.store, "smoke", "Demonstration Shop", [self.shop], staff_mode=True)
        self.assertTrue(self.window.nav_buttons["Suppliers"].isHidden())
        self.assertTrue(self.window.nav_buttons["Reports"].isHidden())
        self.assertTrue(self.window.nav_buttons["Settings"].isHidden())
        self.assertTrue(self.window.product_add_button.isHidden())
        self.assertTrue(self.window.edit_entry_button.isHidden())
        self.assertFalse(self.window.nav_buttons["Customers"].isHidden())


if __name__ == "__main__":
    unittest.main()
