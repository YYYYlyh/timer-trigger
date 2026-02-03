#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/timer-trigger-1.0.0.jar"
CONFIG_PATH="${SCRIPT_DIR}/config.yaml"
PID_FILE="${SCRIPT_DIR}/timer-trigger.pid"
LOG_DIR="${SCRIPT_DIR}/logs"
LOG_FILE="${LOG_DIR}/console.out"

mkdir -p "${LOG_DIR}"

if [[ -f "${PID_FILE}" ]]; then
  if kill -0 "$(cat "${PID_FILE}")" >/dev/null 2>&1; then
    echo "timer-trigger already running (PID $(cat "${PID_FILE}"))."
    exit 0
  fi
fi

nohup java -jar "${JAR_PATH}" --config "${CONFIG_PATH}" > "${LOG_FILE}" 2>&1 &
echo $! > "${PID_FILE}"
echo "Started timer-trigger (PID $!). Logs: ${LOG_FILE}"
