"""Regression tests for AI Khata's private shop ledger and APIs.

Run with: python -m unittest discover -s tests -v
"""
import io
import os
import sqlite3
import tempfile
import unittest
from unittest.mock import patch
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

    def test_manual_credit_and_payment_calculate_the_balance(self):
        self.create_owner()
        token = self.csrf(self.client, "/dashboard")
        created = self.client.post(
            "/customers/new",
            data={"csrf_token": token, "name": "Hassan", "phone": "03001234567"},
        )
        self.assertEqual(created.status_code, 302)
        self.client.post(
            "/customers/1",
            data={
                "csrf_token": token, "transaction_type": "credit",
                "amount": "750.00", "transaction_date": "2026-11-16", "description": "Flour",
            },
        )
        self.client.post(
            "/customers/1",
            data={
                "csrf_token": token, "transaction_type": "payment",
                "amount": "250.00", "transaction_date": "2026-11-16", "description": "Cash received",
            },
        )
        rows = self.db_rows(
            """SELECT
                 SUM(CASE WHEN transaction_type='credit' THEN amount ELSE 0 END) AS credits,
                 SUM(CASE WHEN transaction_type='payment' THEN amount ELSE 0 END) AS payments
               FROM transactions"""
        )
        self.assertEqual(rows[0]["credits"] - rows[0]["payments"], 500.0)

    def test_access_code_change_requires_current_code(self):
        self.create_owner()
        account = self.db_rows("SELECT password_hash FROM owner WHERE id=1")[0]
        token = self.csrf(self.client, "/settings")
        denied = self.client.post(
            "/settings",
            data={
                "csrf_token": token, "shop_name": "Noor General Store",
                "current_passcode": "incorrect", "new_passcode": "654321",
                "confirm_passcode": "654321",
            },
        )
        self.assertEqual(denied.status_code, 302)
        unchanged = self.db_rows("SELECT password_hash FROM owner WHERE id=1")[0]
        self.assertEqual(account["password_hash"], unchanged["password_hash"])

        self.client.post(
            "/settings",
            data={
                "csrf_token": token, "shop_name": "Noor General Store",
                "current_passcode": "123456", "new_passcode": "654321",
                "confirm_passcode": "654321",
            },
        )
        changed = self.db_rows("SELECT password_hash FROM owner WHERE id=1")[0]
        self.assertTrue(khata.check_password_hash(changed["password_hash"], "654321"))
        self.assertFalse(khata.check_password_hash(changed["password_hash"], "123456"))

    def test_mobile_ai_endpoint_uses_server_key_and_rate_limits_install(self):
        with khata.app.app_context():
            khata.get_db().execute("DELETE FROM mobile_ai_requests")
            khata.get_db().commit()
        previous_limit = khata.MOBILE_RATE_LIMIT_PER_HOUR
        khata.MOBILE_RATE_LIMIT_PER_HOUR = 1
        image = bytes([255, 216, 255]) + b"test-image-payload"
        raw = '{"entries":[{"customer_name":"Amina","date":"2026-11-16","description":"Rice","amount":450,"type":"credit"}]}'
        try:
            with patch.object(khata, "ai_is_ready", return_value=True), patch.object(
                khata, "extract_with_gemini", return_value=raw
            ):
                first = self.client.post(
                    "/api/mobile/extract",
                    data={"ledger_image": (io.BytesIO(image), "page.jpg", "image/jpeg")},
                    headers={"X-Install-ID": "test-install-identifier-001"},
                    content_type="multipart/form-data",
                )
                self.assertEqual(first.status_code, 200)
                self.assertTrue(first.get_json()["review_required"])
                self.assertEqual(first.get_json()["entries"][0]["customer_name"], "Amina")
                second = self.client.post(
                    "/api/mobile/extract",
                    data={"ledger_image": (io.BytesIO(image), "page.jpg", "image/jpeg")},
                    headers={"X-Install-ID": "test-install-identifier-001"},
                    content_type="multipart/form-data",
                )
                self.assertEqual(second.status_code, 429)
        finally:
            khata.MOBILE_RATE_LIMIT_PER_HOUR = previous_limit

    def test_gemini_key_list_splits_commas_semicolons_and_newlines(self):
        with patch.dict(os.environ, {
            "GEMINI_API_KEYS": "first-key; second-key\nthird-key",
            "GEMINI_API_KEY": "fourth-key",
        }, clear=False):
            keys = khata.gemini_api_keys()
        self.assertEqual(keys, ["first-key", "second-key", "third-key", "fourth-key"])

    def test_gemini_tries_another_key_and_sends_key_in_header(self):
        first = type("Response", (), {"status_code": 403})()
        second = type("Response", (), {"status_code": 404})()
        third = type("Response", (), {
            "status_code": 200,
            "json": lambda self: {"candidates": [{"content": {"parts": [{"text": '{"entries":[]}'}]}}]},
        })()
        with patch.dict(os.environ, {
            "GEMINI_API_KEYS": "bad-key; good-key",
            "GEMINI_API_KEY": "",
            "GEMINI_MODELS": "model-one,model-two",
        }), patch.object(khata.requests, "post", side_effect=[first, second, third]) as request:
            answer = khata.extract_with_gemini(b"image-bytes", "image/jpeg")
        self.assertIn('"entries"', answer)
        self.assertEqual(request.call_args_list[0].kwargs["headers"]["x-goog-api-key"], "bad-key")
        self.assertEqual(request.call_args_list[2].kwargs["headers"]["x-goog-api-key"], "good-key")
        self.assertNotIn("key=", request.call_args_list[2].args[0])
        self.assertIn("responseSchema", request.call_args_list[2].kwargs["json"]["generationConfig"])

    def test_integer_paisa_migration_preserves_customer_and_transaction_ids(self):
        db = sqlite3.connect(":memory:")
        db.execute("PRAGMA foreign_keys = ON")
        db.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
        db.execute(
            "CREATE TABLE transactions (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE, transaction_date TEXT NOT NULL, transaction_type TEXT NOT NULL, description TEXT NOT NULL, amount REAL NOT NULL, created_at TEXT NOT NULL)"
        )
        db.execute("INSERT INTO customers(id, name) VALUES (7, 'Amina')")
        db.execute(
            "INSERT INTO transactions(id, customer_id, transaction_date, transaction_type, description, amount, created_at) VALUES (19, 7, '2026-11-16', 'credit', 'Rice', 1234.50, '2026-11-16T10:00:00')"
        )
        db.execute(
            "INSERT INTO transactions(id, customer_id, transaction_date, transaction_type, description, amount, created_at) VALUES (20, 7, '2026-11-16', 'credit', 'Small amount', 0.001, '2026-11-16T10:01:00')"
        )
        db.execute(
            "INSERT INTO transactions(id, customer_id, transaction_date, transaction_type, description, amount, created_at) VALUES (21, 7, '2026-11-16', 'credit', 'Half-up amount', 1.005, '2026-11-16T10:02:00')"
        )
        db.execute(
            "CREATE TABLE transactions_new (id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE, transaction_date TEXT NOT NULL, transaction_type TEXT NOT NULL, description TEXT NOT NULL, amount_paisa INTEGER NOT NULL CHECK(amount_paisa > 0), created_at TEXT NOT NULL)"
        )
        db.execute(
            "INSERT INTO transactions_new (id, customer_id, transaction_date, transaction_type, description, amount_paisa, created_at) SELECT id, customer_id, transaction_date, transaction_type, description, MAX(1, CAST(ROUND(amount * 100.0 + 0.000001) AS INTEGER)), created_at FROM transactions WHERE amount > 0"
        )
        db.execute("DROP TABLE transactions")
        db.execute("ALTER TABLE transactions_new RENAME TO transactions")
        db.execute("CREATE INDEX idx_local_tx_customer_date ON transactions(customer_id, transaction_date)")
        rows = db.execute(
            "SELECT id, customer_id, amount_paisa, description FROM transactions ORDER BY id"
        ).fetchall()
        self.assertEqual(rows[0], (19, 7, 123450, "Rice"))
        self.assertEqual(rows[1], (20, 7, 1, "Small amount"))
        self.assertEqual(rows[2], (21, 7, 101, "Half-up amount"))
        self.assertEqual(db.execute("SELECT name FROM customers WHERE id=7").fetchone()[0], "Amina")
        self.assertEqual(db.execute("PRAGMA foreign_key_check").fetchall(), [])
        db.close()

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
