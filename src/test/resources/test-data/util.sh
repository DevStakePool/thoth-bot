#!/bin/bash

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