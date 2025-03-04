package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.pool.PoolService;
import rest.koios.client.backend.api.pool.model.*;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PoolServiceDouble implements PoolService {
    private final BackendServiceDouble.BackendBehavior backendBehavior;

    public PoolServiceDouble(BackendServiceDouble.BackendBehavior backendBehavior) {
        this.backendBehavior = backendBehavior;
    }

    @Override
    public Result<List<Pool>> getPoolList(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolInfo>> getPoolInformation(List<String> poolIds, Options options) throws ApiException {
        try {
            List<PoolInfo> data = KoiosDataBuilder.getPoolInformationTestData();

            if (backendBehavior.equals(BackendServiceDouble.BackendBehavior.SIMULATE_RETIRING_POOLS)) {
                var retiring = data.stream()
                        .filter(p -> p.getPoolIdBech32().equals("pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv"))
                        .findAny().orElseThrow();
                retiring.setPoolStatus("retiring");
                retiring.setRetiringEpoch(999);

                var retired = data.stream()
                        .filter(p -> p.getPoolIdBech32().equals("pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd"))
                        .findAny().orElseThrow();
                retired.setPoolStatus("retired");
                retired.setRetiringEpoch(666);
            }

            List<PoolInfo> filteredList = data.stream().filter(p -> poolIds.contains(p.getPoolIdBech32())).collect(Collectors.toList());

            // Filter out retiring/retired pools?
            if (Objects.nonNull(options) &&
                    options.getOptionList()
                            .stream()
                            .filter(Filter.class::isInstance)
                            .map(Filter.class::cast)
                            .anyMatch(f -> f.getField().equals("pool_status"))) {
                filteredList = filteredList.stream().filter(p -> !p.getPoolStatus().equals("registered")).collect(Collectors.toList());
            }

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
    public Result<List<PoolStatus>> getPoolRegistrations(Integer integer, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<PoolStatus>> getPoolRetirements(Integer integer, Options options) throws ApiException {
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
