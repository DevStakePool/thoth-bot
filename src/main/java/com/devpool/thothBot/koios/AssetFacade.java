package com.devpool.thothBot.koios;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.Asset;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAsset;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.asset.model.AssetInformation;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class AssetFacade implements Runnable {
    public static final String UNKNOWN = "UNKNOWN";
    private static final Logger LOG = LoggerFactory.getLogger(AssetFacade.class);
    public static final String ADA_HANDLE_PREFIX = "$";
    public static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";


    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private UserDao userDao;

    private ScheduledExecutorService scheduledExecutorService;

    private ExecutorService usersExecutorService;
    private ExecutorService assetsExecutorService;

    @Value("${thoth.asset.fetching.enabled:true}")
    private boolean isFetchingEnabled;


    @PostConstruct
    public void post() {
        LOG.info("Creating Asset Facade");
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1,
                new CustomizableThreadFactory("MainAssetSyncWorker"));

        this.usersExecutorService = Executors.newFixedThreadPool(5,
                new CustomizableThreadFactory("UserScanSyncWorker"));

        this.assetsExecutorService = Executors.newFixedThreadPool(5,
                new CustomizableThreadFactory("AssetScanSyncWorker"));


        if (this.isFetchingEnabled) {
            LOG.info("Starting Asset Facade Fetching mechanism");
            this.scheduledExecutorService.scheduleWithFixedDelay(this, 1, 30, TimeUnit.MINUTES);
        }
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down executor services");
        this.scheduledExecutorService.shutdown();
        this.assetsExecutorService.shutdown();
        this.usersExecutorService.shutdown();
    }

    public void refreshAssetsForUserNow(String addr) {
        this.usersExecutorService.submit(new UserScannerWorker(new User(-1L, addr, -1, -1), this.koiosFacade));
    }

    public String getAssetDisplayName(String policyId, String assetName) throws ApiException {
        Optional<Asset> cachedAsset = this.assetsDao.getAssetInformation(policyId, assetName);
        String displayName = AbstractCheckerTask.hexToAscii(assetName, policyId);

        if (cachedAsset.isEmpty()) {
            LOG.debug("Asset {}, {} not cached. Retrieving it...", policyId, assetName);
            // We need to get the decimals for the asset. Note, this will be cached
            Result<AssetInformation> assetInfoResult = this.koiosFacade.getKoiosService()
                    .getAssetService().getAssetInformation(policyId, assetName);
            if (!assetInfoResult.isSuccessful()) {
                LOG.warn("Failed to retrieve asset {} information from KOIOS, due to {} ({})",
                        policyId, assetInfoResult.getResponse(), assetInfoResult.getCode());
            } else if (assetInfoResult.isSuccessful() && assetInfoResult.getValue().getTokenRegistryMetadata() != null) {
                displayName = assetInfoResult.getValue().getTokenRegistryMetadata().getName();
            }

            if (assetInfoResult.isSuccessful()) {
                if (ADA_HANDLE_POLICY_ID.equals(policyId)) {
                    // normalize ADA handle
                    if (displayName.indexOf('@') != -1) {
                        displayName = displayName.substring(displayName.indexOf('@') + 1);
                    }
                    displayName = ADA_HANDLE_PREFIX + displayName;
                }
                // Cache it for the future
                this.assetsDao.addNewAsset(policyId, assetName, displayName,
                        assetInfoResult.getValue().getTokenRegistryMetadata() == null ? -1 :
                                assetInfoResult.getValue().getTokenRegistryMetadata().getDecimals());
            }
        } else {
            if (cachedAsset.get().getAssetDisplayName() != null)
                displayName = cachedAsset.get().getAssetDisplayName();
        }

        return displayName;
    }

    public Object getAssetQuantity(String policyId, String assetName, Long quantity) throws ApiException {
        Optional<Asset> cachedAsset = this.assetsDao.getAssetInformation(policyId, assetName);
        Object assetQuantity = quantity;
        String displayName = AbstractCheckerTask.hexToAscii(assetName, policyId);
        if (cachedAsset.isEmpty()) {
            LOG.debug("Asset {}, {} not cached. Retrieving it...", policyId, assetName);
            // We need to get the decimals for the asset. Note, this will be cached
            Result<AssetInformation> assetInfoResult = this.koiosFacade.getKoiosService()
                    .getAssetService().getAssetInformation(policyId, assetName);
            if (!assetInfoResult.isSuccessful()) {
                LOG.warn("Failed to retrieve asset {} information from KOIOS, due to {} ({})",
                        policyId, assetInfoResult.getResponse(), assetInfoResult.getCode());
            } else if (assetInfoResult.isSuccessful() && assetInfoResult.getValue().getTokenRegistryMetadata() != null) {
                assetQuantity = quantity / Math.pow(10, assetInfoResult.getValue().getTokenRegistryMetadata().getDecimals());
                if (assetInfoResult.getValue().getTokenRegistryMetadata().getName() != null)
                    displayName = assetInfoResult.getValue().getTokenRegistryMetadata().getName();
            }

            if (assetInfoResult.isSuccessful()) {
                if (ADA_HANDLE_POLICY_ID.equals(policyId)) {
                    // normalize ADA handle
                    if (displayName.indexOf('@') != -1) {
                        displayName = displayName.substring(displayName.indexOf('@') + 1);
                    }
                    displayName = ADA_HANDLE_PREFIX + displayName;
                }
                // Cache it for the future
                this.assetsDao.addNewAsset(policyId, assetName, displayName,
                        assetInfoResult.getValue().getTokenRegistryMetadata() == null ? -1 :
                                assetInfoResult.getValue().getTokenRegistryMetadata().getDecimals());
            }
        } else {
            // We have it cached
            if (cachedAsset.get().getDecimals() != -1)
                assetQuantity = quantity / Math.pow(10, cachedAsset.get().getDecimals());
        }

        return assetQuantity;
    }

    public String formatAssetQuantity(Object assetQuantity) {
        if (assetQuantity == null) return "?";

        return assetQuantity instanceof Double ?
                String.format("%,.2f", (Double) assetQuantity) :
                String.format("%,d", (Long) assetQuantity);
    }

    @Override
    public void run() {
        LOG.debug("Syncing assets cached information");

        try {
            List<User> allUsers = this.userDao.getUsers();

            int flowControl = 0;
            for (User u : allUsers) {
                if (flowControl % 2 == 0)
                    Thread.sleep(3000); // Avoid reaching Koios limits
                this.usersExecutorService.submit(new UserScannerWorker(u, this.koiosFacade));
                flowControl++;
            }
        } catch (Exception e) {
            LOG.error("Unknown exception while syncing the assets cache", e);
            Thread.currentThread().interrupt();
        }
    }

    public long countTotalCachedAssets() {
        return this.assetsDao.countAll();
    }

    public class UserScannerWorker implements Runnable {
        private final Logger LOG = LoggerFactory.getLogger(UserScannerWorker.class);
        private final User user;
        private final KoiosFacade koiosFacade;

        public UserScannerWorker(User user, KoiosFacade koiosFacade) {
            this.user = user;
            this.koiosFacade = koiosFacade;
        }

        @Override
        public void run() {
            try {
                LOG.debug("Syncing assets for account {}", this.user.getAddress());
                List<rest.koios.client.backend.api.base.common.Asset> assetsToProcess;

                if (this.user.isStakeAddress()) {
                    Result<List<AccountAsset>> result = this.koiosFacade.getKoiosService()
                            .getAccountService().getAccountAssets(List.of(user.getAddress()), null, null);
                    if (!result.isSuccessful()) {
                        LOG.warn("Can't sync the user {} assets, due to {}", this.user.getAddress(), result.getResponse());
                        return;
                    }

                    if (result.getValue().isEmpty()) {
                        LOG.debug("The account {} has no assets to sync", this.user.getAddress());
                        return;
                    }
                    assetsToProcess = result.getValue().stream().map(rest.koios.client.backend.api.base.common.Asset.class::cast).collect(Collectors.toList());
                } else {
                    // Non-staking address
                    Result<List<AddressAsset>> result = this.koiosFacade.getKoiosService()
                            .getAddressService().getAddressAssets(List.of(user.getAddress()), null);
                    if (!result.isSuccessful()) {
                        LOG.warn("Can't sync the user {} assets, due to {}", this.user.getAddress(), result.getResponse());
                        return;
                    }

                    if (result.getValue().isEmpty()) {
                        LOG.debug("The account {} has no assets to sync", this.user.getAddress());
                        return;
                    }
                    assetsToProcess = result.getValue().stream().map(rest.koios.client.backend.api.base.common.Asset.class::cast).collect(Collectors.toList());
                }

                int flowControl = 0;
                for (rest.koios.client.backend.api.base.common.Asset asset : assetsToProcess) {
                    if (flowControl % 2 == 0)
                        Thread.sleep(3000); //Avoid saturating the Koios limits
                    AssetFacade.this.assetsExecutorService.submit(new AssetScannerWorker(asset));
                    flowControl++;
                }
            } catch (ApiException e) {
                LOG.warn("Issue while syncing the assets for user {}, due to {}", this.user.getAddress(), e.toString());
            } catch (Exception e) {
                LOG.error("Unknown error in user worker for user {}", this.user.getAddress(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    public class AssetScannerWorker implements Runnable {
        private final Logger LOG = LoggerFactory.getLogger(AssetScannerWorker.class);

        private final rest.koios.client.backend.api.base.common.Asset asset;

        public AssetScannerWorker(rest.koios.client.backend.api.base.common.Asset asset) {
            this.asset = asset;
        }

        @Override
        public void run() {
            try {
                AssetFacade.this.getAssetQuantity(
                        this.asset.getPolicyId(), asset.getAssetName(), Long.parseLong(asset.getQuantity()));
            } catch (ApiException e) {
                LOG.warn("API Error when trying to refresh the asset cache for {}, {}. Due to {}",
                        this.asset.getPolicyId(), this.asset.getAssetName(), e.toString());
            } catch (Exception e) {
                LOG.error("Unknown error in asset worker for asset {}, {}",
                        this.asset.getPolicyId(), this.asset.getAssetName(), e);
            }
        }
    }

}
