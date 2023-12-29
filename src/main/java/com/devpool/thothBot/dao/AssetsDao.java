package com.devpool.thothBot.dao;

import com.devpool.thothBot.dao.data.Asset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AssetsDao {
    private static final Logger LOG = LoggerFactory.getLogger(AssetsDao.class);
    private static final String FIELD_POLICY_ID = "policy_id";
    private static final String FIELD_ASSET_NAME = "asset_name";
    private static final String FIELD_DECIMALS = "decimals";
    private static final String FIELD_ASSET_DISPLAY_NAME = "asset_display_name";
    private static final String FIELD_ID = "id";

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @PostConstruct
    public void post() {
        LOG.info("Assets DAO initialised");
    }

    public Optional<Asset> getAssetInformation(String policyId, String assetName) {
        List<Asset> assets = this.namedParameterJdbcTemplate
                .query("select id, policy_id, asset_name, asset_display_name, decimals from assets where policy_id = :policy_id and asset_name = :asset_name",
                        Map.of(FIELD_POLICY_ID, policyId, FIELD_ASSET_NAME, assetName), (rs, numRow) ->
                                new Asset(rs.getLong(FIELD_ID), rs.getString(FIELD_POLICY_ID),
                                        rs.getString(FIELD_ASSET_NAME), rs.getString(FIELD_ASSET_DISPLAY_NAME), rs.getInt(FIELD_DECIMALS)));

        if (assets.isEmpty()) return Optional.empty();

        return Optional.ofNullable(assets.get(0));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void addNewAsset(String policyId, String assetName, String displayName, Integer decimals) {

        // Check again for existence
        if (getAssetInformation(policyId, assetName).isEmpty()) {
            namedParameterJdbcTemplate.update("insert into assets (policy_id, asset_name, display_name, decimals) values (:policy_id, :asset_name, :display_name, :decimals)",
                    new MapSqlParameterSource(Map.of(FIELD_POLICY_ID, policyId, FIELD_ASSET_NAME, assetName, FIELD_ASSET_DISPLAY_NAME, displayName, FIELD_DECIMALS, decimals)));

            LOG.debug("Inserted new asset with policy_id {}, asset_name {}, and decimals {}", policyId, assetName, decimals);
        }
    }

    public long countAll() {
        Long outcome = this.jdbcTemplate.queryForObject("select count(id) as assets_counter from assets", Long.class);
        if (outcome == null) return -1;

        return outcome;
    }
}
