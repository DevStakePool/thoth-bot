#!/bin/bash

source ./util.sh

# Issue 43
curl -s -X POST "https://api.koios.rest/api/v1/tx_info" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_tx_hashes":["f5401d48ac42a1199c8fbb214e63e4f350ee5a4f099ff460ca7f8f7bdcfabd4c"]}' | jq > issues/43_txs_info.json

# Fetching assets
echo "Fetching all assets in all TXs"
download_assets "issues/43_txs_info.json"