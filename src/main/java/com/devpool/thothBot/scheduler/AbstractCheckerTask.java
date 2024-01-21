package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.oracle.CoinPaprikaOracle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import rest.koios.client.backend.api.account.model.AccountAsset;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.Asset;
import rest.koios.client.backend.api.pool.model.PoolInfo;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract checker task component holding generic data and utilities
 */
public abstract class AbstractCheckerTask {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCheckerTask.class);
    public static final int MAX_MSG_PAYLOAD_SIZE = 4096 - 512;

    protected static final long DEFAULT_PAGINATION_SIZE = 1000;
    public static final double LOVELACE = 1000000.0;
    public static final String ADA_SYMBOL = " " + '\u20B3';
    public static final String CARDANO_SCAN_STAKE_KEY = "https://cardanoscan.io/stakekey/";
    public static final String CARDANO_SCAN_ADDR_KEY = "https://cardanoscan.io/address/";
    public static final String CARDANO_SCAN_STAKE_POOL = "https://cardanoscan.io/pool/";

    public static final String CARDANO_SCAN_TX = "https://cardanoscan.io/transaction/";

    protected static final DateTimeFormatter TX_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, hh:mm a");

    @Autowired
    protected UserDao userDao;

    @Autowired
    protected KoiosFacade koiosFacade;
    @Autowired
    protected CoinPaprikaOracle oracle;
    @Autowired
    protected AssetFacade assetFacade;
    @Value("${thoth.users-batch-size:100}")
    protected Integer usersBatchSize;

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

    public static String shortenAddr(String address) {
        return address.substring(0, 6) + "..." + address.substring(address.length() - 8);
    }

    /**
     * Get the ADA Handle associated to the stake address stakeAddr, if any.
     * If there's no handle the shortenStakeAddr() will be returned.
     * If there are multiple handles, the first in alphabetical order will be shown (usually the most valuable)
     *
     * @param addresses list
     * @return the handle or short stake address organised by staking address
     */
    protected Map<String, String> getAdaHandleForAccount(String... addresses) {
        Map<String, String> handlesMap = new HashMap<>();

        List<String> stakingAddresses = Arrays.stream(addresses).filter(User::isStakingAddress).collect(Collectors.toList());
        List<String> normalAddresses = Arrays.stream(addresses).filter(User::isNormalAddress).collect(Collectors.toList());

        Set<String> processedAddresses = new HashSet<>();
        try {
            // Nominal address
            if (!normalAddresses.isEmpty()) {
                Result<List<AddressAsset>> addrAssetsResp = this.koiosFacade.getKoiosService().getAddressService().getAddressAssets(normalAddresses, null);
                if (addrAssetsResp.isSuccessful()) {
                    Set<String> allAddresses = addrAssetsResp.getValue().stream().map(AddressAsset::getAddress).collect(Collectors.toSet());
                    for (String addr : allAddresses) {
                        processedAddresses.add(addr);
                        Optional<Asset> bestHandle = addrAssetsResp.getValue().stream()
                                .filter(a -> a.getAddress().equals(addr))
                                .filter(a -> a.getPolicyId().equals(AssetFacade.ADA_HANDLE_POLICY_ID))
                                .map(a -> (Asset) a)
                                .findFirst();
                        if (bestHandle.isEmpty()) {
                            // Account has no handles
                            handlesMap.put(addr, shortenAddr(addr));
                        } else {
                            String handleName = this.assetFacade.getAssetDisplayName(bestHandle.get().getPolicyId(), bestHandle.get().getAssetName());

                            if (handleName == null)
                                handleName = shortenAddr(addr);
                            LOG.debug("Found handle {} for account {}", handleName, addr);
                            handlesMap.put(addr, handleName);
                        }
                    }
                } else {
                    LOG.warn("Can't get the assets for accounts {}, due to '{}' (code {}}. Returning the address shortened instead",
                            normalAddresses, addrAssetsResp.getResponse(), addrAssetsResp.getCode());
                }
            }

            // Staking address?
            if (!stakingAddresses.isEmpty()) {
                Result<List<AccountAsset>> accountAssetsResp = this.koiosFacade.getKoiosService().getAccountService().getAccountAssets(stakingAddresses, null, null);
                if (accountAssetsResp.isSuccessful()) {
                    Set<String> allStakeAddresses = accountAssetsResp.getValue().stream().map(AccountAsset::getStakeAddress).collect(Collectors.toSet());
                    for (String stakeAddr : allStakeAddresses) {
                        processedAddresses.add(stakeAddr);
                        Optional<Asset> bestHandle = accountAssetsResp.getValue().stream()
                                .filter(a -> a.getStakeAddress().equals(stakeAddr))
                                .filter(a -> a.getPolicyId().equals(AssetFacade.ADA_HANDLE_POLICY_ID))
                                .map(a -> (Asset) a)
                                .findFirst();
                        if (bestHandle.isEmpty()) {
                            // Account has no handles
                            handlesMap.put(stakeAddr, shortenAddr(stakeAddr));
                        } else {
                            String handleName = this.assetFacade.getAssetDisplayName(bestHandle.get().getPolicyId(), bestHandle.get().getAssetName());
                            if (handleName == null)
                                handleName = shortenAddr(stakeAddr);

                            LOG.debug("Found handle {} for account {}", handleName, stakeAddr);
                            handlesMap.put(stakeAddr, handleName);
                        }
                    }
                } else {
                    LOG.warn("Can't get the assets for accounts {}, due to '{}' (code {}}. Returning the stake address shortened instead",
                            stakingAddresses, accountAssetsResp.getResponse(), accountAssetsResp.getCode());
                }
            }
        } catch (Exception e) {
            LOG.warn("Exception while getting the assets for accounts {}, due to '{}'. Returning the address shortened instead",
                    stakingAddresses, e.toString());
        }

        // Handle the case of an address that has zero assets.
        normalAddresses.removeAll(processedAddresses);
        stakingAddresses.removeAll(processedAddresses);

        for (String addr : normalAddresses) {
            handlesMap.put(addr, shortenAddr(addr));
        }

        for (String addr : stakingAddresses) {
            handlesMap.put(addr, shortenAddr(addr));
        }

        return handlesMap;
    }

    public static String hexToAscii(String assetName, String policyId) {
        StringBuilder output = new StringBuilder();
        boolean isHandle = policyId.equals(AssetFacade.ADA_HANDLE_POLICY_ID);
        for (int i = 0; i < assetName.length(); i += 2) {
            String str = assetName.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }

        if (isHandle || StandardCharsets.US_ASCII.newEncoder().canEncode(output.toString())) {
            return output.toString();
        } else {
            return "..." + assetName.substring(Math.abs(assetName.length() - 10));
        }
    }
}
