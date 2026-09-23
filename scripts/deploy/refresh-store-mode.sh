#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ( "$1" != "-" && ! -f "$1" ) ]]; then
  echo "usage: refresh-store-mode.sh application.yaml" >&2
  exit 2
fi

awk '
  BEGIN { mode = "rds" }
  /^auth:[[:space:]]*(#.*)?$/ { in_auth = 1; in_refresh = 0; next }
  in_auth && /^[^[:space:]#]/ { in_auth = 0; in_refresh = 0 }
  in_auth && /^  refresh-store:[[:space:]]*(#.*)?$/ { in_refresh = 1; next }
  in_refresh && /^  [^[:space:]#]/ { in_refresh = 0 }
  in_refresh && /^    mode:[[:space:]]*/ {
    value = $0
    sub(/^    mode:[[:space:]]*/, "", value)
    sub(/[[:space:]]*(#.*)?$/, "", value)
    if (value != "rds" && value != "redis") invalid = 1
    else mode = value
  }
  END {
    if (invalid) exit 2
    print mode
  }
' "$1"
