package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.asset.AssetService;
import rest.koios.client.backend.api.asset.model.*;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.TxHash;
import rest.koios.client.backend.api.base.common.UTxO;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.utils.Tuple;

import java.io.IOException;
import java.util.List;

public class AssetServiceDouble implements AssetService {

    @Override
    public Result<List<Asset>> getAssetList(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AssetTokenRegistry>> getAssetTokenRegistry(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AssetAddress>> getAssetsAddresses(String assetPolicy, String assetName, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PaymentAddress>> getNFTAddress(String s, String s1, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AssetAddress>> getPolicyAssetAddressList(String s, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AssetHistory>> getAssetHistory(String assetPolicy, String assetName, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PolicyAssetInfo>> getPolicyAssetInformation(String assetPolicy, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PolicyAssetMint>> getPolicyAssetMints(String s, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssetList(String assetPolicy, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<AssetInformation> getAssetInformation(String assetPolicy, String assetName) throws ApiException {
        try {
            AssetInformation assetInformation = KoiosDataBuilder.getAssetInformation(assetPolicy, assetName);
            return Result.<AssetInformation>builder().code(200).response("").successful(true).value(assetInformation).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AssetInformation>> getAssetInformationBulk(List<Tuple<String, String>> list, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<UTxO>> getAssetUTxOs(List<Tuple<String, String>> list, Boolean aBoolean, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<AssetSummary> getAssetSummary(String assetPolicy, String assetName) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getAssetTransactions(String assetPolicy, String assetName, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getAssetTransactions(String assetPolicy, String assetName, Integer afterBlockHeight, boolean history, Options options) throws ApiException {
        return null;
    }
}
