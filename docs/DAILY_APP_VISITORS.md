# 일일 앱 방문자 통계

## 집계 기준

한국 시간(Asia/Seoul) 기준 로그인 계정이 앱을 실행하거나 포그라운드로 복귀한 날을 방문일로 기록한다. 앱을 켜둔 채 자정이 지나도 포그라운드에서 다음 날 방문을 기록한다. 같은 계정이 반복 실행하거나 여러 기기에서 접속해도 하루 한 명이다.

앱은 방문 이벤트를 전달하고 서버가 인증 계정과 현재 날짜를 결정한다. 앱이 포그라운드로 복귀하면 서버에 다시 확인하고, 계속 켜져 있을 때는 서버가 전달한 다음 자정까지의 지연으로 요청을 예약한다. 기기 시계가 잘못되어도 방문일 판단에 사용하지 않는다. 푸시 수신·토큰 갱신 자체는 방문으로 집계하지 않는다. 비로그인 화면과 오프라인 상태에서 서버에 도달하지 못한 방문도 포함하지 않는다.

방문 기록을 지원하는 앱 버전부터 데이터가 쌓인다. 관리자 화면에 표시되는 도입 이전 날짜의 0은 과거 전체 사용량이 0이었다는 뜻이 아니다. 방문 통계는 신규 가입자 수와 구분한다.

## API

| 요청 | 권한 | 결과 |
|------|------|------|
| `POST /api/users/me/visits` | 사용자 Access Token | `result.visitDate`: 서버 방문일, `nextVisitInSeconds`: 다음 한국 자정까지 1~86,400초 |
| `GET /api/admin/visits` | 관리자 세션 | 오늘·어제 고유 방문자 및 최근 7일 일별 방문자 |

기록 요청에 사용자 ID나 날짜를 보내지 않는다. 관리자 응답은 `today`, `timeZone`, `todayVisitors`, `yesterdayVisitors`, `daily[{date,visitors}]`로 구성한다. `daily`는 오래된 날짜부터 오늘까지 7개이며 빈 날짜는 0으로 채운다. 관리자 응답에는 사용자 식별 정보가 없으며 캐시를 금지한다.

## 저장과 중복 방지

`user_visits`는 사용자·방문일과 생성/수정 시각만 저장한다. IP·기기 ID·화면 이동·의료 데이터는 수집하지 않는다. 사용자 행 잠금을 먼저 얻은 뒤 방문 존재 여부를 확인하고 생성하며, DB의 `(user_id, visit_date)` 유일 제약을 함께 둔다.

계정 삭제도 동일한 사용자 잠금을 얻고 방문 기록을 함께 삭제한다. 따라서 탈퇴한 계정의 방문은 조회 시점의 과거 통계에서도 제외된다. 새로운 이력 테이블은 기존 약물·인증 데이터와 별개다.

## 배포 준비

1. 서버 배포 전에 새 테이블 생성 권한과 스키마 적용 방식을 확인한다. `ddl-auto=update`를 사용하는 환경에서는 엔티티 변경으로 생성되지만 다른 환경은 아래 DDL을 별도로 적용해야 한다.
2. 서버 API를 먼저 배포하고 관리자 접근·집계 응답을 확인한다.
3. 방문 이벤트를 포함한 앱을 배포한다. 기존 앱에는 방문 호출이 없으므로 앱 업데이트 이전의 접속을 소급해 추정하지 않는다.
4. 실제 로그인 앱 방문이 오늘 집계에 반영되고 반복 방문으로 늘지 않는지 확인한다.

```sql
CREATE TABLE IF NOT EXISTS user_visits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_visits_user_date UNIQUE (user_id, visit_date),
    INDEX idx_user_visits_date (visit_date),
    CONSTRAINT fk_user_visits_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

운영 DB에 이 DDL을 자동 실행하는 스크립트나 운영 접속값은 제공하지 않는다.

## 검증

`VisitServiceTest`는 한국 자정 경계·다음 자정까지 남은 시간·중복·누락 계정·7일 빈 날짜 처리를 검사한다. `VisitApiSecurityTest`는 비로그인 401·일반 사용자 관리자 통계 403·서버가 정한 사용자와 날짜·관리자 집계 응답을 검사한다.

`UserVisitIntegrationTest`는 동시 방문 6개 요청, 다른 계정, 자정 날짜 분리와 실제 계정 삭제를 검사한다. 로컬에서는 격리된 H2를, 기존 CI에서는 `MYSQL_INTEGRATION_TEST=true`와 임시 MySQL을 사용한다. 외부 DB 연결값은 loopback 테스트 DB만 허용한다.
