#!/bin/bash

BLOCK_HEIGHT=8026000

function download_assets() {
  for asset in `grep -A1 policy_id "$1"  | \
    awk -vFS=":" '{print $2}' | \
    awk -vFS="\n" -vRS="\n\n" '{gsub(" ", "", $0); gsub("\"", "", $0); gsub(",", "", $0); print "\""$1"\"" ",\"" $2"\""}' \
    | sort | uniq`; do
      asset_policy_name=`echo -n $asset | awk '{gsub("\"", "", $0); gsub(",", "_", $0); print $0}'`
      if [ -f "assets/asset_${asset_policy_name}.json" ]
      then
        echo "Asset ${asset_policy_name} already existing"
      else
        echo "Fetching asset ${asset_policy_name}"
       curl -s -X POST "https://api.koios.rest/api/v1/asset_info" \
         -H "accept: application/json"\
         -H "content-type: application/json" \
         -d "{\"_asset_list\":[[${asset}]]}" | jq > "assets/asset_${asset_policy_name}.json"
      fi
  done
}

# KOIOS Calls to gather test data
## Account rewards for epoch 369
echo "Fetching account rewards for epoch 369"
curl -s -X POST "https://api.koios.rest/api/v1/account_rewards" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_stake_addresses":["stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32","stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy","stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz","stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr"],"_epoch_no":369}' | jq > account_rewards_369.json

echo "Fetching account rewards for epoch 341"
curl -s -X POST "https://api.koios.rest/api/v1/account_rewards" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_stake_addresses":["stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32","stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy","stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz","stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr"],"_epoch_no":341}' | jq > account_rewards_341.json

## Account Addresses
echo "Fetching data for account addresses"
curl -s -X POST "https://api.koios.rest/api/v1/account_addresses" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_stake_addresses":["stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32","stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz","stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr","stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy"]}' | jq > account_addresses.json

## Account Information
echo "Fetching data for account information"
curl -s -X POST "https://api.koios.rest/api/v1/account_info" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_stake_addresses":["stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32","stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz","stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr","stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy"]}' | jq > account_information.json

### Address Information
echo "Fetching data for address information"
curl -s -X POST "https://api.koios.rest/api/v1/address_info" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_addresses":["addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv","addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m"]}' | jq > address_information.json

## Fetch UTxOs
echo "Fetching data for address UTxOs stake addresses"
curl -s -X POST "https://api.koios.rest/api/v1/account_utxos?block_height=gt.${BLOCK_HEIGHT}&order=block_height" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_stake_addresses":["stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32","stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy","stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz","stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr"],"_extended":false}' | jq > accounts_utxos.json

echo "Fetching data for address UTxOs addresses"
curl -s -X POST "https://api.koios.rest/api/v1/address_utxos?block_height=gt.${BLOCK_HEIGHT}&order=block_height" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_addresses":["addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m","addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv"],"_extended":false}' | jq > addresses_utxos.json

### Fetching address assets
curl -s -X POST "https://api.koios.rest/api/v1/address_assets" \
 -H "accept: application/json"\
 -H "content-type: application/json" \
 -d '{"_addresses":["addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv","addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m"]}' | jq > address_assets.json

#Fetching account assets
echo "Fetching account assets"
curl -s -X POST "https://api.koios.rest/api/v1/account_assets" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_stake_addresses":["stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr","stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32","stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz","stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy"]}' | jq > account_assets.json

## Fetching pool information
echo "Fetching data for pool information"
curl -s -X POST "https://api.koios.rest/api/v1/pool_info" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d '{"_pool_bech32_ids":["pool1p82vmqednsalje23mpnz9u3qt9ruj79xu83mr6p8t93fw5fcu3y","pool1rthy0xp2syng0cydp85wvz973szmq2ns8u5p4hdedkwlyhry27w","pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv","pool10q33p4hx4wqum6thmglw7z5l2vaay4w6m5cdq8fnurw7vjdppcf","pool17e4rdh59t4fmn4g3p02xvs853katrjzge830tsmd3sfdc645yvt","pool1f6lnuxzw90mmd399nxqjvzyyxgmf4h3cp7j6pp5s7xmps86arct", "pool15g3cwwmd3qks03ztl5464j044jasn3dcs6zcgpxtxadfj86lvf2", "pool1hvevy8qu5lwg7ul66xmzvwv8zwdgdradsg9ncvrflm7dvy407e0"]}' | jq > pool_information.json


# Fetching data for TX info
echo "Fetching data for transactions info"
ALL_TX_HASHES=$(for i in `ls *utxos.json`; do cat $i | jq -r '"\""+.[].tx_hash + "\","'; done)
ALL_TX_HASHES=$(echo -n ${ALL_TX_HASHES} | awk -vRS=',' '{print $0}' | sort | uniq | awk '{printf $0","}')
ALL_TX_HASHES=$(echo ${ALL_TX_HASHES} | sed 's/.$//')
curl -s -X POST "https://api.koios.rest/api/v1/tx_info" \
 -H "Accept: application/json" \
 -H "Content-Type: application/json" \
 -d "{\"_tx_hashes\":[${ALL_TX_HASHES}]}" | jq > txs_info.json

echo "Fetching all assets in all TXs"
download_assets "txs_info.json"

echo "Fetching all assets for addresses and stake accounts"
download_assets account_assets.json
download_assets address_assets.json
