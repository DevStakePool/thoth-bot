package com.devpool.thothBot.dao;

import com.devpool.thothBot.dao.data.Asset;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.MaxRegistrationsExceededException;
import org.apache.commons.collections4.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AssetsDao {
    private static final Logger LOG = LoggerFactory.getLogger(AssetsDao.class);

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @PostConstruct
    public void post() {
        LOG.info("Assets DAO initialised");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Optional<Asset> getAssetInformation(String policyId, String assetName) {
        List<Asset> assets = this.namedParameterJdbcTemplate.query(
                "select policy_id, asset_name, decimals from assets where policy_id = :policy_id and asset_name = :asset_name",
                Map.of("policy_id", policyId,
                        "asset_name", assetName),
                (rs, numRow) -> new Asset(
                        rs.getString("policy_id"),
                        rs.getString("asset_name"),
                        rs.getInt("decimals")
                ));

        if (assets.isEmpty()) return Optional.empty();

        return Optional.ofNullable(assets.get(0));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void addNewAsset(String policyId, String asset_name, Integer decimals) {
        namedParameterJdbcTemplate.update(
                "insert into assets (policy_id, asset_name, decimals) values (:policy_id, :asset_name, :decimals)",
                new MapSqlParameterSource(Map.of(
                        "policy_id", policyId,
                        "asset_name", asset_name,
                        "decimals", decimals)));

        LOG.debug("Inserted new asset with policy_id {}, asset_name {}, and decimals {}", policyId, asset_name, decimals);
    }
}
