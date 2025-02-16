package com.devpool.thothBot.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class PoolVotesDao {
    private static final Logger LOG = LoggerFactory.getLogger(PoolVotesDao.class);
    private static final String FIELD_GOV_ID = "gov_id";
    private static final String FIELD_BLOCK_TIME = "block_time";
    private static final String FIELD_POOL_ID = "pool_id";

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void post() {
        LOG.info("Pool Votes DAO initialised");
    }

    /**
     * Get the votes for a given gov action of a pool, lower than the given block time
     *
     * @param govId     gov action
     * @param poolId    the Pool ID
     * @param blockTime latest block time of the vote
     * @return the list of block times of all the pool votes for a given gov action
     */
    public List<Long> getVotesForGovAction(String govId, String poolId, long blockTime) {
        SqlRowSet rs = this.jdbcTemplate.queryForRowSet(
                """
                        select id, pool_id, block_time
                        from pool_votes
                        where gov_id = :gov_id and
                              pool_id = :pool_id and
                              block_time <= :block_time
                        """,
                Map.of(FIELD_GOV_ID, govId,
                        FIELD_POOL_ID, poolId,
                        FIELD_BLOCK_TIME, blockTime));

        List<Long> outcome = new ArrayList<>();
        while (rs.next()) {
            outcome.add(rs.getLong(FIELD_BLOCK_TIME));
        }

        return outcome;
    }

    public void addPoolVote(String govId, String poolId, Long blockTime) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                """
                        insert into pool_votes (gov_id, pool_id, block_time)
                            values (:gov_id, :pool_id, :block_time)
                        """,
                new MapSqlParameterSource(Map.of(
                        FIELD_GOV_ID, govId,
                        FIELD_POOL_ID, poolId,
                        FIELD_BLOCK_TIME, blockTime)),
                keyHolder, new String[]{"id"});

        LOG.debug("Inserted new pool vote with key {} for gov action {} and pool {} ",
                keyHolder.getKeyAs(Long.class), govId, poolId);
    }
}
