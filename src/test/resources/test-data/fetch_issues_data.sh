#!/bin/bash

source ./util.sh

# Issue 43
curl -s -X POST "https://api.koios.rest/api/v1/tx_info" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_tx_hashes":["f5401d48ac42a1199c8fbb214e63e4f350ee5a4f099ff460ca7f8f7bdcfabd4c"],"_inputs":true,"_metadata": true,"_assets": true,"_withdrawals": true,"_scripts": true}' | jq > issues/43_txs_info.json

# Issue 39
curl -s -X POST "https://api.koios.rest/api/v1/tx_info" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_tx_hashes":["0a416d362c9e1884292c4160254a7a8afc4b3921c783114d3d7574a8087ba3da"],"_inputs":true,"_metadata": true,"_assets": true,"_withdrawals": true,"_scripts": true}' | jq > issues/39_txs_info.json

# Issue 47
curl -s -X POST "https://api.koios.rest/api/v1/tx_info" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_tx_hashes":["779133d969dc88440d18741dc17e536b8b1b21ac0fdb431f4d2850f028839d81"],"_inputs":true,"_metadata": true,"_assets": true,"_withdrawals": true,"_scripts": true}' | jq > issues/47_txs_info.json

# Issue 48
curl -s -X POST "https://api.koios.rest/api/v1/tx_info" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_tx_hashes":["24188c3d4aa6efad2c1250705a9ee5f8acd8c59cf9e4eebf9541477af7b10d15"],"_inputs":true,"_metadata": true,"_assets": true,"_withdrawals": true,"_scripts": true}' | jq > issues/48_txs_info.json

# Issue 49
curl -s -X POST "https://api.koios.rest/api/v1/tx_info" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_tx_hashes":["6ad2da8864edf66da2090de526a9c8851c41331c319896ba6d65c7a2278ecba6", "4e00fca28aaa0c5a3907290ee5f94d4c265f0f5e950585b3627d64018b5633df"],"_inputs":true,"_metadata": true,"_assets": true,"_withdrawals": true,"_scripts": true}' | jq > issues/49_txs_info.json

# Fetching assets
echo "Fetching all assets in all TXs"
download_assets "issues/43_txs_info.json"
download_assets "issues/39_txs_info.json"
download_assets "issues/47_txs_info.json"
download_assets "issues/48_txs_info.json"
download_assets "issues/49_txs_info.json"