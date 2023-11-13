#!/bin/bash

echo "Expected messages regarding staking rewards: $(cat account_rewards_341.json  | grep stake_address | wc -l)"
#-> Check why only the last one

#echo "Expected messages regarding transactions:    $(cat *utxos.json  | grep tx_hash | wc -l)"

# 10:39 $ grep -b4 stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy accounts_utxos.json | grep tx_hash  | awk '{print $3}' | sort | uniq | wc -l
#-> 16 TX

tot_tx_no=0
for stake in `grep stake_address accounts_utxos.json  | sort  | uniq  | awk '{gsub("\"", "", $2); gsub(",", "", $2); print $2}'`
do
    tx_no=$(grep -b4 ${stake} accounts_utxos.json | grep tx_hash  | awk '{print $3}' | sort | uniq | wc -l)
    tot_tx_no=$((tot_tx_no + tx_no))
    echo "Expected transactions for account ${stake}: ${tx_no}"
done
for addr in `grep '"address' addresses_utxos.json  | sort  | uniq  | awk '{gsub("\"", "", $2); gsub(",", "", $2); print $2}'`
do
    tx_no=$(grep -b4 ${addr} addresses_utxos.json | grep tx_hash  | awk '{print $3}' | sort | uniq | wc -l)
    tot_tx_no=$((tot_tx_no + tx_no))
    echo "Expected transactions for addresses ${addr}: ${tx_no}"
done
echo "Total expected transactions for accounts+addresses $((tot_tx_no))"
