#!/bin/bash

DIR=$(dirname "$(readlink -f "$BASH_SOURCE")")
JAVA_OPTS="-Xmx4g"
TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN_ENV}
DB_USER="postgres"
DB_PASSWORD="postgres"
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="thoth_dev"
THOTH_VERSION="1.0.0-beta"
LOGS_FOLDER="${DIR}/logs"

mkdir -p "${LOGS_FOLDER}"

echo "*** Starting THOTH BOT v${THOTH_VERSION} ***"

java -jar target/thoth-bot-${THOTH_VERSION}.jar \
      ${JAVA_OPTS} \
      --thoth.test-mode=false \
      --logging.level.com.devpool=DEBUG \
      --logging.file.name="${LOGS_FOLDER}/thoth-bot.log" \
      --telegram.bot.token="${TELEGRAM_BOT_TOKEN}" \
      --spring.datasource.username=${DB_USER} \
      --spring.datasource.password=${DB_PASSWORD} \
      --spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} > /dev/null 2>&1 &

# get the app pid
rm -f "${DIR}/THOTH.pid"
echo $! > "${DIR}/THOTH.pid"

echo "*** THOTH BOT v${THOTH_VERSION} started with PID $(cat "${DIR}/THOTH.pid") ***"