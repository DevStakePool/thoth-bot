package com.devpool.thothBot.doubles.koios;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rest.koios.client.backend.api.account.model.AccountAddress;
import rest.koios.client.backend.api.account.model.AccountAssets;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.account.model.AccountRewards;
import rest.koios.client.backend.api.asset.model.AssetInformation;
import rest.koios.client.backend.api.common.TxHash;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.api.transactions.model.TxInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class KoiosDataBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(KoiosDataBuilder.class);

    private static final String TX_INFO_JSON_FILE = "test-data/txs_info.json";
    private static final String ACCOUNT_ADDRESSES_JSON_FILE = "test-data/account_addresses.json";
    private static final String ACCOUNT_REWARDS_341_JSON_FILE = "test-data/account_rewards_341.json";
    private static final String ACCOUNT_REWARDS_369_JSON_FILE = "test-data/account_rewards_369.json";
    private static final String ACCOUNT_ASSETS_JSON_FILE = "test-data/account_assets.json";
    private static final String POOL_INFORMATION_JSON_FILE = "test-data/pool_information.json";
    private static final String ACCOUNT_INFORMATION_JSON_FILE = "test-data/account_information.json";
    private static final String ADDRESS_TRANSACTIONS_PREFIX_JSON_FILE = "test-data/address_txs_";
    private static final String ASSET_INFORMATION_PREFIX_JSON_FILE = "test-data/assets/asset_";

    public static List<TxInfo> getTxInfoTestData() throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(TX_INFO_JSON_FILE).getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<TxInfo> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });
        return data;
    }

    public static List<AccountAddress> getAccountAddressesTestData() throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(ACCOUNT_ADDRESSES_JSON_FILE).getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        List<AccountAddress> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });
        return data;
    }

    public static List<PoolInfo> getPoolInformationTestData() throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(POOL_INFORMATION_JSON_FILE).getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        List<PoolInfo> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });
        return data;
    }

    public static List<AccountInfo> getAccountInformationTestData() throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(ACCOUNT_INFORMATION_JSON_FILE).getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        List<AccountInfo> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });
        return data;
    }

    public static List<AccountRewards> getAccountRewardsTestData(Integer epochNo) throws IOException {
        String fileName = null;
        if (epochNo == 341) fileName = ACCOUNT_REWARDS_341_JSON_FILE;
        else if (epochNo == 369) fileName = ACCOUNT_REWARDS_369_JSON_FILE;
        else throw new RuntimeException("Unsupported epoch number " + epochNo);

        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(fileName).getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        List<AccountRewards> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });
        return data;
    }


    public static List<TxHash> getAddressTransactionTestData(String stakeAddress) throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(ADDRESS_TRANSACTIONS_PREFIX_JSON_FILE + stakeAddress + ".json").getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        List<TxHash> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });

        return data;
    }

    public static List<AccountAssets> getAccountAssets() throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(ACCOUNT_ASSETS_JSON_FILE).getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<AccountAssets> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });

        return data;
    }

    /**
     * @param policyId   Asset Policy ID in hexadecimal format (hex).
     *                   Example: 750900e4999ebe0d58f19b634768ba25e525aaf12403bfe8fe130501
     * @param policyName Asset Name in hexadecimal format (hex)
     *                   Example: 424f4f4b
     * @return The asset policy or {@link IOException} if it cannot be found in the json files
     * @throws IOException
     */
    public static AssetInformation getAssetInformation(String policyId, String policyName) throws IOException {
        String fileName = ASSET_INFORMATION_PREFIX_JSON_FILE + policyId + "_" + policyName + ".json";
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        LOG.info("Reading asset file {}", fileName);
        String f = classLoader.getResource(fileName).getFile();
        File jsonFile = new File(f);
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<AssetInformation> data = mapper.readValue(jsonFile, new TypeReference<>() {
        });

        if (data.isEmpty())
            throw new RuntimeException("Asset " + policyId + "/" + policyName + " returned an empty list");

        return data.get(0);
    }
}
