# Point and Find

카메라로 사진을 촬영한 뒤 Google Gemini API로 이미지 속 강조된 단어를 분석하고,
단어와 뜻을 단어장 형태로 저장하며 필요하면 TTS로 읽어주는 Android 앱입니다.

## Android 앱

- 패키지 ID: `point.and.find`
- CameraX 기반 앱 내 카메라 프리뷰 사용
- Gemini API 키를 앱에서 입력하여 저장
- 사진 촬영 후 Gemini에 이미지 분석 요청
- 응답 JSON의 `items` 배열에서 `word`, `definition`을 읽어 단어장에 추가
- 단어장은 SharedPreferences에 저장됨
- TTS 음성 안내 지원
- 단어 클릭 시 Google 검색 실행
- 롱클릭으로 선택 모드 진입 후 선택 삭제 가능

## 빌드/실행

1. Android Studio에서 프로젝트를 연다.
2. 앱을 실행하고 카메라 권한을 허용한다.
3. 상단 `API` 버튼에서 Gemini API 키를 입력한다.
4. 카메라 버튼을 눌러 사진을 촬영한다.
5. 분석 결과가 하단 단어장에 표시된다.
