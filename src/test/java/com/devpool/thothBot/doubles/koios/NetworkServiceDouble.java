package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.network.NetworkService;
import rest.koios.client.backend.api.network.model.*;
import rest.koios.client.backend.factory.options.Options;

import java.util.List;

public class NetworkServiceDouble implements NetworkService {
    @Override
    public Result<Tip> getChainTip() throws ApiException {
        Tip tip = new Tip();
        tip.setEpochNo(371);
        tip.setBlockNo(1234);
        return Result.<Tip>builder().successful(true).response("").code(200).value(tip).build();
    }

    @Override
    public Result<Genesis> getGenesisInfo() throws ApiException {
        return null;
    }

    @Override
    public Result<Totals> getHistoricalTokenomicStatsByEpoch(Integer epochNo) throws ApiException {
        return null;
    }

    @Override
    public Result<List<Totals>> getHistoricalTokenomicStats(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<ParamUpdateProposal>> getParamUpdateProposals(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<Withdrawal>> getReserveWithdrawals(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<Withdrawal>> getTreasuryWithdrawals(Options options) throws ApiException {
        return null;
    }
}
