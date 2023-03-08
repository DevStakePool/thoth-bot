package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.koios.KoiosFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import rest.koios.client.backend.api.account.model.AccountAssets;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.pool.model.PoolInfo;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract checker task component holding generic data and utilities
 */
public abstract class AbstractCheckerTask {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCheckerTask.class);
    protected static final long DEFAULT_PAGINATION_SIZE = 1000;
    public static final double LOVELACE = 1000000.0;
    public static final String ADA_SYMBOL = " " + '\u20B3';
    public static final String CARDANO_SCAN_STAKE_KEY = "https://cardanoscan.io/stakekey/";
    public static final String CARDANO_SCAN_STAKE_POOL = "https://cardanoscan.io/pool/";

    public static final String CARDANO_SCAN_TX = "https://cardanoscan.io/transaction/";
    protected static final int USERS_BATCH_SIZE = 50;

    public static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
    private static final String ADA_HANDLE_PREFIX = "$";

    protected static final DateTimeFormatter TX_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, hh:mm a");

    @Autowired
    protected UserDao userDao;

    @Autowired
    protected KoiosFacade koiosFacade;

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

    private String shortenStakeAddr(String stakeAddr) {
        return "stake1u..." + stakeAddr.substring(stakeAddr.length() - 8);
    }

    /**
     * Get the ADA Handle associated to the stake address stakeAddr, if any.
     * If there's no handle the shortenStakeAddr() will be returned.
     * If there are multiple handles, the first in alphabetical order will be shown (usually the most valuable)
     *
     * @param stakeAddrs list
     * @return the handle or short stake address organised by staking address
     */
    protected Map<String, String> getAdaHandleForAccount(String... stakeAddrs) {
        Map<String, String> handlesMap = new HashMap<>();
        boolean errorFound = false;

        try {
            Result<List<AccountAssets>> assetsResp = this.koiosFacade.getKoiosService().getAccountService().getAccountAssets(List.of(stakeAddrs), null, null);
            if (assetsResp.isSuccessful()) {
                for (AccountAssets asset : assetsResp.getValue()) {
                    String stakeAddr = asset.getStakeAddress();
                    Optional<String> bestHandle = asset.getAssetList().stream().filter(a -> a.getPolicyId().equals(ADA_HANDLE_POLICY_ID)).map(a -> hexToAscii(a.getAssetName())).sorted().findFirst();
                    if (bestHandle.isEmpty()) {
                        // Account has no handles
                        handlesMap.put(stakeAddr, shortenStakeAddr(stakeAddr));
                    } else {
                        LOG.debug("Found handle {} for account {}", bestHandle.get(), stakeAddr);
                        handlesMap.put(stakeAddr, ADA_HANDLE_PREFIX + bestHandle.get());
                    }
                }
            } else {
                LOG.warn("Can't get the assets for accounts {}, due to '{}' (code {}}. Returning the stake address shortened instead",
                        stakeAddrs, assetsResp.getResponse(), assetsResp.getCode());
                errorFound = true;
            }
        } catch (Exception e) {
            LOG.warn("Exception while getting the assets for accounts {}, due to '{}'. Returning the stake address shortened instead",
                    stakeAddrs, e.toString());
            errorFound = true;
        }

        if (errorFound) {
            for (String stakeAddr : stakeAddrs) {
                handlesMap.put(stakeAddr, shortenStakeAddr(stakeAddr));
            }
        }

        return handlesMap;
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
