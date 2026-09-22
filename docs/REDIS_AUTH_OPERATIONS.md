# Redis 임시 인증 저장소 운영

OAuth state와 일회성 인증 코드만 Redis에 저장한다. 리프레시 토큰과 사용자 데이터는 계속 RDS에 저장한다.

## 운영 반영 상태 — 2026-09-22 KST

- [이슈 #31](https://github.com/myYack-team/BackEnd-SpringBoot/issues/31)과 [PR #32](https://github.com/myYack-team/BackEnd-SpringBoot/pull/32)에서 구현을 완료했다.
- 머지 커밋 `3b7eff50b5d6b441eca5fefba5f39ab58a9e3904`의 [자동 배포](https://github.com/myYack-team/BackEnd-SpringBoot/actions/runs/35718461858)가 성공했으며 롤백은 실행되지 않았다.
- 운영 Redis 7.4.11은 Docker 없이 systemd로 실행한다. `127.0.0.1:6379`에만 바인딩하고 EC2 보안 그룹에 6379 공개 규칙을 두지 않는다.
- 애플리케이션 ACL은 `myyak:prod:auth:v1:*` 키와 연결에 필요한 명령, `SET`, `GETDEL`만 허용한다.
- `maxmemory 32mb`, `noeviction`, RDB/AOF 비활성화 정책을 사용한다. Redis 재시작 시 진행 중 로그인 상태가 사라지는 것은 의도된 동작이다.
- OAuth state TTL은 600초, 일회성 인증 코드 TTL은 300초다. 저장은 TTL을 포함한 `SET`, 소비는 원자적 `GETDEL`을 사용한다.
- Redis 장애는 `AUTH503`/HTTP 503으로 처리하며 JVM 메모리 저장소로 우회하지 않는다.

### 완료한 검증

- 실제 Redis를 사용한 통합 테스트와 `bootJar`
- 운영 ACL의 `PING`, TTL 포함 `SET`, `GETDEL`, 두 번째 소비 실패
- 허용 범위 밖 키와 `CONFIG` 명령 거부, 외부 6379 연결 실패
- `/v3/api-docs` 200, OAuth 시작 302, 존재하지 않는 인증 코드 교환 400/`AUTH406`
- Redis를 유지한 채 애플리케이션만 재시작한 뒤 OAuth state 보존과 1회 소비
- 최종 `myyak`, `redis-server`, `nginx` 서비스의 active 상태

운영 Redis 자체를 재시작하는 무영속 동작 검증과 실제 카카오 사용자의 전체 로그인 회귀는 운영에서 유발하지 않았다. timeout과 `noeviction` 한도 도달도 운영 장애로 재현하지 않았다.

## 데이터와 보안 계약

| 데이터 | 저장소 | TTL / 내구성 |
|---|---|---|
| OAuth state | Redis | 600초, Redis 재시작 시 소실 가능 |
| 일회성 인증 코드 | Redis | 300초, Redis 재시작 시 소실 가능 |
| Refresh token | RDS | 기존 정책 유지 |

일회성 인증 코드 payload에는 토큰이 포함되므로 Redis 비밀번호, 키, payload를 로그나 문서에 출력하지 않는다. Redis ACL 비밀번호는 서버 ACL과 전체 `APPLICATION_YAML`에서 항상 같은 값이어야 한다.

## 운영 구성

운영 EC2는 ARM64의 소형 인스턴스다. Redis는 네이티브 systemd 서비스로 실행하고 애플리케이션 서비스가 Redis 시작과 가용성에 의존하도록 구성한다.

- Redis 설정: [myyak-redis.conf](../scripts/redis/myyak-redis.conf)
- ACL 템플릿: [myyak-users.acl.example](../scripts/redis/myyak-users.acl.example)
- Redis systemd override: [redis-server.override.conf](../scripts/redis/redis-server.override.conf)
- 애플리케이션 systemd override: [myyak.service.override.conf](../scripts/redis/myyak.service.override.conf)

`maxmemory`는 Redis 프로세스 전체 RSS 제한이 아니다. 버퍼와 메모리 단편화를 포함한 RSS, 호스트 available 메모리, swap in/out, 지연과 OOM을 함께 관찰한다.

## 재구축 및 설정 변경

아래 명령의 버전은 현재 운영 기준이다. 재구축이나 업그레이드 전에는 대상 아키텍처와 Redis 공식 저장소의 지원 버전을 다시 확인한다.

```bash
sudo apt-get install lsb-release curl gpg
curl -fsSL https://packages.redis.io/gpg | sudo gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
sudo chmod 644 /usr/share/keyrings/redis-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/redis.list
sudo apt-get update

dpkg --print-architecture
apt-cache madison redis-server redis-tools
sudo apt-get install redis-server=6:7.4.11 redis-tools=6:7.4.11
sudo apt-mark hold redis-server redis-tools
```

1. 저장소의 Redis 설정과 systemd override를 위 경로에 설치한다.
2. ACL 템플릿의 `CHANGE_ME`를 승인된 보안 원본의 비밀번호로 바꾼다. 비밀번호를 명령 인자, 출력, 로그에 남기지 않는다.
3. Redis 설정과 ACL 파일의 소유권을 `root:redis`, 권한을 `640`으로 설정한다.
4. `systemctl daemon-reload` 후 Redis를 enable/restart한다.
5. 앱을 배포하기 전에 localhost 바인딩, ACL 명령 범위, 메모리·영속성 정책과 외부 차단을 검증한다.

## APPLICATION_YAML

GitHub Actions의 `APPLICATION_YAML`은 전체 파일 Secret이다. 다음 구조를 기존 전체 설정에 병합하며, 이 조각만 Secret에 등록하지 않는다.

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

Redis 비밀번호는 승인된 보안 원본과 GitHub Secret에만 둔다. Secret 변경만으로 실행 중인 서버 설정은 바뀌지 않으며, 해당 전체 설정을 포함한 빌드와 배포가 성공해야 적용 완료다.

## 배포 및 기능 검증

`main` push는 GitHub Actions의 서버 빌드와 EC2 배포를 자동 실행한다. Redis가 준비되지 않은 상태에서 Redis 설정을 포함한 애플리케이션을 배포하지 않는다.

```bash
systemctl is-active myyak redis-server nginx
ss -ltnp
redis-cli --user myyak-app --askpass PING
```

배포 후에는 다음을 확인한다.

1. `/v3/api-docs`가 200이고 OAuth 시작이 302인지 확인한다. `Location` 헤더의 state는 출력하지 않는다.
2. 로그인 시작 후 Redis를 유지한 채 앱만 재시작하고 TTL 안에 callback을 완료한다.
3. callback으로 받은 일회성 코드를 TTL 안에 교환하고 같은 state와 code의 재사용이 실패하는지 확인한다.
4. 통제된 환경에서 Redis 중지 시 인증 흐름이 `AUTH503`/HTTP 503으로 실패하고 메모리 우회가 없는지 확인한다.
5. Redis 재시작 시 기존 임시 상태가 사라지고 새 로그인은 정상 동작하는지 확인한다.

## 자원 관찰과 롤백

```bash
free -m
vmstat 1 10
systemctl show myyak redis-server -p MemoryCurrent -p MemoryPeak
journalctl -u redis-server --since "30 minutes ago" --no-pager
```

애플리케이션 ACL에는 `INFO` 권한이 없으므로 앱 계정으로 Redis 전체 통계를 조회하지 않는다. 내부 통계가 필요하면 별도의 최소 권한 운영 절차를 마련한다.

이전 JAR은 Redis의 임시 상태를 읽지 못한다. 롤백하면 진행 중 로그인은 다시 시작해야 하며, Redis 자체 장애는 JAR 롤백만으로 복구되지 않는다. Redis 복구, 이전 JAR 복원, 새 로그인 확인 순서로 처리한다.

## 로컬 실행

Docker Desktop을 시작한 뒤 로컬 전용 비밀번호로 Redis를 실행한다.

```powershell
$env:REDIS_PASSWORD = '<local-only-password>'
docker compose -f compose.redis.yaml up -d
docker compose -f compose.redis.yaml ps
```

로컬 애플리케이션에는 `REDIS_USERNAME=default`, 같은 `REDIS_PASSWORD`, `AUTH_REDIS_KEY_PREFIX=myyak:local:auth:v1`을 사용한다. 종료할 때 `docker compose -f compose.redis.yaml down`을 실행한다. 이 Compose도 무영속이므로 컨테이너 재시작 후 진행 중 로그인 소실이 정상이다.
