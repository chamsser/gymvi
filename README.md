# Gymvi

서울의 공공체육시설과 프로그램을 AI 대화와 지도로 찾는 Android 앱입니다.

- **AI 대화**: "오늘 저녁 수영할 곳"처럼 물으면 가까운 공공체육시설과 프로그램을 찾아 근거와 함께 보여 줍니다.
- **지도**: 종목별로 시설을 찾고, 운영 시간, 이용료, 프로그램과 길찾기 미리보기를 확인합니다.
- **내 정보**: 신체 정보와 AI 메모리로 추천을 맞춥니다. 이 정보는 기기에만 저장합니다.

## 구성

```text
android/   Kotlin, Jetpack Compose 앱
server/    Kotlin, Spring Boot API 서버 (PostgreSQL 18 + PostGIS 3.6)
pipeline/  공공데이터 수집과 정규화 (Python 3.13)
```

## 준비물

- JDK 17
- Android SDK (compileSdk 37)
- PostgreSQL 18과 PostGIS 3.6 (실제 데이터 조회 시)
- Python 3.13 (데이터 파이프라인 실행 시)

키와 비밀번호는 저장소에 넣지 않습니다. `.env.example`의 변수 이름을 참고해 환경 변수로 넣어 주세요.

## 서버 실행

DB 없이 API 형태만 확인할 때:

```powershell
cd server
.\gradlew.bat bootRun
```

실제 데이터를 조회할 때는 `postgis` 프로필과 DB 변수를 함께 지정합니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'postgis'
$env:GYMVI_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/gymvi'
$env:GYMVI_DB_USERNAME = 'gymvi'
$env:GYMVI_DB_PASSWORD = '<비밀번호>'
cd server
.\gradlew.bat bootRun
```

DB 스키마는 서버 시작 시 Flyway가 만듭니다. AI 기능은 `GYMVI_AI_ENABLED=true`와 `GYMVI_OPENAI_API_KEY`가 있을 때 켜집니다. API 명세는 `server/openapi/gymvi-api-v1.yaml`에 있습니다.

## 앱 빌드

```powershell
$env:GYMVI_API_BASE_URL = 'http://10.0.2.2:8080'
$env:GYMVI_NAVER_MAPS_CLIENT_ID = '<NAVER Maps 클라이언트 ID>'
cd android
.\gradlew.bat :app:assembleDebug
```

`GYMVI_API_BASE_URL`을 지정하지 않으면 debug 빌드는 에뮬레이터에서 호스트 PC를 가리키는 `http://10.0.2.2:8080`을 씁니다. 빌드 전에 앱에 포함된 오픈소스 라이선스 목록을 자동으로 검사합니다.

## 데이터

시설과 프로그램 정보는 공공데이터포털과 운영기관의 공개 자료에서 가져옵니다. 확인한 출처와 이용 조건은 `server/src/main/resources/curated/dataset-sources.json`에 있습니다. 파이프라인은 다음처럼 실행합니다.

```powershell
cd pipeline
python -m gymvi_pipeline --help
```

## 개인정보

회원가입과 로그인이 없고, 신원 정보를 받지 않습니다. 앱이 보내는 정보와 보관 기간은 앱의 **내 정보 > 개인정보 이용 안내**에서 확인할 수 있습니다.

## 라이선스

All rights reserved. 2026 국민체육진흥공단(KSPO) 공공데이터 활용 경진대회의 접수, 심사, 검증과 결과 발표에 필요한 범위에서만 이용할 수 있으며, 그 밖의 복제, 수정, 배포와 상업적 이용은 사전 서면 허가가 필요합니다. 자세한 내용은 [LICENSE](LICENSE)를 확인해 주세요.
