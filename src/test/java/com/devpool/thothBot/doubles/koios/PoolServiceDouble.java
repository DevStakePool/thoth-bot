package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.account.model.AccountRewards;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.pool.PoolService;
import rest.koios.client.backend.api.pool.model.*;
import rest.koios.client.backend.factory.options.Options;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class PoolServiceDouble implements PoolService {
    @Override
    public Result<List<Pool>> getPoolList(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolInfo>> getPoolInformation(List<String> poolIds, Options options) throws ApiException {
        try {
            List<PoolInfo> data = KoiosDataBuilder.getPoolInformationTestData();
            List<PoolInfo> filteredList = data.stream().filter(p -> poolIds.contains(p.getPoolIdBech32())).collect(Collectors.toList());
            return Result.<List<PoolInfo>>builder().code(200).response("").successful(true).value(filteredList).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<PoolStakeSnapshot>> getPoolStakeSnapshot(String poolBech32, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolDelegator>> getPoolDelegatorsList(String poolBech32, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolDelegatorHistory>> getPoolDelegatorsHistory(String poolBech32, Integer epochNo, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolBlock>> getPoolBlocksByEpoch(String poolBech32, Integer epochNo, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolBlock>> getPoolBlocks(String poolBech32, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<PoolHistory> getPoolHistoryByEpoch(String poolBech32, Integer epochNo, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolHistory>> getPoolHistory(String poolBech32, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolUpdate>> getPoolUpdatesByPoolBech32(String poolBech32, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolUpdate>> getPoolUpdates(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolRelay>> getPoolRelays(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolMetadata>> getPoolMetadata(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolMetadata>> getPoolMetadata(List<String> poolIds, Options options) throws ApiException {
        return null;
    }
}
