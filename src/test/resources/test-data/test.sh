#!/bin/bash
echo "Fetching data for transactions info"
ALL_TX_HASHES=$(for i in `ls *utxos.json`; do cat $i | jq -r '"\""+.[].tx_hash + "\","'; done)
ALL_TX_HASHES=$(echo -n ${ALL_TX_HASHES} | awk -vRS=',' '{print $0}' | sort | uniq | awk '{printf $0","}')
ALL_TX_HASHES=$(echo ${ALL_TX_HASHES} | sed 's/.$//')
IFS_BACKUP=${IFS}
export IFS=","
batch_size=10
idx=1
batch=""
batch_no=0
for hash in ${ALL_TX_HASHES}
do
  if [ "${batch}" == "" ]
  then
    batch="${hash}"
  else
    batch="${batch},${hash}"
  fi

  if [ $((idx++)) -eq $batch_size ]; then
      tx_file="txs/txs_info_$((batch_no++)).json"
      IFS=$IFS_BACKUP # restore IFS

      echo "Fetching TX info batch ${tx_file}"

      #echo ${batch}
      batch=""
      idx=1
  fi
done
if [ $idx -gt 0 ]; then
  tx_file="txs/txs_info_${batch_no}.json"
  IFS=$IFS_BACKUP
  echo "Leftover: $idx\n$batch"
  echo $tx_file
fi