#!/bin/bash

DIR=$(dirname "$(readlink -f "$BASH_SOURCE")")

${DIR}/stop.sh

sleep 3

${DIR}/start.sh &