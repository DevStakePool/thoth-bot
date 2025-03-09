package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.network.NetworkService;
import rest.koios.client.backend.api.network.model.*;
import rest.koios.client.backend.factory.options.Options;

import java.util.List;

public class NetworkServiceDouble implements NetworkService {
    public static final String TIP_EPOCH_NO_SYS_VAR_KEY = "tip_epoch_no";
    public static final String TIP_BLOCK_NO_SYS_VAR_KEY = "tip_block_no";

    @Override
    public Result<Tip> getChainTip() throws ApiException {
        // Check if the tip is defined in the system variables
        var epochNo = System.getProperty(TIP_EPOCH_NO_SYS_VAR_KEY, "371");
        var blockNo = System.getProperty(TIP_BLOCK_NO_SYS_VAR_KEY, "1234");

        Tip tip = new Tip();
        tip.setEpochNo(Integer.valueOf(epochNo));
        tip.setBlockNo(Integer.valueOf(blockNo));
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
