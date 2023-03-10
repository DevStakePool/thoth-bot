package com.devpool.thothBot.koios;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.Asset;
import com.devpool.thothBot.dao.data.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAssets;
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

@Component
public class AssetFacade implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(AssetFacade.class);

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private UserDao userDao;

    private ScheduledExecutorService scheduledExecutorService;

    private ExecutorService usersExecutorService;
    private ExecutorService assetsExecutorService;

    @PostConstruct
    public void post() throws Exception {
        LOG.info("Creating Asset Facade");
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1,
                new CustomizableThreadFactory("MainAssetSyncWorker"));

        this.scheduledExecutorService.scheduleWithFixedDelay(this, 5, 120, TimeUnit.SECONDS);

        this.usersExecutorService = Executors.newFixedThreadPool(5,
                new CustomizableThreadFactory("UserScanSyncWorker"));

        this.assetsExecutorService = Executors.newFixedThreadPool(10,
                new CustomizableThreadFactory("AssetScanSyncWorker"));
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down executor services");
        this.scheduledExecutorService.shutdown();
        this.assetsExecutorService.shutdown();
        this.usersExecutorService.shutdown();
    }

    public Object getAssetQuantity(String policyId, String assetName, Long quantity) throws ApiException {
        Optional<Asset> cachedAsset = this.assetsDao.getAssetInformation(policyId, assetName);
        Object assetQuantity = quantity;
        if (cachedAsset.isEmpty()) {
            // We need to get the decimals for the asset. Note, this will be cached
            Result<AssetInformation> assetInfoResult = this.koiosFacade.getKoiosService()
                    .getAssetService().getAssetInformation(policyId, assetName);
            if (!assetInfoResult.isSuccessful()) {
                LOG.warn("Failed to retrieve asset {} information from KOIOS, due to {} ({})",
                        policyId, assetInfoResult.getResponse(), assetInfoResult.getCode());
            }

            if (assetInfoResult.isSuccessful() && assetInfoResult.getValue().getTokenRegistryMetadata() != null) {
                assetQuantity = Long.valueOf(quantity) / (1.0 * Math.pow(10, assetInfoResult.getValue().getTokenRegistryMetadata().getDecimals()));
            }

            // Cache it for the future
            this.assetsDao.addNewAsset(policyId, assetName,
                    assetInfoResult.getValue().getTokenRegistryMetadata() == null ? -1 :
                            assetInfoResult.getValue().getTokenRegistryMetadata().getDecimals());
        } else {
            // We have it cached
            if (cachedAsset.get().getDecimals() != -1)
                assetQuantity = Long.valueOf(quantity) / (Math.pow(10, cachedAsset.get().getDecimals()));
        }

        return assetQuantity;
    }

    public String formatAssetQuantity(Object assetQuantity) {
        return assetQuantity instanceof Double ?
                String.format("%,.2f", assetQuantity) :
                String.format("%,d", assetQuantity);
    }

    @Override
    public void run() {
        LOG.debug("Syncing assets cached information");

        try {
            List<User> allUsers = this.userDao.getUsers();

            for (User u : allUsers) {
                this.usersExecutorService.submit(new UserScannerWorker(u, this.koiosFacade));
            }
        } catch (Exception e) {
            LOG.error("Unknown exception while syncing the assets cache", e);
        }
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
                LOG.debug("Syncing assets for account {}", this.user.getStakeAddr());

                Result<List<AccountAssets>> result = this.koiosFacade.getKoiosService()
                        .getAccountService().getAccountAssets(List.of(user.getStakeAddr()), null, null);
                if (!result.isSuccessful()) {
                    LOG.warn("Can't sync the user {} assets, due to {}", this.user.getStakeAddr(), result.getResponse());
                    return;
                }

                Optional<AccountAssets> assetForAccount = result.getValue().stream().findFirst();
                if (assetForAccount.isEmpty()) {
                    LOG.debug("The account {} has no assets to sync", this.user.getStakeAddr());
                    return;
                }
                for (rest.koios.client.backend.api.common.Asset asset : assetForAccount.get().getAssetList()) {
                    AssetFacade.this.assetsExecutorService.submit(new AssetScannerWorker(asset));
                }
            } catch (ApiException e) {
                LOG.warn("Issue while syncing the assets for user {}, due to {}", this.user.getStakeAddr(), e.toString());
            } catch (Exception e) {
                LOG.error("Unknown error in user worker for user {}", this.user.getStakeAddr(), e);
            }
        }
    }

    public class AssetScannerWorker implements Runnable {
        private final Logger LOG = LoggerFactory.getLogger(AssetScannerWorker.class);

        private final rest.koios.client.backend.api.common.Asset asset;

        public AssetScannerWorker(rest.koios.client.backend.api.common.Asset asset) {
            this.asset = asset;
        }

        @Override
        public void run() {
            try {
                LOG.trace("Syncing asset {}, {}", this.asset.getPolicyId(), this.asset.getAssetName());
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
