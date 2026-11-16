import base64
import hashlib
import json
import math
import os
import re
import secrets
import sqlite3
from datetime import date, datetime, timedelta
from functools import wraps
from pathlib import Path

import requests
from flask import Flask, abort, flash, g, jsonify, redirect, render_template, request, session, url_for
from werkzeug.security import check_password_hash, generate_password_hash
from werkzeug.utils import secure_filename

BASE_DIR = Path(__file__).resolve().parent
INSTANCE_DIR = BASE_DIR / "instance"
DATABASE = Path(os.environ.get("DATABASE_PATH", INSTANCE_DIR / "ai_khata.db"))
MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MOBILE_RATE_LIMIT_PER_HOUR = int(os.environ.get("MOBILE_RATE_LIMIT_PER_HOUR", "8"))

app = Flask(__name__)
app.secret_key = os.environ.get("SECRET_KEY") or secrets.token_hex(32)
app.config.update(
    MAX_CONTENT_LENGTH=MAX_UPLOAD_BYTES,
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE="Lax",
    SESSION_COOKIE_SECURE=os.environ.get("COOKIE_SECURE", "0") == "1",
    PERMANENT_SESSION_LIFETIME=timedelta(hours=12),
)
if not os.environ.get("SECRET_KEY"):
    app.logger.warning("SECRET_KEY is not set. Set a persistent random value before deployment.")


def get_db():
    if "db" not in g:
        DATABASE.parent.mkdir(parents=True, exist_ok=True)
        g.db = sqlite3.connect(DATABASE)
        g.db.row_factory = sqlite3.Row
        g.db.execute("PRAGMA foreign_keys = ON")
    return g.db


@app.teardown_appcontext
def close_db(_error=None):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def init_db():
    db = get_db()
    db.executescript(
        """
        CREATE TABLE IF NOT EXISTS owner (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            shop_name TEXT NOT NULL,
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS customers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            phone TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
            transaction_date TEXT NOT NULL,
            transaction_type TEXT NOT NULL CHECK (transaction_type IN ('credit', 'payment')),
            description TEXT NOT NULL DEFAULT '',
            amount REAL NOT NULL CHECK (amount > 0),
            created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS login_attempts (
            ip TEXT PRIMARY KEY,
            failed_count INTEGER NOT NULL DEFAULT 0,
            window_start TEXT NOT NULL,
            locked_until TEXT
        );
        CREATE TABLE IF NOT EXISTS mobile_ai_requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            bucket_hash TEXT NOT NULL,
            requested_at TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_mobile_ai_requests_bucket_time
        ON mobile_ai_requests(bucket_hash, requested_at);
        CREATE INDEX IF NOT EXISTS idx_transactions_customer_date
        ON transactions(customer_id, transaction_date);
        """
    )
    db.commit()


with app.app_context():
    init_db()


def now_iso():
    return datetime.utcnow().replace(microsecond=0).isoformat()


def csrf_token():
    if "csrf_token" not in session:
        session["csrf_token"] = secrets.token_urlsafe(32)
    return session["csrf_token"]


app.jinja_env.globals["csrf_token"] = csrf_token


@app.before_request
def protect_requests():
    if session.get("owner_id"):
        last = session.get("last_activity")
        if last:
            try:
                if datetime.utcnow() - datetime.fromisoformat(last) > timedelta(hours=12):
                    session.clear()
                    flash("Your session expired. Please unlock your shop again.", "info")
            except ValueError:
                session.clear()
        if session.get("owner_id"):
            session["last_activity"] = now_iso()

    if request.path == "/api/mobile/extract":
        return
    if request.method in {"POST", "PUT", "PATCH", "DELETE"}:
        supplied = request.form.get("csrf_token") or request.headers.get("X-CSRF-Token", "")
        expected = session.get("csrf_token", "")
        if not supplied or not expected or not secrets.compare_digest(supplied, expected):
            abort(400, description="Security token expired. Refresh the page and try again.")


@app.after_request
def security_headers(response):
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "SAMEORIGIN"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    response.headers["Permissions-Policy"] = "camera=(self)"
    response.headers["Cache-Control"] = "no-store" if request.endpoint not in {"static"} else "public, max-age=3600"
    return response


def owner_exists():
    return get_db().execute("SELECT 1 FROM owner WHERE id = 1").fetchone() is not None


def login_required(view):
    @wraps(view)
    def wrapped(*args, **kwargs):
        if not session.get("owner_id"):
            if request.path.startswith("/api/"):
                return jsonify({"error": "Your shop is locked. Please unlock it again."}), 401
            return redirect(url_for("login"))
        return view(*args, **kwargs)
    return wrapped


def owner():
    return get_db().execute("SELECT * FROM owner WHERE id = 1").fetchone()


def passcode_is_valid(value):
    return (value.isdigit() and len(value) >= 6) or (len(value) >= 8)


@app.template_filter("money")
def money(value):
    try:
        return "Rs. {:,.2f}".format(float(value or 0))
    except (TypeError, ValueError):
        return "Rs. 0.00"


@app.template_filter("date_display")
def date_display(value):
    try:
        return date.fromisoformat(value).strftime("%d %b %Y")
    except (TypeError, ValueError):
        return value or "—"


@app.context_processor
def common_template_values():
    shop = owner() if owner_exists() else None
    return {"shop": shop, "today": date.today().isoformat()}


@app.get("/health")
def health():
    return jsonify({"status": "ok", "service": "AI Khata"})


@app.get("/")
def index():
    if not owner_exists():
        return redirect(url_for("setup"))
    if session.get("owner_id"):
        return redirect(url_for("dashboard"))
    return redirect(url_for("login"))


@app.route("/setup", methods=["GET", "POST"])
def setup():
    if owner_exists():
        return redirect(url_for("login"))
    if request.method == "POST":
        shop_name = request.form.get("shop_name", "").strip()[:80]
        username = request.form.get("username", "").strip().lower()[:40]
        passcode = request.form.get("passcode", "")
        confirm = request.form.get("confirm_passcode", "")
        if not shop_name or not re.fullmatch(r"[a-z0-9_.-]{3,40}", username):
            flash("Enter a shop name and a username with at least 3 letters or numbers.", "error")
        elif not passcode_is_valid(passcode):
            flash("Use a PIN of at least 6 digits or a password of at least 8 characters.", "error")
        elif passcode != confirm:
            flash("The PIN/password confirmation does not match.", "error")
        else:
            db = get_db()
            db.execute(
                "INSERT INTO owner (id, shop_name, username, password_hash, created_at) VALUES (1, ?, ?, ?, ?)",
                (shop_name, username, generate_password_hash(passcode), now_iso()),
            )
            db.commit()
            session.clear()
            session["owner_id"] = 1
            session["username"] = username
            session.permanent = True
            csrf_token()
            flash("Your private shop account is ready. Keep your PIN/password secret.", "success")
            return redirect(url_for("dashboard"))
    return render_template("setup.html")


def login_is_locked(ip):
    row = get_db().execute("SELECT locked_until FROM login_attempts WHERE ip=?", (ip,)).fetchone()
    if not row or not row["locked_until"]:
        return False
    try:
        return datetime.fromisoformat(row["locked_until"]) > datetime.utcnow()
    except ValueError:
        return False


def record_failed_login(ip):
    db = get_db()
    now = datetime.utcnow()
    row = db.execute("SELECT * FROM login_attempts WHERE ip=?", (ip,)).fetchone()
    if not row:
        db.execute(
            "INSERT INTO login_attempts(ip, failed_count, window_start) VALUES (?, 1, ?)",
            (ip, now.isoformat()),
        )
    else:
        try:
            start = datetime.fromisoformat(row["window_start"])
        except ValueError:
            start = now - timedelta(minutes=20)
        count = 1 if now - start > timedelta(minutes=15) else row["failed_count"] + 1
        window_start = now if count == 1 else start
        locked_until = (now + timedelta(minutes=15)).isoformat() if count >= 5 else None
        db.execute(
            "UPDATE login_attempts SET failed_count=?, window_start=?, locked_until=? WHERE ip=?",
            (count, window_start.isoformat(), locked_until, ip),
        )
    db.commit()


@app.route("/login", methods=["GET", "POST"])
def login():
    if not owner_exists():
        return redirect(url_for("setup"))
    if session.get("owner_id"):
        return redirect(url_for("dashboard"))
    if request.method == "POST":
        ip = request.remote_addr or "unknown"
        if login_is_locked(ip):
            flash("Too many failed attempts. Please wait 15 minutes before trying again.", "error")
            return render_template("login.html"), 429
        account = owner()
        username = request.form.get("username", "").strip().lower()
        passcode = request.form.get("passcode", "")
        if account and secrets.compare_digest(username, account["username"]) and check_password_hash(account["password_hash"], passcode):
            db = get_db()
            db.execute("DELETE FROM login_attempts WHERE ip=?", (ip,))
            db.commit()
            session.clear()
            session["owner_id"] = 1
            session["username"] = account["username"]
            session.permanent = True
            csrf_token()
            return redirect(url_for("dashboard"))
        record_failed_login(ip)
        flash("That username or PIN/password is not correct.", "error")
    return render_template("login.html")


@app.post("/logout")
@login_required
def logout():
    session.clear()
    flash("Your shop is locked. Sign in to access it again.", "success")
    return redirect(url_for("login"))


@app.get("/dashboard")
@login_required
def dashboard():
    db = get_db()
    summary = db.execute(
        """SELECT
             COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount ELSE 0 END), 0) AS credit_total,
             COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount ELSE 0 END), 0) AS payment_total,
             COUNT(*) AS transaction_count
           FROM transactions"""
    ).fetchone()
    month = date.today().strftime("%Y-%m")
    monthly = db.execute(
        """SELECT
             COALESCE(SUM(CASE WHEN transaction_type='credit' THEN amount ELSE 0 END), 0) AS credit_total,
             COALESCE(SUM(CASE WHEN transaction_type='payment' THEN amount ELSE 0 END), 0) AS payment_total
           FROM transactions WHERE substr(transaction_date, 1, 7)=?""",
        (month,),
    ).fetchone()
    recent = db.execute(
        """SELECT t.*, c.name AS customer_name FROM transactions t
           JOIN customers c ON c.id=t.customer_id
           ORDER BY t.transaction_date DESC, t.id DESC LIMIT 8"""
    ).fetchall()
    customer_count = db.execute("SELECT COUNT(*) AS n FROM customers").fetchone()["n"]
    return render_template(
        "dashboard.html", summary=summary, monthly=monthly, recent=recent,
        customer_count=customer_count,
        outstanding=float(summary["credit_total"] or 0) - float(summary["payment_total"] or 0),
    )


@app.get("/customers")
@login_required
def customers():
    q = request.args.get("q", "").strip()
    db = get_db()
    rows = db.execute(
        """SELECT c.*,
             COALESCE(SUM(CASE WHEN t.transaction_type='credit' THEN t.amount ELSE 0 END),0) -
             COALESCE(SUM(CASE WHEN t.transaction_type='payment' THEN t.amount ELSE 0 END),0) AS balance
           FROM customers c LEFT JOIN transactions t ON t.customer_id=c.id
           WHERE c.name LIKE ? OR c.phone LIKE ?
           GROUP BY c.id ORDER BY c.name COLLATE NOCASE""",
        (f"%{q}%", f"%{q}%"),
    ).fetchall()
    return render_template("customers.html", customers=rows, query=q)


@app.post("/customers/new")
@login_required
def customer_new():
    name = request.form.get("name", "").strip()[:100]
    phone = request.form.get("phone", "").strip()[:30]
    if not name:
        flash("Please enter the customer's name.", "error")
    else:
        db = get_db()
        cur = db.execute(
            "INSERT INTO customers(name, phone, created_at) VALUES (?, ?, ?)",
            (name, phone, now_iso()),
        )
        db.commit()
        flash("Customer added.", "success")
        return redirect(url_for("customer_detail", customer_id=cur.lastrowid))
    return redirect(url_for("customers"))


@app.route("/customers/<int:customer_id>", methods=["GET", "POST"])
@login_required
def customer_detail(customer_id):
    db = get_db()
    customer = db.execute("SELECT * FROM customers WHERE id=?", (customer_id,)).fetchone()
    if not customer:
        abort(404)
    if request.method == "POST":
        description = request.form.get("description", "").strip()[:240]
        transaction_type = request.form.get("transaction_type", "credit")
        amount_raw = request.form.get("amount", "").strip().replace(",", "")
        transaction_date = request.form.get("transaction_date", date.today().isoformat())
        try:
            amount = round(float(amount_raw), 2)
            date.fromisoformat(transaction_date)
            if not math.isfinite(amount) or amount <= 0 or amount > 100000000:
                raise ValueError
            if transaction_type not in {"credit", "payment"}:
                raise ValueError
        except ValueError:
            flash("Enter a valid date and an amount greater than zero.", "error")
            return redirect(url_for("customer_detail", customer_id=customer_id))
        db.execute(
            """INSERT INTO transactions(customer_id, transaction_date, transaction_type, description, amount, created_at)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (customer_id, transaction_date, transaction_type, description, amount, now_iso()),
        )
        db.commit()
        flash("Transaction saved.", "success")
        return redirect(url_for("customer_detail", customer_id=customer_id))
    history = db.execute(
        "SELECT * FROM transactions WHERE customer_id=? ORDER BY transaction_date DESC, id DESC",
        (customer_id,),
    ).fetchall()
    credit = sum(t["amount"] for t in history if t["transaction_type"] == "credit")
    payment = sum(t["amount"] for t in history if t["transaction_type"] == "payment")
    return render_template(
        "customer_detail.html", customer=customer, history=history,
        credit_total=credit, payment_total=payment, balance=credit-payment,
    )


@app.post("/transactions/<int:transaction_id>/delete")
@login_required
def transaction_delete(transaction_id):
    db = get_db()
    row = db.execute("SELECT customer_id FROM transactions WHERE id=?", (transaction_id,)).fetchone()
    if not row:
        abort(404)
    db.execute("DELETE FROM transactions WHERE id=?", (transaction_id,))
    db.commit()
    flash("Transaction deleted.", "success")
    return redirect(url_for("customer_detail", customer_id=row["customer_id"]))


@app.get("/scan")
@login_required
def scan():
    return render_template("scan.html", ai_provider=ai_provider_name(), ai_ready=ai_is_ready())


def ai_provider_name():
    return os.environ.get("AI_PROVIDER", "gemini").strip().lower()


def ai_is_ready():
    provider = ai_provider_name()
    if provider == "openrouter":
        return bool(os.environ.get("OPENROUTER_API_KEY"))
    return bool(gemini_api_keys())


EXTRACTION_PROMPT = """
Read this handwritten shop ledger page or bill. Extract only entries that are visibly supported by the image.
Return ONLY valid JSON in this exact shape:
{"entries":[{"customer_name":"name or empty string","date":"YYYY-MM-DD or empty string","description":"short item/note","amount":0,"type":"credit or payment"}]}
Rules:
- Never invent unreadable names, dates, quantities or amounts. Use an empty string or 0 when unclear.
- Use type "credit" when the customer received goods or owes the shop. Use "payment" when money was paid to the shop.
- One row per clear transaction. Do not add totals twice.
- Convert clear dates to YYYY-MM-DD. If year is absent, use the current year.
- Amount must be a plain number, with no currency symbol.
- If the page contains multiple customers, keep each customer's name with the correct row.
"""


def gemini_api_keys():
    values = []
    for source in (os.environ.get("GEMINI_API_KEYS", ""), os.environ.get("GEMINI_API_KEY", "")):
        values.extend(part.strip() for part in re.split(r"[,;\s]+", source) if part.strip())
    return list(dict.fromkeys(values))


def extract_with_gemini(image_bytes, mime_type):
    keys = gemini_api_keys()
    if not keys:
        raise RuntimeError("AI_NOT_CONFIGURED: No Gemini key is configured on the server.")
    models = [
        part.strip() for part in os.environ.get(
            "GEMINI_MODELS",
            "gemini-3.8-flash,gemini-3.7-flash,gemini-3.6-flash,gemini-3.5-flash,gemini-2.5-flash"
        ).split(",") if part.strip()
    ]
    schema = {
        "type": "OBJECT",
        "properties": {
            "entries": {
                "type": "ARRAY",
                "items": {
                    "type": "OBJECT",
                    "properties": {
                        "customer_name": {"type": "STRING"},
                        "date": {"type": "STRING"},
                        "description": {"type": "STRING"},
                        "amount": {"type": "NUMBER"},
                        "type": {"type": "STRING", "enum": ["credit", "payment"]},
                        "confidence": {"type": "NUMBER"},
                    },
                    "required": ["customer_name", "date", "description", "amount", "type", "confidence"],
                },
            },
            "page_total": {"type": "NUMBER"},
        },
        "required": ["entries"],
    }
    failures = []
    statuses = []
    timeout_count = 0
    for key in keys:
        for model in models:
            endpoint = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
            try:
                response = requests.post(
                    endpoint,
                    headers={"x-goog-api-key": key, "Content-Type": "application/json"},
                    json={
                        "contents": [{"parts": [
                            {"text": EXTRACTION_PROMPT + "\nRead Urdu script, Roman Urdu, English and mixed numerals. Ignore crossed-out entries. Do not count running totals or balances as new transactions. Include a confidence from 0 to 1 for every row and a page_total when clearly written. Current date: " + date.today().isoformat()},
                            {"inline_data": {"mime_type": mime_type, "data": base64.b64encode(image_bytes).decode("ascii")}},
                        ]}],
                        "generationConfig": {
                            "responseMimeType": "application/json",
                            "responseSchema": schema,
                            "temperature": 0.1,
                        },
                    },
                    timeout=70,
                )
                if response.status_code >= 400:
                    statuses.append(response.status_code)
                    failures.append(f"{model}: HTTP {response.status_code}")
                    continue
                payload = response.json()
                return payload["candidates"][0]["content"]["parts"][0]["text"]
            except requests.Timeout:
                timeout_count += 1
                failures.append(f"{model}: timeout")
            except requests.RequestException as exc:
                failures.append(f"{model}: network error ({type(exc).__name__})")
            except (KeyError, IndexError, ValueError) as exc:
                failures.append(f"{model}: invalid AI response ({type(exc).__name__})")

    if statuses and all(code in (401, 403) for code in statuses):
        raise RuntimeError("AI_INVALID_KEY: The configured Gemini key is invalid or not allowed to use this model.")
    if 429 in statuses:
        raise RuntimeError("AI_QUOTA: The configured Gemini service has reached its request or quota limit.")
    if timeout_count == len(failures) and timeout_count > 0:
        raise TimeoutError("AI_TIMEOUT: The AI service took too long to respond.")
    if statuses and all(code == 404 for code in statuses):
        raise RuntimeError("AI_MODEL_UNAVAILABLE: None of the configured Gemini model names is available.")
    raise RuntimeError("AI_TEMPORARILY_UNAVAILABLE: All configured Gemini models failed. " + ", ".join(failures[-6:]))

def extract_with_openrouter(image_bytes, mime_type):
    model = os.environ.get("OPENROUTER_MODEL", "google/gemini-2.5-flash")
    data_url = f"data:{mime_type};base64,{base64.b64encode(image_bytes).decode('ascii')}"
    response = requests.post(
        "https://openrouter.ai/api/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {os.environ['OPENROUTER_API_KEY']}",
            "Content-Type": "application/json",
            "HTTP-Referer": os.environ.get("APP_PUBLIC_URL", "https://ai-khata.onrender.com"),
            "X-Title": "AI Khata",
        },
        json={
            "model": model,
            "messages": [{"role": "user", "content": [
                {"type": "text", "text": EXTRACTION_PROMPT + "\nCurrent date: " + date.today().isoformat()},
                {"type": "image_url", "image_url": {"url": data_url}},
            ]}],
            "temperature": 0.1,
        },
        timeout=90,
    )
    response.raise_for_status()
    return response.json()["choices"][0]["message"]["content"]


def parse_ai_json(raw_text):
    cleaned = raw_text.strip()
    fence = chr(96) * 3
    if cleaned.startswith(fence):
        cleaned = re.sub(r"^" + fence + r"(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"\s*" + fence + r"$", "", cleaned)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("The AI response did not contain JSON.")
    parsed = json.loads(cleaned[start:end + 1])
    entries = parsed.get("entries", [])
    if not isinstance(entries, list):
        raise ValueError("The AI response has an unexpected format.")
    safe_entries = []
    for item in entries[:100]:
        if not isinstance(item, dict):
            continue
        try:
            amount = round(float(item.get("amount") or 0), 2)
        except (TypeError, ValueError):
            amount = 0
        kind = str(item.get("type", "credit")).lower()
        if kind not in {"credit", "payment"}:
            kind = "credit"
        raw_date = str(item.get("date", "")).strip()
        try:
            date.fromisoformat(raw_date)
        except ValueError:
            raw_date = ""
        try:
            confidence = float(item.get("confidence", 0.5))
        except (TypeError, ValueError):
            confidence = 0.5
        safe_entries.append({
            "customer_name": str(item.get("customer_name", "")).strip()[:100],
            "date": raw_date,
            "description": str(item.get("description", "")).strip()[:240],
            "amount": max(0, amount),
            "type": kind,
            "confidence": min(1.0, max(0.0, confidence)),
        })
    return safe_entries


@app.post("/api/mobile/extract")
def api_mobile_extract():
    install_id = request.headers.get("X-Install-ID", "").strip()
    if len(install_id) < 16 or len(install_id) > 128:
        return jsonify({"error": "This installation could not be identified. Update the app and try again."}), 400

    # Store only a one-way install/IP hash, not the device identifier.
    address = request.remote_addr or "unknown"
    install_hash = hashlib.sha256(install_id.encode("utf-8")).hexdigest()
    bucket = hashlib.sha256((install_hash + "|" + address).encode("utf-8")).hexdigest()
    db = get_db()
    cutoff = (datetime.utcnow() - timedelta(hours=1)).isoformat()
    try:
        db.execute("BEGIN IMMEDIATE")
        db.execute("DELETE FROM mobile_ai_requests WHERE requested_at < ?", (cutoff,))
        recent_count = db.execute(
            "SELECT COUNT(*) FROM mobile_ai_requests WHERE bucket_hash=?",
            (bucket,),
        ).fetchone()[0]
        if recent_count >= MOBILE_RATE_LIMIT_PER_HOUR:
            db.rollback()
            return jsonify({"error": "You have reached the photo-reading limit. Please try again later."}), 429
        db.execute(
            "INSERT INTO mobile_ai_requests(bucket_hash, requested_at) VALUES (?, ?)",
            (bucket, datetime.utcnow().isoformat()),
        )
        db.commit()
    except sqlite3.Error:
        db.rollback()
        app.logger.exception("Mobile AI rate limiter could not record a request.")
        return jsonify({"error": "Photo reading is temporarily unavailable. Please try again."}), 503

    if not ai_is_ready():
        return jsonify({"error": "AI photo reading is not configured on the service yet."}), 503
    upload = request.files.get("ledger_image")
    if not upload or not upload.filename:
        return jsonify({"error": "Choose a ledger photo first."}), 400
    mime_type = (upload.mimetype or "").lower()
    if mime_type not in {"image/jpeg", "image/png", "image/webp"}:
        return jsonify({"error": "Please choose a JPG, PNG or WebP photo."}), 400
    image_bytes = upload.read(MAX_UPLOAD_BYTES + 1)
    if len(image_bytes) > MAX_UPLOAD_BYTES:
        return jsonify({"error": "The image is larger than 10 MB."}), 413
    signatures = {
        "image/jpeg": image_bytes.startswith(b"\xff\xd8\xff"),
        "image/png": image_bytes.startswith(b"\x89PNG\r\n\x1a\n"),
        "image/webp": len(image_bytes) >= 12 and image_bytes[:4] == b"RIFF" and image_bytes[8:12] == b"WEBP",
    }
    if not signatures.get(mime_type, False):
        return jsonify({"error": "The selected file is not a valid image."}), 400
    try:
        raw = extract_with_openrouter(image_bytes, mime_type) if ai_provider_name() == "openrouter" else extract_with_gemini(image_bytes, mime_type)
        entries = parse_ai_json(raw)
        return jsonify({"entries": entries, "review_required": True})
    except requests.Timeout:
        return jsonify({"error": "The AI service took too long. Please retry."}), 504
    except requests.HTTPError as exc:
        code = exc.response.status_code if exc.response is not None else 502
        if code in (401, 403):
            message = "The AI service key is invalid or does not have access."
        elif code == 429:
            message = "The AI service has reached its usage limit. Try again later."
        else:
            message = "The AI service could not process this photo. Please retry."
        return jsonify({"error": message}), 502
    except RuntimeError as exc:
        detail = str(exc)
        app.logger.warning("Mobile AI fallback exhausted: %s", detail)
        if detail.startswith("AI_INVALID_KEY:"):
            return jsonify({"error": "The AI service key is invalid or does not have access. The service owner must update it."}), 503
        if detail.startswith("AI_QUOTA:"):
            return jsonify({"error": "The AI service has reached its usage limit. Please try again later."}), 429
        if detail.startswith("AI_MODEL_UNAVAILABLE:"):
            return jsonify({"error": "The configured AI models are unavailable. The service owner must update the model list."}), 503
        if detail.startswith("AI_NOT_CONFIGURED:"):
            return jsonify({"error": "AI photo reading has not been enabled on the service yet."}), 503
        return jsonify({"error": "The AI service is temporarily unavailable. Please retry later."}), 503
    except (requests.RequestException, KeyError, IndexError, ValueError, json.JSONDecodeError):
        app.logger.exception("Mobile AI extraction failed")
        return jsonify({"error": "We could not read this photo. Try a brighter, clearer picture."}), 502


@app.post("/api/extract")
@login_required
def api_extract():
    upload = request.files.get("ledger_image")
    if not upload or not upload.filename:
        return jsonify({"error": "Choose a ledger photo first."}), 400
    filename = secure_filename(upload.filename)
    mime_type = (upload.mimetype or "").lower()
    if mime_type not in {"image/jpeg", "image/png", "image/webp"}:
        return jsonify({"error": "Please upload a JPG, PNG or WebP image."}), 400
    image_bytes = upload.read(MAX_UPLOAD_BYTES + 1)
    if len(image_bytes) > MAX_UPLOAD_BYTES:
        return jsonify({"error": "The image is larger than 10 MB."}), 413
    signatures = {
        "image/jpeg": image_bytes.startswith(b"\xff\xd8\xff"),
        "image/png": image_bytes.startswith(b"\x89PNG\r\n\x1a\n"),
        "image/webp": len(image_bytes) >= 12 and image_bytes[:4] == b"RIFF" and image_bytes[8:12] == b"WEBP",
    }
    if not signatures.get(mime_type, False):
        return jsonify({"error": "The file contents do not match the selected image type."}), 400
    if not ai_is_ready():
        return jsonify({"error": "AI is not configured yet. Add a Gemini or OpenRouter API key to the server environment."}), 503
    try:
        raw = extract_with_openrouter(image_bytes, mime_type) if ai_provider_name() == "openrouter" else extract_with_gemini(image_bytes, mime_type)
        entries = parse_ai_json(raw)
        return jsonify({"entries": entries, "filename": filename, "review_required": True})
    except requests.HTTPError as exc:
        app.logger.warning("AI provider returned HTTP error: %s", exc)
        return jsonify({"error": "The AI provider rejected the request. Check the API key, model and usage limits."}), 502
    except (requests.RequestException, KeyError, IndexError, ValueError, json.JSONDecodeError):
        app.logger.exception("AI extraction failed")
        return jsonify({"error": "Could not read this image. Try a clearer photo or check the AI provider settings."}), 502


@app.post("/api/transactions/bulk")
@login_required
def api_transactions_bulk():
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return jsonify({"error": "Send a valid JSON object containing entries."}), 400
    entries = payload.get("entries")
    if not isinstance(entries, list) or not entries or len(entries) > 100:
        return jsonify({"error": "Choose at least one and no more than 100 entries."}), 400

    # Validate the complete batch before writing anything, so a bad row cannot
    # leave the shopkeeper with a partially imported page.
    normalized = []
    for index, item in enumerate(entries, start=1):
        if not isinstance(item, dict):
            return jsonify({"error": f"Entry {index} is not a valid record."}), 400
        name = str(item.get("customer_name", "")).strip()[:100]
        description = str(item.get("description", "")).strip()[:240]
        kind = str(item.get("type", "credit")).lower()
        try:
            amount = round(float(item.get("amount") or 0), 2)
        except (TypeError, ValueError):
            return jsonify({"error": f"Entry {index} has an invalid amount."}), 400
        if not name:
            return jsonify({"error": f"Entry {index} needs a customer name."}), 400
        if not math.isfinite(amount) or amount <= 0 or amount > 100000000:
            return jsonify({"error": f"Entry {index} needs a valid amount greater than zero."}), 400
        if kind not in {"credit", "payment"}:
            return jsonify({"error": f"Entry {index} has an invalid transaction type."}), 400
        transaction_date = str(item.get("date") or date.today().isoformat())
        try:
            date.fromisoformat(transaction_date)
        except ValueError:
            return jsonify({"error": f"Entry {index} has an invalid date."}), 400
        normalized.append((name, description, kind, amount, transaction_date))

    db = get_db()
    saved = 0
    try:
        db.execute("BEGIN IMMEDIATE")
        for name, description, kind, amount, transaction_date in normalized:
            customer = db.execute(
                "SELECT id FROM customers WHERE name=? COLLATE NOCASE", (name,)
            ).fetchone()
            if customer:
                customer_id = customer["id"]
            else:
                cur = db.execute(
                    "INSERT INTO customers(name, phone, created_at) VALUES (?, '', ?)",
                    (name, now_iso()),
                )
                customer_id = cur.lastrowid
            db.execute(
                """INSERT INTO transactions(customer_id, transaction_date, transaction_type, description, amount, created_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (customer_id, transaction_date, kind, description, amount, now_iso()),
            )
            saved += 1
        db.commit()
    except sqlite3.Error:
        db.rollback()
        app.logger.exception("Bulk ledger import failed; the batch was rolled back.")
        return jsonify({"error": "The entries could not be saved. No entries from this batch were kept."}), 500

    return jsonify({"saved": saved, "message": f"{saved} ledger entries saved."})

@app.get("/reports")
@login_required
def reports():
    rows = get_db().execute(
        """SELECT substr(transaction_date, 1, 7) AS month,
             SUM(CASE WHEN transaction_type='credit' THEN amount ELSE 0 END) AS credit_total,
             SUM(CASE WHEN transaction_type='payment' THEN amount ELSE 0 END) AS payment_total,
             COUNT(*) AS entry_count
           FROM transactions GROUP BY substr(transaction_date, 1, 7)
           ORDER BY month DESC LIMIT 12"""
    ).fetchall()
    return render_template("reports.html", months=rows)


@app.route("/settings", methods=["GET", "POST"])
@login_required
def settings():
    if request.method == "POST":
        db = get_db()
        account = owner()
        shop_name = request.form.get("shop_name", "").strip()[:80]
        current = request.form.get("current_passcode", "")
        new_passcode = request.form.get("new_passcode", "")
        confirm = request.form.get("confirm_passcode", "")
        if not check_password_hash(account["password_hash"], current):
            flash("Your current PIN/password is incorrect.", "error")
        elif new_passcode:
            if not passcode_is_valid(new_passcode):
                flash("Use a PIN of at least 6 digits or a password of at least 8 characters.", "error")
            elif new_passcode != confirm:
                flash("The new PIN/password confirmation does not match.", "error")
            else:
                db.execute(
                    "UPDATE owner SET shop_name=?, password_hash=? WHERE id=1",
                    (shop_name or account["shop_name"], generate_password_hash(new_passcode)),
                )
                db.commit()
                flash("Shop details and access code updated.", "success")
        else:
            db.execute("UPDATE owner SET shop_name=? WHERE id=1", (shop_name or account["shop_name"],))
            db.commit()
            flash("Shop details updated.", "success")
        return redirect(url_for("settings"))
    return render_template("settings.html", provider=ai_provider_name(), ai_ready=ai_is_ready())


@app.errorhandler(400)
def bad_request(error):
    return render_template("error.html", code=400, message=getattr(error, "description", "Please refresh and try again.")), 400


@app.errorhandler(404)
def not_found(_error):
    return render_template("error.html", code=404, message="We couldn't find that page."), 404


@app.errorhandler(413)
def file_too_large(_error):
    if request.path.startswith("/api/"):
        return jsonify({"error": "The image is larger than 10 MB."}), 413
    return render_template("error.html", code=413, message="That upload is too large. The maximum is 10 MB."), 413


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), debug=False)
