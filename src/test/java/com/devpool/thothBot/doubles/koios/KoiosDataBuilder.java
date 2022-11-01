package com.devpool.thothBot.doubles.koios;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rest.koios.client.backend.api.account.model.AccountAddress;
import rest.koios.client.backend.api.account.model.AccountRewards;
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
    private static final String POOL_INFORMATION_JSON_FILE = "test-data/pool_information.json";

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


}
