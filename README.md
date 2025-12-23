# pointandfind

사진 촬영 후 서버로 업로드하고, 서버가 수신 확인 JSON을 돌려주는 간단한 샘플입니다.

## Android 앱
- 패키지 ID: `me.ancal.pointandfind`
- 버튼을 누르면 카메라를 실행하여 사진을 촬영하고 `/upload`로 전송합니다.
- 서버 응답에서 `received: true`를 확인하면 수신 완료 메시지를 표시합니다.

### 빌드/실행
1. Android Studio에서 프로젝트 열기.
2. 서버 URL 입력 (에뮬레이터: `http://10.0.2.2:5000`, 실제 기기: PC의 로컬 IP 예 `http://192.168.0.10:5000`).
3. **Take Photo & Upload** 버튼을 눌러 촬영 후 업로드.

## 서버
Flask로 구현된 간단한 파일 수신 서버입니다.

```bash
cd server
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

- 업로드 엔드포인트: `POST /upload`
- 헬스 체크: `GET /health`
- 업로드된 파일은 `server/uploads/`에 저장됩니다.
