# MyYak Server

복약 관리 앱 **마이약(MyYak)**의 백엔드 서버입니다.

## Tech Stack

| 항목 | 상세 |
|------|------|
| Framework | Spring Boot 3.5.8 |
| Language | Java 21 |
| Build Tool | Gradle |
| Database | MySQL 8 (AWS RDS) |
| ORM | Spring Data JPA + Hibernate |
| Security | Spring Security + JWT |
| API Docs | SpringDoc OpenAPI (Swagger) |

### 주요 라이브러리

- **firebase-admin** 9.2.0 - FCM 푸시 알림
- **google-cloud-vision** 3.31.0 - 처방전 OCR
- **jjwt** 0.11.5 - JWT 토큰
- **spring-dotenv** 4.0.0 - 환경변수 로딩
- **jsoup** 1.17.2 - HTML 파싱
- **poi** 5.2.5 - Excel 파싱
- **pdfbox** 3.0.3 - PDF 파싱
- **caffeine** 3.1.8 - 인메모리 캐시
- **aws-java-sdk-s3** - 이미지 저장

---

## 로컬 개발 환경 설정

### 사전 요구사항

- Java 21
- MySQL 8.0+
- Gradle 8+

### 실행

```bash
# 로컬 프로필로 실행
cd myyak-server
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 프로필 구분

| 프로필 | DB | 용도 |
|--------|-----|------|
| (기본) application.yaml | AWS RDS | 프로덕션 기본 설정 |
| local | localhost:3306 | 로컬 개발 |
| prod | AWS RDS | 프로덕션 배포 (GitHub Secrets에서 주입) |

### Swagger UI

서버 실행 후 접속: `http://localhost:8080/swagger-ui.html`

---

## 프로젝트 구조

```
src/main/java/com/myyak/
├── apiPayload/              # API 응답 처리
│   ├── code/status/         # SuccessStatus, ErrorStatus
│   ├── exception/           # ExceptionAdvice, GeneralException
│   └── ApiResponse.java     # 통합 응답 객체
├── config/                  # 설정
│   ├── SecurityConfig       # Spring Security + JWT
│   ├── CorsConfig           # CORS
│   ├── FirebaseConfig       # Firebase FCM
│   ├── S3Config             # AWS S3
│   └── SwaggerConfig        # OpenAPI
├── converter/               # Entity <-> DTO 변환
├── domain/                  # JPA Entity
│   ├── common/BaseEntity    # 생성일/수정일 자동 관리
│   ├── enums/               # DrugType, IntakeStatus 등
│   ├── User, DrugInfo, Supplement, Intake, Reminder ...
│   └── (22개 Entity)
├── filter/                  # JWT 인증 필터
├── repository/              # JPA Repository
├── scheduler/               # ReminderScheduler (FCM 발송)
├── service/                 # 비즈니스 로직 (기능별 폴더)
│   ├── authService/         # 카카오 OAuth + JWT
│   ├── medicationService/   # 약물 CRUD
│   ├── intakeService/       # 복용 기록
│   ├── scanService/         # 처방전 OCR + LLM 분석
│   ├── analysisService/     # AI 약물 분석
│   ├── familyService/       # 가족 연동
│   ├── llm/                 # LLM Adapter (Gemini, OpenAI)
│   ├── ocr/                 # OCR Adapter (Google Vision)
│   ├── storage/             # Storage Adapter (S3)
│   └── ...
├── util/                    # 유틸리티
└── web/
    ├── controller/          # REST Controller (20개)
    └── dto/                 # Request/Response DTO
```

---

## Entity 관계도

```
User (사용자)
├── 1:N  UserMedication (내 약물)
│         ├── N:1  DrugInfo (약물 마스터 - 식약처 API)
│         ├── 1:N  Intake (복용 기록)
│         └── 1:N  Reminder (복용 알림)
├── 1:N  UserSupplement (내 영양제)
│         ├── N:1  Supplement (영양제 마스터)
│         ├── 1:N  Intake
│         └── 1:N  Reminder
├── 1:N  Prescription (처방전)
├── 1:N  HealthNote (건강 메모)
├── 1:N  AnalysisReport (AI 분석 리포트)
├── 1:N  QnA (문의)
├── 1:N  FamilyLink (보호자/피보호자)
└── 1:1  RefreshToken
```

---

## API 엔드포인트 (110개+)

### 인증 `/api/auth`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/kakao/login` | 카카오 로그인 리다이렉트 |
| GET | `/kakao/callback` | 카카오 OAuth 콜백 |
| POST | `/exchange` | 인증 코드 교환 |
| POST | `/kakao` | 카카오 토큰 로그인 |
| POST | `/refresh` | 토큰 갱신 |
| POST | `/logout` | 로그아웃 |
| POST | `/test-login` | 테스트 로그인 (Play Store 심사용) |

### 사용자 `/api/users`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/me` | 내 정보 조회 |
| PATCH | `/me` | 내 정보 수정 |
| DELETE | `/me` | 회원 탈퇴 (카카오 연동 해제 포함) |
| GET | `/me/notification-settings` | 알림 설정 조회 |
| PATCH | `/me/notification-settings` | 알림 설정 수정 |
| PUT | `/me/profile-setup` | 온보딩 프로필 설정 |
| GET/PATCH | `/me/ai-consent` | AI 데이터 동의 |
| GET/POST | `/me/consent` | 서비스 약관 동의 |
| PATCH | `/me/phone` | 전화번호 등록 (가족 연동용) |

### 약물 관리 `/api/medications`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 약 등록 |
| GET | `/` | 약 목록 |
| GET | `/{id}` | 약 상세 |
| PATCH | `/{id}` | 약 수정 |
| DELETE | `/{id}` | 약 삭제 |
| DELETE | `/batch` | 일괄 삭제 |
| POST | `/check-duplicates` | 중복 약물 체크 |

### 복용 기록 `/api/intakes`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 복약 기록 |
| GET | `/` | 일별 복약 조회 |
| GET | `/monthly-summary` | 월간 달력 데이터 |

### 오늘의 복약 `/api/today`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 오늘 복용 스케줄 + 상태 |

### 알림 `/api/reminders`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 알림 목록 |
| PATCH | `/{id}` | 알림 수정 |
| PATCH | `/{id}/toggle` | 알림 ON/OFF |
| POST | `/{id}/snooze` | 다시 알림 (10/30/60분) |
| DELETE | `/{id}/snooze` | 다시 알림 해제 |

### 처방전 `/api/prescriptions`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/upload` | 이미지 업로드 (S3) |
| POST | `/register` | 처방전 + 약물 일괄 등록 |
| GET | `/` | 처방전 목록 |
| GET | `/{id}` | 처방전 상세 |
| PATCH | `/{id}` | 처방전 수정 |
| DELETE | `/{id}` | 처방전 삭제 |
| DELETE | `/batch` | 일괄 삭제 |

### 영양제 `/api/supplements`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 영양제 마스터 등록 |
| POST | `/with-image` | 이미지 포함 등록 |
| GET | `/search` | 영양제 검색 |
| GET | `/popular` | 인기 영양제 |
| GET | `/{id}` | 영양제 상세 |
| POST | `/my` | 내 영양제 추가 |
| GET | `/my` | 내 영양제 목록 |
| GET/PATCH/DELETE | `/my/{id}` | 내 영양제 상세/수정/삭제 |
| DELETE | `/my/batch` | 일괄 삭제 |

### 처방전 스캔 `/api/scan`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | OCR + LLM 처방전 분석 |

### 건강 메모 `/api/health-notes`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 건강 메모 생성 (컨디션 0-10) |
| GET | `/{date}` | 날짜별 조회 |
| PUT | `/{date}` | 수정 |
| DELETE | `/{date}` | 삭제 |
| GET | `/` | 기간별 목록 |

### AI 분석 `/api/analysis`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/request` | AI 분석 요청 (동기) |
| GET | `/reports` | 리포트 목록 |
| GET | `/reports/{id}` | 리포트 상세 |
| DELETE | `/reports/{id}` | 리포트 삭제 |
| GET | `/quota` | 월간 쿼터 조회 |
| GET | `/data-sufficiency` | 데이터 충분성 확인 |
| POST/DELETE | `/temporary-notes` | 임시 메모 관리 |

### 가족 연동 `/api/family`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/status` | 연동 현황 |
| POST | `/request` | 연동 요청 |
| DELETE | `/request/{id}` | 요청 취소 |
| POST | `/request/{id}/accept` | 요청 수락 |
| POST | `/request/{id}/reject` | 요청 거절 |
| DELETE | `/link/{id}` | 연동 해제 |
| GET | `/protected/{userId}/today` | 피보호자 오늘 스케줄 |
| GET | `/protected/{userId}/schedule` | 피보호자 날짜별 |
| GET | `/protected/{userId}/monthly-summary` | 피보호자 월간 |
| GET/PATCH | `/notification-settings` | 가족 알림 설정 |

### Q&A `/api/qna`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/` | 내 문의 목록 |
| POST | `/` | 문의 등록 |
| GET | `/{id}` | 문의 상세 |
| POST | `/{id}/replies` | 답글 추가 |
| DELETE | `/{id}` | 문의 삭제 |

### 약물 검색 `/api/drugs`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/search/fast` | 캐시 기반 빠른 검색 |
| GET | `/search/api` | e약은요 API 직접 검색 |
| POST | `/search-and-save` | 검색 후 DB 저장 |
| GET | `/{itemSeq}` | 품목기준코드로 조회 |

### 관리자 `/api/admin`

| Method | Path | 설명 |
|--------|------|------|
| GET | `/stats/drugs` | 약물 통계 |
| GET | `/users/stats` | 사용자 통계 |
| GET | `/users/daily` | 일별 가입 추이 |
| GET | `/health` | 서버 헬스체크 |
| GET | `/logs` | 에러 로그 조회 |
| POST | `/logs/chat` | AI 에러 분석 |
| PUT | `/settings/ai-model` | AI 모델 변경 |
| POST | `/test-login/toggle` | 테스트 로그인 토글 |

### 데이터 배치 `/api/drugs/batch`

| Method | Path | 설명 |
|--------|------|------|
| POST | `/full-sync` | 전체 동기화 (CSV 업로드) |
| GET | `/full-sync/{jobId}/status` | 진행 상태 |
| POST | `/full-sync/{jobId}/cancel` | 작업 중단 |
| POST | `/test-table/promote` | 테스트 → 운영 이관 |

---

## 외부 연동

### Google Vision OCR
- 처방전/약봉투 이미지에서 텍스트 추출
- `google-cloud-credentials.json` 필요

### LLM (Adapter Pattern)
- **Gemini** (기본): vision=`gemini-2.5-flash-lite`, analysis=`gemini-3-pro-preview`
- **OpenAI** (대체): vision=`gpt-4o-mini`, analysis=`gpt-4o`
- 설정으로 교체 가능 (`ai.vision-provider`, `ai.analysis-provider`)

### Kakao OAuth
- 카카오 로그인 + 회원탈퇴 시 카카오 연동 해제

### Firebase FCM
- 복약 알림 푸시
- `firebase-service-account.json` (classpath) 필요

### AWS S3
- 처방전/영양제/프로필 이미지 저장
- Bucket: `myyak-uploads`, Region: `ap-northeast-2`

### 공공데이터 포털 (e약은요 API)
- 약물 정보 수집 (품목명, 효능, 용법, 주의사항)

---

## 보안

### JWT 인증
- Access Token: 1일 / Refresh Token: 180일
- Stateless Session + CSRF 비활성화

### 공개 경로 (인증 불필요)
- `/api/auth/**`, `/api/drugs/search/**`
- `/swagger-ui/**`, `/v3/api-docs/**`
- `/api/admin/**`, `/api/drugs/batch/**`

### 데이터 암호화
- email, phone, fcmToken → AES 필드 레벨 암호화
- `EncryptedStringConverter` (JPA Converter)
- 가족 연동: 전화번호 해시(검색용) + AES 암호화(저장용) 이중 처리

### 소유권 검증
- 모든 사용자 데이터 API에서 userId 기반 소유권 확인
- 일괄 처리(batch) API도 소유권 검증 포함

---

## API 응답 형식

```json
{
  "isSuccess": true,
  "code": "200",
  "message": "Ok",
  "result": { ... }
}
```

---

## 처방전 스캔 파이프라인

```
이미지 촬영 → S3 업로드
              ↓
         Google Vision OCR (텍스트 추출)
              ↓
         LLM 분석 (약물명 파싱)
              ↓
         DB 검색 (DrugInfo 매칭, 자모 편집거리 보정)
              ↓
         결과 반환 (매칭된 약물 + 미매칭 약물)
```

---

## AI 분석 로직

1. 사용자의 현재 약물 목록 스냅샷
2. 최근 30일 복약 기록 + 건강 메모 수집
3. LLM에 약물 상호작용/음식 병용주의 분석 요청
4. 리포트 저장 (기전 분석, 음식 상호작용, 생활 팁, 패턴 분석)
5. 월간 쿼터: 기본 2회

---

## 배포

- PR이 `main`에 머지되면 **GitHub Actions CI/CD로 자동 배포**
- 수동 배포 금지
- `application.yaml`은 GitHub Secrets(`APPLICATION_YAML`)에서 통째로 주입

### 서버 인프라

| 항목 | 상세 |
|------|------|
| Compute | AWS EC2 (ap-northeast-2) |
| Database | AWS RDS MySQL 8 |
| Storage | AWS S3 (`myyak-uploads`) |
| Domain | api.myyak.xyz |

---

## 설계 패턴

### Adapter Pattern
LLM, OCR, Storage 등 교체 가능한 외부 모듈에 적용:
```
LlmClient (interface)
├── GeminiClientAdapter
└── OpenAiClientAdapter
```

### Service Interface + Impl
```
UserService (interface)
└── UserServiceImpl
```

### Converter (Entity <-> DTO)
순수 static 메서드로 구성, 상태 없음

### DTO (Inner Class 패턴)
```java
UserRequestDTO.CreateRequest
UserResponseDTO.UserInfo
```
