#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="${SCRIPT_DIR}/timer-trigger.pid"

if [[ -f "${PID_FILE}" ]]; then
  PID="$(cat "${PID_FILE}")"
  if kill -0 "${PID}" >/dev/null 2>&1; then
    echo "Stopping timer-trigger (PID ${PID})"
    kill "${PID}"
    rm -f "${PID_FILE}"
    exit 0
  fi
  rm -f "${PID_FILE}"
fi

PIDS="$(pgrep -f "timer-trigger-1.0.0.jar" || true)"
if [[ -n "${PIDS}" ]]; then
  echo "Stopping timer-trigger (PID ${PIDS})"
  kill ${PIDS}
else
  echo "timer-trigger is not running."
fi
