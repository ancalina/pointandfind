from __future__ import annotations

import json
import os
from datetime import datetime
from pathlib import Path

from dotenv import load_dotenv
from flask import Flask, jsonify, request
import google.generativeai as genai
from PIL import Image

UPLOAD_DIR = Path(__file__).resolve().parent / "uploads"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

app = Flask(__name__)

load_dotenv()
API_KEY = os.getenv("GOOGLE_API_KEY")
MODEL_ERROR: str | None = None

if API_KEY:
    genai.configure(api_key=API_KEY)
    try:
        MODEL = genai.GenerativeModel(
            model_name="gemini-3-flash-preview",
            generation_config={
                "response_mime_type": "application/json",
            },
        )
    except Exception as exc:  # pragma: no cover - startup only
        MODEL = None
        MODEL_ERROR = f"모델 초기화 실패: {exc}"
else:
    MODEL = None
    MODEL_ERROR = "오류: GOOGLE_API_KEY를 찾을 수 없습니다."


def load_image(image_path: Path) -> Image.Image | None:
    try:
        if not image_path.exists():
            raise FileNotFoundError(f"파일을 찾을 수 없습니다: {image_path}")
        return Image.open(image_path)
    except Exception as exc:
        app.logger.exception("이미지 로드 중 오류 발생: %s", exc)
        return None


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

    if MODEL is None:
        return jsonify({"received": False, "error": MODEL_ERROR}), 500

    image = load_image(save_path)
    if image is None:
        return jsonify({"received": False, "error": "이미지를 불러오지 못했습니다."}), 500

    system_prompt = """
이미지를 분석하여 반드시 아래의 JSON 스키마 구조로만 답하세요.
다른 텍스트나 요약(summary)은 포함하지 마세요.
강조된(형광펜 쳐짐, 밑줄그어짐)등을 찾습니다.(링크 텍스트 같은건 포함 안함)

{
  "items": [
    {
      "word": "단어",
      "definition": "설명"
    }
  ]
}
한국어로 답변하세요.
"""

    try:
        response = MODEL.generate_content([system_prompt, image])
        json_data = json.loads(response.text)
    except json.JSONDecodeError:
        return jsonify({"received": False, "error": "AI JSON 파싱 실패"}), 500
    except Exception as exc:
        return jsonify({"received": False, "error": f"AI 처리 오류: {exc}"}), 500

    return jsonify(json_data)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "5000"))
    app.run(host="0.0.0.0", port=port)
