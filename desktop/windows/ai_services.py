"""Optional Gemini-powered bookkeeping tools for the desktop edition."""
from __future__ import annotations

import base64
import ctypes
import json
import mimetypes
import os
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

DEFAULT_MODELS = ("gemini-2.5-flash", "gemini-2.0-flash")
API_ROOT = "https://generativelanguage.googleapis.com/v1beta/models"


class _DataBlob(ctypes.Structure):
    _fields_ = [("cbData", ctypes.c_uint32), ("pbData", ctypes.POINTER(ctypes.c_ubyte))]


def _dpapi(data: bytes, protect: bool) -> bytes:
    if os.name != "nt":
        raise RuntimeError("Windows data protection is only available on Windows.")
    source_buffer = ctypes.create_string_buffer(data)
    source = _DataBlob(len(data), ctypes.cast(source_buffer, ctypes.POINTER(ctypes.c_ubyte)))
    target = _DataBlob()
    crypt = ctypes.windll.crypt32
    kernel = ctypes.windll.kernel32
    crypt.CryptProtectData.argtypes = [ctypes.POINTER(_DataBlob), ctypes.c_wchar_p,
        ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, ctypes.POINTER(_DataBlob)]
    crypt.CryptProtectData.restype = ctypes.c_int
    crypt.CryptUnprotectData.argtypes = [ctypes.POINTER(_DataBlob), ctypes.POINTER(ctypes.c_wchar_p),
        ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32, ctypes.POINTER(_DataBlob)]
    crypt.CryptUnprotectData.restype = ctypes.c_int
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    if protect:
        ok = crypt.CryptProtectData(ctypes.byref(source), "AI Khata Desktop", None, None, None, 0, ctypes.byref(target))
    else:
        ok = crypt.CryptUnprotectData(ctypes.byref(source), None, None, None, None, 0, ctypes.byref(target))
    if not ok:
        raise OSError("Windows could not protect or unlock the AI service key.")
    try:
        return ctypes.string_at(target.pbData, target.cbData)
    finally:
        kernel.LocalFree(target.pbData)


def protect_api_key(key: str) -> str:
    value = key.strip()
    if not value:
        return ""
    if os.name == "nt":
        encrypted = _dpapi(value.encode("utf-8"), True)
        return "dpapi:" + base64.b64encode(encrypted).decode("ascii")
    raise RuntimeError("AI key protection requires Windows Data Protection API.")


def reveal_api_key(stored: str) -> str:
    if not stored:
        return ""
    if not stored.startswith("dpapi:"):
        raise ValueError("The saved AI key is not protected in this Windows profile.")
    data = base64.b64decode(stored[6:], validate=True)
    return _dpapi(data, False).decode("utf-8")


def _request(model: str, api_key: str, parts: list[dict[str, Any]], timeout: int = 75) -> str:
    url = f"{API_ROOT}/{urllib.parse.quote(model, safe='')}:generateContent?key={urllib.parse.quote(api_key)}"
    body = {
        "contents": [{"role": "user", "parts": parts}],
        "generationConfig": {"temperature": 0.15, "responseMimeType": "application/json"},
    }
    request = urllib.request.Request(
        url, data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": "AI-Khata-Desktop/1.0"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read(4096).decode("utf-8", "replace")
        raise RuntimeError(f"AI service returned HTTP {exc.code}: {detail[:700]}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Could not reach the AI service: {exc.reason}") from exc
    try:
        return "".join(part.get("text", "") for part in result["candidates"][0]["content"]["parts"]).strip()
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError("The AI service returned an empty or unexpected response.") from exc


def _parse_json(text: str) -> Any:
    cleaned = text.strip()
    cleaned = re.sub(r"^\s*```(?:json)?\s*", "", cleaned, flags=re.I)
    cleaned = re.sub(r"\s*```\s*$", "", cleaned)
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start >= 0 and end > start:
            return json.loads(cleaned[start:end + 1])
        start = cleaned.find("[")
        end = cleaned.rfind("]")
        if start >= 0 and end > start:
            return json.loads(cleaned[start:end + 1])
        raise ValueError("The AI response did not contain valid structured data.")


def _with_fallback(api_keys: str | list[str] | tuple[str, ...], parts: list[dict[str, Any]],
                   models: tuple[str, ...] = DEFAULT_MODELS) -> str:
    keys = [api_keys] if isinstance(api_keys, str) else list(api_keys)
    failures = []
    for key_index, key in enumerate(keys, 1):
        for model in models:
            try:
                return _request(model, key, parts)
            except Exception as exc:
                failures.append(f"key {key_index}/{len(keys)}, {model}: {exc}")
    raise RuntimeError("All configured AI keys and models failed. " + " | ".join(failures))


def ask_bookkeeper(api_keys: str | list[str] | tuple[str, ...], question: str, context: dict[str, Any],
                   models: tuple[str, ...] = DEFAULT_MODELS) -> dict[str, Any]:
    prompt = (
        "You are the bookkeeping assistant inside a local shop management desktop application. "
        "Use only the supplied ledger evidence for factual ledger answers. Do not invent totals or transactions. "
        "You may explain a calculation, identify ambiguity, and suggest one draft entry. Never claim you saved anything. "
        "Return JSON with keys answer (string), draft (object or null). "
        "A draft object must have type credit or payment, amount (positive number), description (string), "
        "customer_id (integer or null), and date (YYYY-MM-DD or null). "
        "Only propose a draft when the user clearly asks to record a transaction and the amount/customer are clear. "
        "If anything is unclear, set draft to null and ask a concise question in answer. "
        f"\nLEDGER CONTEXT (read-only): {json.dumps(context, ensure_ascii=False)}"
        f"\nUSER QUESTION: {question}"
    )
    text = _with_fallback(api_keys, [{"text": prompt}], models)
    data = _parse_json(text)
    if not isinstance(data, dict) or not isinstance(data.get("answer"), str):
        raise ValueError("AI response is missing its answer.")
    draft = data.get("draft")
    if draft is not None:
        if not isinstance(draft, dict) or draft.get("type") not in ("credit", "payment"):
            raise ValueError("AI transaction draft has an invalid type.")
        try:
            amount = float(draft.get("amount"))
        except (TypeError, ValueError) as exc:
            raise ValueError("AI transaction draft has an invalid amount.") from exc
        if not 0 < amount <= 100_000_000:
            raise ValueError("AI transaction draft amount is outside the accepted range.")
        draft["amount"] = amount
        if not isinstance(draft.get("description", ""), str):
            raise ValueError("AI transaction description is invalid.")
    return {"answer": data["answer"], "draft": draft}


def read_ledger_photo(api_keys: str | list[str] | tuple[str, ...], image_path: str | Path,
                      models: tuple[str, ...] = DEFAULT_MODELS) -> dict[str, Any]:
    path = Path(image_path)
    if not path.is_file() or path.stat().st_size > 12 * 1024 * 1024:
        raise ValueError("Choose a valid image smaller than 12 MB.")
    mime = mimetypes.guess_type(path.name)[0] or "image/jpeg"
    if mime not in ("image/jpeg", "image/png", "image/webp"):
        raise ValueError("Use a JPG, PNG or WebP ledger photo.")
    prompt = (
        "Read this handwritten shop ledger image. Extract only entries that are clearly visible. "
        "Do not guess unclear digits or names. Return JSON: {entries:[{customer_name:string, "
        "date:string|null, type:'credit'|'payment', amount:number|null, description:string, confidence:number, "
        "needs_review:boolean}], notes:string}. Amounts must be positive numbers or null. "
        "Use ISO dates when clear. Set needs_review true and amount null when uncertain. "
        "Do not create records or claim that anything has been saved."
    )
    parts = [
        {"text": prompt},
        {"inline_data": {"mime_type": mime, "data": base64.b64encode(path.read_bytes()).decode("ascii")}},
    ]
    data = _parse_json(_with_fallback(api_keys, parts, models))
    if not isinstance(data, dict) or not isinstance(data.get("entries"), list):
        raise ValueError("The scan response did not contain a valid entries list.")
    cleaned = []
    for item in data["entries"][:250]:
        if not isinstance(item, dict) or item.get("type") not in ("credit", "payment"):
            continue
        amount = item.get("amount")
        try:
            amount = float(amount) if amount is not None else None
        except (TypeError, ValueError):
            amount = None
        if amount is not None and not 0 < amount <= 100_000_000:
            amount = None
        try:
            confidence = float(item.get("confidence", 0) or 0)
        except (TypeError, ValueError):
            confidence = 0.0
        if not 0.0 <= confidence <= 1.0:
            confidence = 0.0
        entry_date = str(item.get("date") or "")[:10]
        if entry_date:
            try:
                from datetime import datetime
                datetime.strptime(entry_date, "%Y-%m-%d")
            except ValueError:
                entry_date = ""
        cleaned.append({
            "customer_name": str(item.get("customer_name", "")).strip()[:100],
            "date": entry_date,
            "type": item["type"], "amount": amount,
            "description": str(item.get("description", "")).strip()[:240],
            "confidence": confidence,
            "needs_review": bool(item.get("needs_review", False)) or amount is None or confidence < 0.6
                            or not str(item.get("customer_name", "")).strip() or not entry_date,
        })
    return {"entries": cleaned, "notes": str(data.get("notes", ""))[:1000]}
