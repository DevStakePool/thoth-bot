package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.model.model.Body;
import com.devpool.thothBot.model.model.DrepMetadata;
import com.devpool.thothBot.oracle.CoinPaprikaOracle;
import com.devpool.thothBot.util.CollectionsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import rest.koios.client.backend.api.account.model.AccountAsset;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.Asset;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract checker task component holding generic data and utilities
 */
public abstract class AbstractCheckerTask {
    protected static final String DREP_HASH_PREFIX = "drep1";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCheckerTask.class);
    public static final int MAX_MSG_PAYLOAD_SIZE = 4096 - 512;

    protected static final long DEFAULT_PAGINATION_SIZE = 1000;
    public static final double LOVELACE = 1000000.0;
    public static final String ADA_SYMBOL = " " + '\u20B3';
    public static final String CARDANO_SCAN_STAKE_KEY = "https://cardanoscan.io/stakekey/";
    public static final String CARDANO_SCAN_ADDR_KEY = "https://cardanoscan.io/address/";
    public static final String CARDANO_SCAN_STAKE_POOL = "https://cardanoscan.io/pool/";
    public static final String GOV_TOOLS_DREP = "https://cardanoscan.io/dRep/";
    public static final String GOV_TOOLS_PROPOSAL = "https://cardanoscan.io/govAction/%s?tab=meta";
    public static final String CARDANO_SCAN_TX = "https://cardanoscan.io/transaction/";

    protected static final DateTimeFormatter TX_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, hh:mm a");
    protected static final String IPFS_SCHEME = "ipfs";
    protected static final String IPFS_HTTP_URI = "https://c-ipfs-gw.nmkr.io/ipfs/%s";

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
    @Autowired
    protected RestTemplate restTemplate;


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

    protected String getPoolName(PoolInfo poolInfo) {
        if (poolInfo.getMetaJson() != null && poolInfo.getMetaJson().getTicker() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(poolInfo.getMetaJson().getTicker());
            sb.append("]");
            if (poolInfo.getMetaJson().getName() != null)
                sb.append(" ").append(poolInfo.getMetaJson().getName());

            return sb.toString();
        }

        return "pool1..." + poolInfo.getPoolIdBech32().substring(poolInfo.getPoolIdBech32().length() - 8);
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

        if (LOG.isTraceEnabled()) {
            LOG.trace("Found handles {}", handlesMap);
        }

        return handlesMap;
    }

    protected Map<String, String> getDrepNames(List<String> drepIds) {
        Map<String, String> drepNames = new HashMap<>();
        drepIds.forEach(d -> drepNames.put(d, shortenDrepHash(d)));
        try {
            var drepResp = this.koiosFacade.getKoiosService().getGovernanceService()
                    .getDRepsInfo(drepIds.stream()
                            .filter(d -> d.startsWith
                                    (DREP_HASH_PREFIX))
                            .collect(Collectors.toList()), null);
            if (drepResp.isSuccessful()) {
                for (var drep : drepResp.getValue()) {
                    var drepUrl = drep.getMetaUrl();
                    if (drepUrl != null) {
                        LOG.debug("Drep {} has the url {}", drep.getDrepId(), drepUrl);

                        try {
                            var uri = new URI(drepUrl);
                            uri = handleIpfsUri(uri);
                            ResponseEntity<DrepMetadata> entity = this.restTemplate.getForEntity(uri, DrepMetadata.class);
                            var givenName = Optional.ofNullable(entity.getBody())
                                    .map(DrepMetadata::getBody)
                                    .map(Body::getGivenName)
                                    .map(Object::toString)
                                    .orElse(null);

                            if (entity.getStatusCode().equals(HttpStatus.OK) && givenName != null) {
                                LOG.debug("Got a DRep name {} for ID {}", givenName, drep.getDrepId());
                                drepNames.put(drep.getDrepId(), givenName);
                            }
                        } catch (Exception e) {
                            LOG.warn("Can't get drep metadata from URL {} due to {}", drepUrl, e.toString());
                        }
                    }
                }
            } else
                LOG.warn("Cannot retrieve drep information due to {}", drepResp.getResponse());
        } catch (ApiException e) {
            LOG.warn("Cannot retrieve drep information: {}", e, e);
        }

        return drepNames;
    }

    protected URI handleIpfsUri(URI uri) throws URISyntaxException {
        if (!IPFS_SCHEME.equals(uri.getScheme()))
            return uri;


        var httpUri = new URI(IPFS_HTTP_URI.formatted(uri.getSchemeSpecificPart()));
        LOG.debug("Converted IPFS uri {} to HTTP uri {}", uri, httpUri);

        return httpUri;
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

    protected String shortenDrepHash(String drepHash) {
        if (drepHash.startsWith(DREP_HASH_PREFIX))
            return DREP_HASH_PREFIX + "..." + drepHash.substring(drepHash.length() - 8);

        return drepHash;
    }

    protected String constructInFilter(Collection<String> items) {
        return "(" + String.join(",", items) + ")";
    }

    // returns Staking Address -> Pool Address
    protected Map<String, String> collectPoolAddressesAssociatedToStakingAddresses(List<String> allStakingAddresses) throws ApiException {
        Map<String, String> allPoolIdsStakingAddresses = new HashMap<>();
        Options options = Options.builder()
                .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                .option(Offset.of(0))
                .build();

        var iter = CollectionsUtil.batchesList(allStakingAddresses, usersBatchSize).iterator();
        while (iter.hasNext()) {
            var batch = iter.next();
            LOG.debug("Grabbing staking account info for batch of size {}", batch.size());
            var resp = koiosFacade.getKoiosService().getAccountService().getCachedAccountInformation(batch, options);
            if (!resp.isSuccessful()) {
                throw new ApiException(String.format("Invalid API response while getting account infos: %d - %s",
                        resp.getCode(), resp.getResponse()));
            }

            for (AccountInfo accountInfo : resp.getValue()) {
                var stakingAddr = accountInfo.getStakeAddress();
                var poolAddr = accountInfo.getDelegatedPool();
                if (poolAddr != null)
                    allPoolIdsStakingAddresses.put(stakingAddr, poolAddr);
            }
        }

        return allPoolIdsStakingAddresses;
    }
}
