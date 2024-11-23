package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.epoch.EpochService;
import rest.koios.client.backend.api.epoch.model.EpochBlockProtocols;
import rest.koios.client.backend.api.epoch.model.EpochInfo;
import rest.koios.client.backend.api.epoch.model.EpochParams;
import rest.koios.client.backend.factory.options.Options;

import java.io.IOException;
import java.util.List;

public class EpochServiceDouble implements EpochService {
    @Override
    public Result<EpochInfo> getLatestEpochInfo() throws ApiException {
        try {
            var epochInformation = KoiosDataBuilder.getEpochInformationTestData();
            return Result.<EpochInfo>builder().code(200).response("").successful(true).value(epochInformation.get(0)).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<EpochInfo> getEpochInformationByEpoch(Integer epochNo) throws ApiException {
        return getLatestEpochInfo();
    }

    @Override
    public Result<List<EpochInfo>> getEpochInformation(boolean includeNextEpoch, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<EpochParams> getLatestEpochParameters() throws ApiException {
        return null;
    }

    @Override
    public Result<EpochParams> getEpochParametersByEpoch(Integer epochNo) throws ApiException {
        return null;
    }

    @Override
    public Result<List<EpochParams>> getEpochParameters(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<EpochBlockProtocols> getEpochBlockProtocolsByEpoch(Integer epochNo) throws ApiException {
        return null;
    }

    @Override
    public Result<List<EpochBlockProtocols>> getEpochBlockProtocols(Options options) throws ApiException {
        return null;
    }
}
