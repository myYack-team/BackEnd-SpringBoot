# Redis 임시 인증 저장소 운영

이 문서는 OAuth state와 일회성 인증 코드만 Redis로 이전하는 1단계 운영 절차다. 리프레시 토큰은 계속 RDS에 저장한다.

## 인수인계 상태 — 2026-09-22 KST

이 절은 기존 대화가 없는 작업자가 현재 상태에서 이어가기 위한 기준이다. 실제 작업 전 로컬 `C:/dev/myYak/AGENTS.md`와 `C:/dev/myYak/.codex/OPERATIONS.md`를 읽고, 거기에 기록된 Git·SSH·배포 규칙과 현재 접속 정보를 재확인한다. 두 파일은 이 Git 저장소 밖의 로컬 운영 문서이므로 GitHub 링크로 대체하지 않는다. 비밀값은 대화, 명령 인자, 로그, 커밋에 출력하지 않는다.

### 작업 위치와 원격 상태

- 서버 저장소: `C:/dev/myYak/myyak-server-redis-auth-31`
- GitHub: `myYack-team/BackEnd-SpringBoot`
- 브랜치: `feat/#31`
- 이슈: [#31](https://github.com/myYack-team/BackEnd-SpringBoot/issues/31)
- PR: [#32](https://github.com/myYack-team/BackEnd-SpringBoot/pull/32), base `main`
- 실제 Redis 7.4.11을 사용한 PR 통합 테스트와 `bootJar`가 통과했다.
- 원래 `C:/dev/myYak/myyak-server`에는 다른 작업과 미추적 파일이 있으므로 Redis 작업을 옮기거나 덮어쓰지 않는다.

### 완료된 준비

- OAuth state 600초, 일회성 auth code 300초 Redis TTL 구현
- 생성 시 TTL 포함 SET, 소비 시 원자적 GETDEL 적용
- Redis 장애를 `AUTH503`/HTTP 503으로 분리하고 JVM 메모리 fallback 제거
- state, auth code, 인증 URL query 원문 로그 제거
- EC2용 Redis 설정, ACL 예시, systemd drop-in, 로컬 Compose, 배포 health check 준비
- 운영 JAR에서 현재 전체 YAML을 추출하고 Redis 설정이 포함된 별도 후보 파일 생성

### 아직 수행하지 않은 운영 변경

- 운영 EC2에 Redis 설치·설정
- Redis ACL 파일에 후보 YAML과 같은 비밀번호 적용
- GitHub `APPLICATION_YAML` Secret 갱신
- PR #32 머지와 자동 배포

현재 운영 앱과 nginx는 동작 중이고 Redis는 설치되어 있지 않다. 6379 listener와 EC2 보안 그룹 공개 규칙도 없다. GitHub `APPLICATION_YAML`의 마지막 확인된 갱신 시각은 2026-07-17 18:45:03 KST다.

### 보안 설정 원본

| 용도 | 로컬 경로 | SHA-256 | 상태 |
|---|---|---|---|
| 현재 운영 JAR 추출본 | `C:/dev/myYak/secrets/backup/application-production-current-20260922.yaml` | `79170E03C94E91E21361743064C04283B4D9EFDF928ADAA3732E11B4820BF6A5` | 변경하지 않는 기준 원본 |
| Redis 반영 후보 | `C:/dev/myYak/secrets/backup/application-production-redis-20260922.yaml` | `5FC21F334B21DDC855C1503212F67FBE172B1B52D6047CFC7F23947DC29D80BC` | `APPLICATION_YAML`에 올릴 전체 파일 |

두 파일은 실제 비밀을 포함하며 ACL이 현재 사용자, SYSTEM, Administrators로 제한되어 있다. Redis 반영 후보에는 이미 48-byte 무작위 ACL 비밀번호가 들어 있다. **새 비밀번호를 다시 만들지 말고** 후보의 `spring.data.redis.password`를 출력 없이 읽어 `/etc/redis/myyak-users.acl`의 `myyak-app` 사용자에 동일하게 적용한다. 후보를 새로 만들 때만 YAML과 ACL 비밀번호를 함께 교체한다.

### 남은 작업의 고정 순서

1. Git remote, PR HEAD/check, SSH host key, EC2의 `myyak`·nginx 상태와 6379 미사용을 읽기 전용으로 재확인한다.
2. 아래 절차로 Redis 7.4.11을 설치한 뒤 앱은 재시작하지 않고 Redis만 설정한다.
3. 후보 YAML의 기존 비밀번호를 메모리에서 읽어 ACL 파일에 적용한다. 비밀번호를 셸 명령 인자나 출력에 넣지 않고 SSH 표준입력 등 비노출 경로를 사용한다.
4. localhost PING, ACL 명령 범위, 32 MB/noeviction, RDB/AOF 비활성, 외부 6379 차단을 검증한다.
5. Redis가 정상일 때만 후보 파일 전체를 GitHub `APPLICATION_YAML`에 표준입력으로 등록하고 Secret 갱신 시각을 확인한다. 일부 YAML만 덮어쓰지 않는다.
6. 사용자가 운영 배포를 명시적으로 승인한 범위에서만 PR #32를 머지한다. main 머지는 즉시 GitHub Actions 자동 배포를 실행한다.
7. 배포 run, Redis-backed OAuth health check, 실제 로그인 회귀, 앱 재시작 사이 TTL 내 상태 보존과 자원 사용량을 확인한다.

Redis 설치 전 merge 금지, Redis 준비 전 새 `APPLICATION_YAML`을 포함한 배포 금지다. Secret 갱신 자체는 실행 중인 앱을 바꾸지 않지만, 다음 빌드부터 JAR에 포함된다.

## 용량과 실행 방식

운영 EC2는 `t4g.micro`(ARM64, RAM 약 1 GiB)다. Redis는 Docker 없이 네이티브 systemd 서비스로 실행하고 `maxmemory 32mb`, `noeviction`, RDB/AOF 비활성화로 시작한다. `maxmemory`는 Redis 프로세스 전체 RSS 제한이 아니므로 배포 후 RSS, swap in/out, 지연, OOM을 함께 확인한다.

2026-09-22 기준 Redis 7.4 계열의 보안 패치 버전은 7.4.11이다. Redis 공식 APT 저장소의 Ubuntu 22.04 ARM64 인덱스에서 `redis-server`와 `redis-tools`의 `6:7.4.11` 패키지를 확인했다. 설치 직전에도 후보가 남아 있는지 다시 확인하고 해당 버전을 고정한다. 다른 버전을 사용할 경우 GETDEL(6.2 이상), ACL, 현재 보안 지원 여부를 다시 확인한다.

## 운영 설치 준비

1. Redis 공식 APT 저장소를 등록한다.

   ```bash
   sudo apt-get install lsb-release curl gpg
   curl -fsSL https://packages.redis.io/gpg | sudo gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
   sudo chmod 644 /usr/share/keyrings/redis-archive-keyring.gpg
   echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/redis.list
   sudo apt-get update
   ```

2. 후보와 아키텍처를 확인하고 검증한 버전을 지정해 설치·고정한다.

   ```bash
   dpkg --print-architecture
   apt-cache madison redis-server redis-tools
   sudo apt-get install redis-server=6:7.4.11 redis-tools=6:7.4.11
   sudo apt-mark hold redis-server redis-tools
   ```

3. [myyak-redis.conf](../scripts/redis/myyak-redis.conf)를 `/etc/redis/myyak-redis.conf`에 설치한다.
4. [myyak-users.acl.example](../scripts/redis/myyak-users.acl.example)을 `/etc/redis/myyak-users.acl`로 복사하고 `CHANGE_ME`를 Redis 반영 후보 YAML의 `spring.data.redis.password`와 같은 값으로 교체한다. 이미 생성된 값을 사용하며 새 비밀번호를 만들지 않는다. 실제 값은 저장소, 명령 인자, 출력, 로그에 남기지 않는다.
5. 두 파일 소유권을 `root:redis`, 권한을 각각 `640`으로 설정한다.
6. [redis-server.override.conf](../scripts/redis/redis-server.override.conf)를 `/etc/systemd/system/redis-server.service.d/override.conf`에 설치한다.
7. [myyak.service.override.conf](../scripts/redis/myyak.service.override.conf)를 `/etc/systemd/system/myyak.service.d/redis.conf`에 설치한다. 이 단계는 의존성만 추가하며 애플리케이션 JAR을 바꾸지 않는다.
8. `systemctl daemon-reload`, Redis enable, Redis restart 순서로 적용한다. 앱은 Secret과 코드가 준비되기 전까지 재시작하지 않는다.

앱 계정에는 `myyak:prod:auth:v1:*` 키와 연결 명령, `SET`, `GETDEL`만 허용한다. 6379 포트는 `127.0.0.1`에만 바인딩하며 EC2 보안 그룹 규칙을 추가하지 않는다.

## APPLICATION_YAML 변경

GitHub Actions의 `APPLICATION_YAML`은 전체 파일 Secret이다. 기존 `spring` 루트 아래에 다음 항목을 병합하고, 파일 일부만 Secret에 덮어쓰지 않는다.

현재 인수인계에서는 병합과 검증이 끝난 `C:/dev/myYak/secrets/backup/application-production-redis-20260922.yaml`을 사용한다. 아래 예시는 구조 확인용이며 이 조각만 Secret에 등록하지 않는다.

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      username: myyak-app
      password: <ACL 파일과 같은 값>
      connect-timeout: 2s
      timeout: 2s

auth:
  temporary-store:
    key-prefix: myyak:prod:auth:v1
```

Redis 비밀번호는 운영 보안 원본과 GitHub Secret에만 둔다. 저장소나 이 문서에 기록하지 않는다.

## 배포 전 검증

Redis 프로세스와 접근 범위를 먼저 확인한다.

```bash
systemctl is-active redis-server
ss -ltnp | grep 6379
redis-cli --user myyak-app --askpass PING
redis-cli -h <EC2_PRIVATE_IP> -p 6379 PING  # 연결 실패가 정상
```

그 다음 전체 `APPLICATION_YAML` Secret을 갱신하고 코드 PR을 배포한다. 최초 Map에서 Redis로 전환되는 순간 기존 JVM 안의 진행 중 state/code는 이관되지 않으므로 해당 로그인은 다시 시작해야 한다.

배포 워크플로우는 `/v3/api-docs`와 `/api/auth/kakao/login`의 상태 코드를 모두 확인한다. 후자가 302여야 Redis 쓰기까지 성공한 것이다. 응답의 Location 헤더에는 state가 있으므로 배포 로그에 출력하지 않는다.

## 기능 검증

1. 카카오 로그인 시작 후 앱 서버만 재시작하고 TTL 안에 callback을 완료한다.
2. callback으로 일회성 코드를 받은 뒤 앱 서버만 재시작하고 TTL 안에 `/api/auth/exchange`를 호출한다.
3. 같은 state와 code를 재사용했을 때 기존 실패 계약을 유지하는지 확인한다.
4. Redis를 중지했을 때 로그인 시작과 코드 교환이 `AUTH503`/HTTP 503으로 실패하고 메모리 우회가 없는지 확인한다.
5. Redis를 다시 시작하면 무영속 정책에 따라 진행 중 로그인이 소실되고 새 로그인은 정상 동작하는지 확인한다.

## 자원 관찰과 롤백

배포 직후와 실제 로그인 부하 중 다음을 확인한다.

```bash
free -m
vmstat 1 10
systemctl show myyak redis-server -p MemoryCurrent -p MemoryPeak
redis-cli --user myyak-app --askpass INFO memory
redis-cli --user myyak-app --askpass INFO stats
journalctl -u redis-server --since "30 minutes ago" --no-pager
```

`used_memory`, `used_memory_rss`, `evicted_keys`, `rejected_connections`, swap in/out, 앱 응답 시간을 기록한다. 현재 정책에서는 `evicted_keys`가 0이어야 하며 메모리 한도 도달 시 인증 쓰기가 503으로 거절되어야 한다.

롤백 시 이전 JAR은 Redis 상태를 읽지 못하므로 진행 중 로그인은 재시도해야 한다. Redis 자체가 장애 상태라면 JAR 롤백만으로 공유 상태가 복구되지 않는다. Redis 복구, 이전 JAR 복원, 새 로그인 확인 순서로 처리한다.

## 로컬 실행

Docker Desktop을 시작한 뒤 개발용 비밀번호를 환경 변수에 설정하고 Redis만 실행한다.

```powershell
$env:REDIS_PASSWORD = '<local-only-password>'
docker compose -f compose.redis.yaml up -d
docker compose -f compose.redis.yaml ps
```

로컬 애플리케이션에는 `REDIS_USERNAME=default`, 같은 `REDIS_PASSWORD`, `AUTH_REDIS_KEY_PREFIX=myyak:local:auth:v1`을 사용한다. 종료할 때 `docker compose -f compose.redis.yaml down`을 실행한다. 이 Compose는 무영속이므로 컨테이너 재시작 후 진행 중 로그인 소실이 정상이다.
