import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import ai_services


class AiServiceTests(unittest.TestCase):
    def test_parse_json_accepts_markdown_fence(self):
        fences = chr(96) * 3
        sample = fences + "json" + chr(10) + '{"answer":"ok","draft":null}' + chr(10) + fences
        self.assertEqual(ai_services._parse_json(sample), {"answer": "ok", "draft": None})

    def test_parse_json_rejects_non_json(self):
        with self.assertRaises(ValueError):
            ai_services._parse_json("The model returned a paragraph without structured data.")

    def test_fallback_tries_second_key_and_next_model(self):
        calls = []
        def fake_request(model, key, parts, timeout=75):
            calls.append((model, key))
            if key == "first" or model == "gemini-2.5-flash":
                raise RuntimeError("temporary failure")
            return '{"answer":"success"}'
        with patch.object(ai_services, "_request", side_effect=fake_request):
            result = ai_services._with_fallback(["first", "second"], [{"text": "hello"}],
                                                ("gemini-2.5-flash", "gemini-2.0-flash"))
        self.assertEqual(result, '{"answer":"success"}')
        self.assertEqual(calls, [
            ("gemini-2.5-flash", "first"),
            ("gemini-2.0-flash", "first"),
            ("gemini-2.5-flash", "second"),
            ("gemini-2.0-flash", "second"),
        ])

    def test_bookkeeper_rejects_invalid_transaction_draft(self):
        with patch.object(ai_services, "_with_fallback",
                          return_value='{"answer":"Ready","draft":{"type":"delete","amount":10}}'):
            with self.assertRaises(ValueError):
                ai_services.ask_bookkeeper("test-key", "Record this", {"customer": None})

    def test_bookkeeper_chat_only_result(self):
        with patch.object(ai_services, "_with_fallback",
                          return_value='{"answer":"Balance is based on provided entries.","draft":null}'):
            result = ai_services.ask_bookkeeper("test-key", "What is the balance?", {"customer": None})
        self.assertEqual(result["answer"], "Balance is based on provided entries.")
        self.assertIsNone(result["draft"])

    def test_photo_scan_requires_supported_file_type(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "ledger.txt"
            path.write_text("not an image")
            with self.assertRaises(ValueError):
                ai_services.read_ledger_photo("test-key", path)

    def test_photo_scan_marks_unclear_and_invalid_fields_for_manual_review(self):
        response = {
            "entries": [
                {"customer_name": "Ali", "date": "2026-11-20", "type": "credit",
                 "amount": 100, "confidence": "high", "needs_review": False},
                {"customer_name": "", "date": "not-a-date", "type": "payment",
                 "amount": None, "confidence": 1.2, "needs_review": False},
            ],
            "notes": "Review unclear rows"
        }
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "ledger.png"
            path.write_bytes(bytes([137, 80, 78, 71, 13, 10, 26, 10]) + b"image")
            with patch.object(ai_services, "_with_fallback", return_value=json.dumps(response)):
                result = ai_services.read_ledger_photo("test-key", path)
        self.assertEqual(result["entries"][0]["confidence"], 0.0)
        self.assertTrue(result["entries"][0]["needs_review"])
        self.assertTrue(result["entries"][1]["needs_review"])
        self.assertEqual(result["entries"][1]["date"], "")


if __name__ == "__main__":
    unittest.main()
