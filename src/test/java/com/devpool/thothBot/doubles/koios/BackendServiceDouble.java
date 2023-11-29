package com.devpool.thothBot.doubles.koios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rest.koios.client.backend.api.account.AccountService;
import rest.koios.client.backend.api.address.AddressService;
import rest.koios.client.backend.api.asset.AssetService;
import rest.koios.client.backend.api.block.BlockService;
import rest.koios.client.backend.api.epoch.EpochService;
import rest.koios.client.backend.api.network.NetworkService;
import rest.koios.client.backend.api.pool.PoolService;
import rest.koios.client.backend.api.script.ScriptService;
import rest.koios.client.backend.api.transactions.TransactionsService;
import rest.koios.client.backend.factory.BackendService;

/**
 * Koios Java client backend service test double
 */
public class BackendServiceDouble implements BackendService {

    private static final Logger LOG = LoggerFactory.getLogger(BackendServiceDouble.class);
    private final boolean disableThothNftForAccounts;

    public BackendServiceDouble() {
        this(false);
    }

    public BackendServiceDouble(boolean disableThothNftForAccounts) {
        this.disableThothNftForAccounts = disableThothNftForAccounts;
    }


    @Override
    public NetworkService getNetworkService() {
        return new NetworkServiceDouble();
    }

    @Override
    public EpochService getEpochService() {
        return null;
    }

    @Override
    public BlockService getBlockService() {
        return null;
    }

    @Override
    public TransactionsService getTransactionsService() {
        return new TransactionsServiceDouble();
    }

    @Override
    public AddressService getAddressService() {
        return new AddressServiceDouble();
    }

    @Override
    public AccountService getAccountService() {
        return new AccountServiceDouble(this.disableThothNftForAccounts);
    }

    @Override
    public AssetService getAssetService() {
        return new AssetServiceDouble();
    }

    @Override
    public PoolService getPoolService() {
        return new PoolServiceDouble();
    }

    @Override
    public ScriptService getScriptService() {
        return null;
    }
}
