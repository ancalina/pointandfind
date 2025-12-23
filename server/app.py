from __future__ import annotations

import os
from datetime import datetime
from pathlib import Path

from flask import Flask, jsonify, request

UPLOAD_DIR = Path(__file__).resolve().parent / "uploads"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

app = Flask(__name__)


@app.get("/health")
def health_check():
    return jsonify({"status": "ok"})


@app.post("/upload")
def upload_photo():
    if "photo" not in request.files:
        return jsonify({"received": False, "error": "photo missing"}), 400

    photo = request.files["photo"]
    timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
    safe_name = f"{timestamp}_{photo.filename or 'upload.jpg'}"
    save_path = UPLOAD_DIR / safe_name
    photo.save(save_path)

    return jsonify({"received": True, "filename": safe_name})


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "5000"))
    app.run(host="0.0.0.0", port=port)
