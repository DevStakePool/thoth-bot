package com.devpool.thothBot.koios;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.data.Asset;
import com.devpool.thothBot.exceptions.MaxRegistrationsExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.asset.model.AssetInformation;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;

import java.util.Optional;

@Component
public class AssetFacade {
    private static final Logger LOG = LoggerFactory.getLogger(AssetFacade.class);

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private AssetsDao assetsDao;

    public Object getAssetQuantity(String policyId, String assetName, Long quantity) throws ApiException, MaxRegistrationsExceededException {
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
}
