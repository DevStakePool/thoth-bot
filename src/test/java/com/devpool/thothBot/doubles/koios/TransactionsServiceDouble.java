package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.UTxO;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.transactions.TransactionsService;
import rest.koios.client.backend.api.transactions.model.*;
import rest.koios.client.backend.factory.options.Options;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class TransactionsServiceDouble implements TransactionsService {
    @Override
    public Result<List<UTxO>> getUTxOInfo(List<String> list, boolean b) throws ApiException {
        return null;
    }

    @Override
    public Result<List<RawTx>> getRawTransaction(List<String> list, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<TxInfo> getTransactionInformation(String txHash) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxInfo>> getTransactionInformation(List<String> txHashes, Options options) throws ApiException {
        try {
            List<TxInfo> txInfoList = KoiosDataBuilder.getTxInfoTestData();
            List<TxInfo> filteredList = txInfoList.stream().filter(tx -> txHashes.contains(tx.getTxHash())).collect(Collectors.toList());
            return Result.<List<TxInfo>>builder().code(200).response("").successful(true).value(filteredList).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<TxMetadata>> getTransactionMetadata(List<String> txHashes, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxMetadataLabels>> getTransactionMetadataLabels(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<String> submitTx(byte[] cborData) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxStatus>> getTransactionStatus(List<String> txHashes, Options options) throws ApiException {
        return null;
    }
}
