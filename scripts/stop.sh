#!/bin/bash
DIR=$(dirname "$(readlink -f "$BASH_SOURCE")")
PID_PATH=${DIR}/THOTH.pid
APP_NAME="THOTH BOT"

if [ ! -f "$PID_PATH" ]; then
  echo "PID $PID_PATH Not found"
else
  PID="$(cat "${PID_PATH}")"
  if [ "$PID" = "" ]; then
    echo "$APP_NAME is not running"
    exit 0
  fi
  if [ ! -e "/proc/$PID" -a "/proc/$PID/exe" ]; then
    echo "$APP_NAME was not running."
  else
    # send SIGTERM
    kill -15 "${PID}"
    echo "Gracefully stopping $APP_NAME with PID ${PID}..."

    # wait 10 secs for the shutdown then kill the app
    sleep 10
    if [ -f "/proc/${PID}/exe" ]; then
      echo "Sending SIGKILL to ${PID}..."
      kill -9 "${PID}"
    fi
  fi
fi
