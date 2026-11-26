package com.devpool.thothBot.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * Keeps track of the last block minted by a pool for which a notification has been sent
 */
@Repository
public class PoolBlocksDao {
    private static final String FIELD_POOL_ID = "pool_id";
    private static final String FIELD_LAST_BLOCK_HEIGHT = "last_block_height";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PoolBlocksDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Optional<Long> getLastBlockHeight(String poolId) {
        return this.namedParameterJdbcTemplate.query(
                        "select last_block_height from pool_blocks where pool_id = :pool_id",
                        Map.of(FIELD_POOL_ID, poolId),
                        (rs, rowNum) -> rs.getLong(FIELD_LAST_BLOCK_HEIGHT))
                .stream().findFirst();
    }

    public void upsertLastBlockHeight(String poolId, long lastBlockHeight) {
        this.namedParameterJdbcTemplate.update(
                """
                        insert into pool_blocks (pool_id, last_block_height)
                            values (:pool_id, :last_block_height)
                        on conflict (pool_id) do update set last_block_height = excluded.last_block_height
                        """,
                Map.of(FIELD_POOL_ID, poolId, FIELD_LAST_BLOCK_HEIGHT, lastBlockHeight));
    }
}
