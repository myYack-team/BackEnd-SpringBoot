# Refresh Token Redis 세션 전환 절차

Refresh Token 세션 전환은 코드 배포와 운영 설정 변경을 분리한다. 기본값 `auth.refresh-store.mode=rds`에서는 기존 RDS 저장소를 계속 사용한다. `redis` 전환 전에는 아래 조건을 모두 충족해야 한다.

## 전환 전 준비

1. 앱 EC2와 장애 영역이 다른 영속 Redis를 준비한다. 복제, 자동 장애조치, 백업과 복구 시험을 완료한다. 현재 EC2의 `appendonly no`, `save ""` Redis는 장기 세션의 원본 저장소로 사용할 수 없다.
2. AOF 정책과 복구 지점을 정한다. `appendfsync everysec`는 장애 시 최근 쓰기가 유실될 수 있으므로 재발급·폐기 결과의 복원 정책을 함께 정한다.
3. 애플리케이션 Redis ACL에 임시 인증 키와 Refresh Token 세션 키 접근을 허용한다. 세션 저장소에는 `GET`, `SET`, `DEL`, `EVAL`, `EVALSHA`가 필요하다. `scripts/redis/myyak-users.refresh.acl.example`은 비밀값 없는 예제다. 실제 키 prefix는 환경별로 고정하고 사용자 입력을 받지 않는다.
4. 별도의 32자 이상 HMAC 키를 보안 저장소에 생성한다. 키 원문은 Git, 이슈, PR, 로그에 남기지 않는다. 키를 교체하면 기존 digest가 일치하지 않아 세션 재발급이 실패하므로 회전 계획이 필요하다.
5. 현재 `APPLICATION_YAML`의 전체 원본을 확보하고, 다른 설정을 유지한 상태에서 `auth.refresh-store.mode`, `key-prefix`, `hash-key`만 추가한다. 부분 YAML로 GitHub Secret을 덮어쓰지 않는다.

## 기존 토큰 이관

`auth.refresh-store.migrate-legacy=true`는 시작 시 RDS의 활성 Refresh Token을 읽어 Redis에 digest와 남은 TTL을 저장한다. 토큰 원문은 이관 과정의 입력으로만 사용하고 Redis에는 보관하지 않는다.

1. 유지보수 창에서 로그인·재발급 요청 유입과 기존 앱의 토큰 쓰기를 중지한다.
2. 영속 Redis와 ACL을 확인한다.
3. `mode=redis`, `migrate-legacy=true`인 전체 설정으로 새 JAR을 배포한다. 시작 로그의 이관 건수와 누락 건수를 확인한다. 누락된 토큰은 재로그인이 필요하다.
4. 정상 로그인·재발급·로그아웃과 실제 Redis TTL을 확인한 뒤 `migrate-legacy=false`로 재배포한다.
5. 이관 후 RDS fallback을 켜지 않는다. Redis에서 폐기·회전한 옛 토큰이 RDS에 남아 다시 유효해질 수 있다.

현재 배포 workflow는 `main` 머지 시 자동 배포하며 실패 시 이전 JAR로 롤백한다. Redis 모드로 실제 전환한 뒤 RDS 모드 JAR로 자동 롤백하면 폐기된 옛 토큰이 재사용될 수 있다. **운영 전환 전에 배포 롤백을 Redis 모드 호환 버전으로 제한하거나, 롤백 시 전체 세션 재로그인 정책을 적용해야 한다.** 코드 PR 머지만으로 Redis 모드를 활성화하지 않는다.

## 구현과 검증

- `RefreshTokenSessionStore` Port에 RDS/Redis Adapter를 둔다.
- Redis에는 HMAC-SHA-256 digest, 세션·family 식별자와 TTL만 저장한다.
- 재발급은 Lua Script에서 이전 digest 확인, 이전 토큰 사용 기록, 새 digest 저장을 원자적으로 처리한다.
- 회전된 토큰 재사용 시 현재 family 세션을 폐기한다. 동일 토큰 동시 사용에서는 한 요청만 회전에 성공한다.
- 로그아웃과 탈퇴는 Redis 세션을 삭제한다. Redis 장애는 명시적 503으로 실패하고 JVM/RDS로 우회하지 않는다.
- 실제 Redis 통합 테스트와 앱 로그인·재발급·로그아웃·탈퇴 회귀를 통과해야 운영 설정을 전환한다.

임시 인증 state와 일회용 코드는 기존 무영속 Redis에도 저장할 수 있으나, 장기 Refresh Token 세션은 별도 내구성 요구를 가진다. 단일 EC2의 AOF만으로 고가용성을 충족했다고 표현하지 않는다.
