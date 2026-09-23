#!/usr/bin/env bash
set -euo pipefail

mode_script="scripts/deploy/refresh-store-mode.sh"

redis_mode="$("$BASH" "$mode_script" - <<'YAML'
spring:
  application:
    name: myyak
auth:
  temporary-store:
    key-prefix: myyak:test:auth:v1
  refresh-store:
    mode: redis
    migrate-legacy: true
YAML
)"
[[ "$redis_mode" == redis ]]

rds_mode="$("$BASH" "$mode_script" - <<'YAML'
auth:
  refresh-store:
    mode: rds
scan:
  strategy: ocr-llm
YAML
)"
[[ "$rds_mode" == rds ]]

default_mode="$("$BASH" "$mode_script" - <<'YAML'
auth:
  temporary-store:
    key-prefix: myyak:test:auth:v1
YAML
)"
[[ "$default_mode" == rds ]]

if "$BASH" "$mode_script" - <<'YAML' >/dev/null 2>&1
auth:
  refresh-store:
    mode: unexpected
YAML
then
  echo "unknown refresh store mode was accepted" >&2
  exit 1
fi

echo "Refresh store mode detection passed"
