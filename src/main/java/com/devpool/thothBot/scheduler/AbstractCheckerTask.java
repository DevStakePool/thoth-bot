package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.pool.model.PoolInfo;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Abstract checker task component holding generic data and utilities
 */
@Component
public abstract class AbstractCheckerTask {
    protected static final long DEFAULT_PAGINATION_SIZE = 1000;
    public static final double LOVELACE = 1000000.0;
    public static final String ADA_SYMBOL = " " + '\u20B3';
    public static final String CARDANO_SCAN_STAKE_KEY = "https://cardanoscan.io/stakekey/";
    public static final String CARDANO_SCAN_STAKE_POOL = "https://cardanoscan.io/pool/";

    public static final String CARDANO_SCAN_TX = "https://cardanoscan.io/transaction/";
    protected static final int USERS_BATCH_SIZE = 50;

    protected static final DateTimeFormatter TX_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, hh:mm a");

    @Autowired
    protected UserDao userDao;

    @Autowired
    protected AssetsDao assetsDao;

    @Autowired
    protected KoiosFacade koiosFacade;

    @Autowired
    protected TelegramFacade telegramFacade;

    protected String getPoolName(List<PoolInfo> poolIds, String poolAddress) {
        if (poolAddress == null) return null;

        if (poolIds != null) {
            Optional<PoolInfo> poolInfo = poolIds.stream().filter(pi -> pi.getPoolIdBech32().equals(poolAddress)).findFirst();
            if (poolInfo.isPresent() && poolInfo.get().getMetaJson() != null && poolInfo.get().getMetaJson().getTicker() != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                sb.append(poolInfo.get().getMetaJson().getTicker());
                sb.append("]");
                if (poolInfo.get().getMetaJson().getName() != null)
                    sb.append(" ").append(poolInfo.get().getMetaJson().getName());

                return sb.toString();
            }
        }

        return "pool1..." + poolAddress.substring(poolAddress.length() - 8);
    }

    public static String shortenStakeAddr(String stakeAddr) {
        return "stake1u..." + stakeAddr.substring(stakeAddr.length() - 8);
    }

    public static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");

        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }

        return output.toString();
    }

}
