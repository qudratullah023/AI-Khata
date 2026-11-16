"""Regression tests for AI Khata's private shop ledger and APIs.

Run with: python -m unittest discover -s tests -v
"""
import io
import os
import tempfile
import unittest
from pathlib import Path

_TEST_HOME = tempfile.TemporaryDirectory(prefix="ai-khata-tests-")
os.environ["DATABASE_PATH"] = str(Path(_TEST_HOME.name) / "test.db")
os.environ["SECRET_KEY"] = "test-only-secret-key-never-use-in-production"
os.environ.pop("GEMINI_API_KEY", None)
os.environ.pop("OPENROUTER_API_KEY", None)

import app as khata  # noqa: E402


class AIKhataTests(unittest.TestCase):
    def setUp(self):
        self.db_path = Path(khata.DATABASE)
        if self.db_path.exists():
            self.db_path.unlink()
        with khata.app.app_context():
            khata.init_db()
        khata.app.config.update(TESTING=True, WTF_CSRF_ENABLED=False)
        self.client = khata.app.test_client()

    def csrf(self, client, page="/setup"):
        response = client.get(page)
        self.assertIn(response.status_code, (200, 302))
        with client.session_transaction() as session:
            return session["csrf_token"]

    def create_owner(self):
        token = self.csrf(self.client)
        response = self.client.post(
            "/setup",
            data={
                "csrf_token": token,
                "shop_name": "Noor General Store",
                "username": "noorstore",
                "passcode": "123456",
                "confirm_passcode": "123456",
            },
        )
        self.assertEqual(response.status_code, 302)
        return self.client

    def db_rows(self, sql, params=()):
        with khata.app.app_context():
            return khata.get_db().execute(sql, params).fetchall()

    def test_first_run_setup_hashes_passcode(self):
        self.create_owner()
        row = self.db_rows("SELECT username, password_hash FROM owner WHERE id=1")[0]
        self.assertEqual(row["username"], "noorstore")
        self.assertNotEqual(row["password_hash"], "123456")
        self.assertTrue(khata.check_password_hash(row["password_hash"], "123456"))
        self.assertEqual(self.client.get("/dashboard").status_code, 200)

    def test_private_pages_redirect_and_private_api_returns_json_401(self):
        self.create_owner()
        locked_client = khata.app.test_client()
        self.assertEqual(locked_client.get("/dashboard").status_code, 302)
        token = self.csrf(locked_client, "/login")
        response = locked_client.post(
            "/api/transactions/bulk",
            json={"entries": [{"customer_name": "Amina", "amount": 100, "type": "credit"}]},
            headers={"X-CSRF-Token": token},
        )
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.get_json()["error"], "Your shop is locked. Please unlock it again.")

    def test_write_forms_require_csrf(self):
        self.create_owner()
        response = self.client.post("/customers/new", data={"name": "Amina"})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(len(self.db_rows("SELECT id FROM customers")), 0)

    def test_bulk_import_is_atomic_when_one_row_is_invalid(self):
        self.create_owner()
        token = self.csrf(self.client, "/dashboard")
        response = self.client.post(
            "/api/transactions/bulk",
            json={"entries": [
                {"customer_name": "Amina", "date": "2026-11-16", "description": "Rice", "amount": 500, "type": "credit"},
                {"customer_name": "Bilal", "date": "2026-11-16", "description": "Oil", "amount": float("nan"), "type": "credit"},
            ]},
            headers={"X-CSRF-Token": token},
        )
        self.assertEqual(response.status_code, 400)
        self.assertEqual(len(self.db_rows("SELECT id FROM customers")), 0)
        self.assertEqual(len(self.db_rows("SELECT id FROM transactions")), 0)

    def test_bulk_import_saves_confirmed_credit_and_payment(self):
        self.create_owner()
        token = self.csrf(self.client, "/dashboard")
        response = self.client.post(
            "/api/transactions/bulk",
            json={"entries": [
                {"customer_name": "Amina", "date": "2026-11-16", "description": "Groceries", "amount": 500, "type": "credit"},
                {"customer_name": "Amina", "date": "2026-11-16", "description": "Cash received", "amount": 200, "type": "payment"},
            ]},
            headers={"X-CSRF-Token": token},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["saved"], 2)
        rows = self.db_rows(
            "SELECT transaction_type, amount FROM transactions ORDER BY id"
        )
        self.assertEqual([(r["transaction_type"], r["amount"]) for r in rows], [("credit", 500.0), ("payment", 200.0)])

    def test_uploaded_file_must_really_be_an_image(self):
        self.create_owner()
        token = self.csrf(self.client, "/dashboard")
        response = self.client.post(
            "/api/extract",
            data={"ledger_image": (io.BytesIO(b"this is not a jpeg"), "ledger.jpg", "image/jpeg")},
            headers={"X-CSRF-Token": token},
            content_type="multipart/form-data",
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("contents do not match", response.get_json()["error"])

    def test_sixth_failed_login_is_temporarily_blocked(self):
        self.create_owner()
        locked_client = khata.app.test_client()
        token = self.csrf(locked_client, "/login")
        for _ in range(5):
            response = locked_client.post(
                "/login",
                data={"csrf_token": token, "username": "noorstore", "passcode": "wrong-code"},
            )
            self.assertEqual(response.status_code, 200)
        response = locked_client.post(
            "/login",
            data={"csrf_token": token, "username": "noorstore", "passcode": "wrong-code"},
        )
        self.assertEqual(response.status_code, 429)


if __name__ == "__main__":
    unittest.main()
