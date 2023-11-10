#!/bin/bash

DIR=$(dirname "$(readlink -f "$BASH_SOURCE")")
JAVA_OPTS="-Xmx4g"
TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN_ENV}
DB_USER="postgres"
DB_PASSWORD="postgres"
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="thoth"
THOTH_VERSION="1.4.1"
LOGS_FOLDER="${DIR}/logs"
ADMIN_USERNAME="CHANGE_ME"

echo "TOKEN: ${TELEGRAM_BOT_TOKEN_ENV}"

mkdir -p "${LOGS_FOLDER}"

echo "*** Starting THOTH BOT v${THOTH_VERSION} ***"

java -jar ${DIR}/../target/thoth-bot-${THOTH_VERSION}.jar \
      ${JAVA_OPTS} \
      --thoth.test-mode=false \
      --logging.level.com.devpool=DEBUG \
      --logging.file.name="${LOGS_FOLDER}/thoth-bot.log" \
      --telegram.bot.token="${TELEGRAM_BOT_TOKEN}" \
      --thoth.admin.username="${ADMIN_USERNAME}" \
      --spring.datasource.username=${DB_USER} \
      --spring.datasource.password=${DB_PASSWORD} \
      --spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} 2>&1

