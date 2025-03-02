package com.devpool.thothBot.dao;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.UserNotFoundException;
import org.apache.commons.collections4.map.HashedMap;
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
public class UserDao {
    private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);
    private static final String FIELD_CHAT_ID = "chat_id";
    private static final String FIELD_ADDR = "addr";
    private static final String FIELD_LAST_BLOCK_HEIGHT = "last_block_height";
    private static final String FIELD_LAST_EPOCH_NUMBER = "last_epoch_number";
    private static final String FIELD_LAST_GOV_VOTES_BLOCK_TIME = "last_gov_votes_block_time";
    private static final String FIELD_LAST_GOV_ACTION_BLOCK_TIME = "last_gov_action_block_time";
    private static final String FIELD_POOL_ID = "pool_id";
    private static final String FIELD_REMAINING_NOTIFICATIONS = "remaining_notifications";
    public static final Integer DEFAULT_RETIRING_POOL_NOTIFICATIONS = 5;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void post() {
        LOG.info("User DAO initialised");
    }

    public List<User> getUsers() {
        SqlRowSet rs = this.jdbcTemplate.queryForRowSet(
                "select id, chat_id, addr, last_block_height, last_epoch_number, last_gov_votes_block_time, last_gov_action_block_time from users");
        Map<Long, User> users = new HashedMap<>();
        while (rs.next()) {
            Long userId = rs.getLong("id");
            User u = new User();
            u.setId(userId);
            u.setChatId(rs.getLong(FIELD_CHAT_ID));
            u.setAddress(rs.getString(FIELD_ADDR));
            u.setLastBlockHeight(rs.getInt(FIELD_LAST_BLOCK_HEIGHT));
            u.setLastEpochNumber(rs.getInt(FIELD_LAST_EPOCH_NUMBER));
            u.setLastGovVotesBlockTime(rs.getLong(FIELD_LAST_GOV_VOTES_BLOCK_TIME));
            u.setLastGovActionBlockTime(rs.getLong(FIELD_LAST_GOV_ACTION_BLOCK_TIME));
            users.putIfAbsent(userId, u);
        }
        return new ArrayList<>(users.values());
    }

    public long countSubscriptions() {
        Long outcome = this.jdbcTemplate.queryForObject("select count(id) as users_counter from users", Long.class);
        if (outcome == null) return -1;
        return outcome;
    }

    public long countUniqueUsers() {
        Long outcome = this.jdbcTemplate.queryForObject("select count (distinct chat_id) as tot_users from users", Long.class);
        if (outcome == null) return -1;
        else return outcome;
    }

    public void addNewUser(User user) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                "insert into users (chat_id, addr, last_block_height, last_epoch_number, last_gov_votes_block_time) values (:chat_id, :addr, :last_block_height, :last_epoch_number, :last_gov_votes_block_time)",
                new MapSqlParameterSource(Map.of(
                        FIELD_CHAT_ID, user.getChatId(),
                        FIELD_ADDR, user.getAddress(),
                        FIELD_LAST_BLOCK_HEIGHT, user.getLastBlockHeight(),
                        FIELD_LAST_EPOCH_NUMBER, user.getLastEpochNumber(),
                        FIELD_LAST_GOV_VOTES_BLOCK_TIME, user.getLastGovVotesBlockTime())),
                keyHolder, new String[]{"id"});

        LOG.debug("Inserted new user with key {}: {}", keyHolder.getKeyAs(Long.class), user);
    }

    public void updateUserBlockHeight(Long id, long blockHeight) {
        int updatedNumOfRows = namedParameterJdbcTemplate.update(
                "update users set last_block_height = :last_block_height where id = :id",
                new MapSqlParameterSource(Map.of(
                        FIELD_LAST_BLOCK_HEIGHT, blockHeight,
                        "id", id)));

        if (updatedNumOfRows != 1) {
            LOG.error("Unexpected updated number of rows {} for the user with id {}, when updating the block height. This is a bug!",
                    updatedNumOfRows, id);
        } else {
            LOG.debug("Updated block height to {} for user ID {}", blockHeight, id);
        }
    }

    public void updateUserGovVotesBlockTime(Long id, long timestamp) {
        int updatedNumOfRows = namedParameterJdbcTemplate.update(
                "update users set last_gov_votes_block_time = :last_gov_votes_block_time where id = :id",
                new MapSqlParameterSource(Map.of(
                        FIELD_LAST_GOV_VOTES_BLOCK_TIME, timestamp,
                        "id", id)));

        if (updatedNumOfRows != 1) {
            LOG.error("Unexpected updated number of rows {} for the user with id {}, when updating the governance votes block time. This is a bug!",
                    updatedNumOfRows, id);
        } else {
            LOG.debug("Updated governance votes block time to {} for user ID {}", timestamp, id);
        }
    }

    public void updateUserGovActionBlockTime(Long id, long timestamp) {
        int updatedNumOfRows = namedParameterJdbcTemplate.update(
                "update users set last_gov_action_block_time = :last_gov_action_block_time where id = :id",
                new MapSqlParameterSource(Map.of(
                        FIELD_LAST_GOV_ACTION_BLOCK_TIME, timestamp,
                        "id", id)));

        if (updatedNumOfRows != 1) {
            LOG.error("Unexpected updated number of rows {} for the user with id {}, when updating the governance action block time. This is a bug!",
                    updatedNumOfRows, id);
        } else {
            LOG.debug("Updated governance action block time to {} for user ID {}", timestamp, id);
        }
    }

    public void updateUserEpochNumber(Long id, Integer epochNumber) {
        int updatedNumOfRows = namedParameterJdbcTemplate.update(
                "update users set last_epoch_number = :last_epoch_number where id = :id",
                new MapSqlParameterSource(Map.of(
                        FIELD_LAST_EPOCH_NUMBER, epochNumber,
                        "id", id)));

        if (updatedNumOfRows != 1) {
            LOG.error("Unexpected updated number of rows for the user with id {}, when updating the epoch number. This is a bug!", id);
        } else {
            LOG.debug("Updated user epoch number to {} for user ID {}", epochNumber, id);
        }
    }

    public boolean removeAddress(Long chatId, String addr) {
        int removedRows = this.namedParameterJdbcTemplate.update(
                "delete from users where chat_id = :chat_id and addr = :addr;",
                Map.of(FIELD_CHAT_ID, chatId,
                        FIELD_ADDR, addr));

        if (removedRows > 1)
            LOG.error("Unexpected deletion of address {} for chat-id {}. The expected removed rows was 1 but got {}",
                    addr, chatId, removedRows);

        if (removedRows == 0)
            LOG.warn("Cannot remove the address {} with chat-id {}. Entry not found", addr, chatId);

        if (removedRows == 1)
            LOG.debug("Successfully unsubscribed the address {} with chat-id {}", addr, chatId);

        return removedRows == 1;
    }

    public User getUser(long id) throws UserNotFoundException {
        SqlRowSet rs = this.namedParameterJdbcTemplate.queryForRowSet(
                "select id, chat_id, addr, last_block_height, last_epoch_number, last_gov_votes_block_time from users where id = :id",
                Map.of("id", id));

        if (!rs.next()) {
            LOG.warn("Cannot find the user with ID {}", id);
            throw new UserNotFoundException("Cannot find the user with ID " + id);
        }

        User u = new User();
        u.setId(rs.getLong("id"));
        u.setChatId(rs.getLong(FIELD_CHAT_ID));
        u.setAddress(rs.getString(FIELD_ADDR));
        u.setLastBlockHeight(rs.getInt(FIELD_LAST_BLOCK_HEIGHT));
        u.setLastEpochNumber(rs.getInt(FIELD_LAST_EPOCH_NUMBER));
        u.setLastGovVotesBlockTime(rs.getLong(FIELD_LAST_GOV_VOTES_BLOCK_TIME));

        return u;
    }

    public int getRemainingUserNotificationForRetiringPool(long chatId, String poolId) {
        SqlRowSet rs = this.namedParameterJdbcTemplate.queryForRowSet(
                "select remaining_notifications from retiring_pools where chat_id = :chat_id and pool_id = :pool_id",
                Map.of(FIELD_CHAT_ID, chatId,
                        FIELD_POOL_ID, poolId));

        if (!rs.next()) {
            LOG.debug("Retiring pool {} for chat-id {} not found. Adding a default with {} notifications left",
                    poolId, chatId, DEFAULT_RETIRING_POOL_NOTIFICATIONS);
            setRemainingUserNotificationForRetiringPool(chatId, poolId, DEFAULT_RETIRING_POOL_NOTIFICATIONS);
            return DEFAULT_RETIRING_POOL_NOTIFICATIONS;
        }

        return rs.getInt("remaining_notifications");
    }

    public void setRemainingUserNotificationForRetiringPool(long chatId, String poolId, int value) {
        var affectedRows = this.namedParameterJdbcTemplate.update(
                "update retiring_pools set remaining_notifications = :value where chat_id = :chat_id and pool_id = :pool_id",
                Map.of("value", value,
                        FIELD_CHAT_ID, chatId,
                        FIELD_POOL_ID, poolId));

        if (affectedRows == 0) {
            LOG.debug("Adding new retiring pool for pool-id {}, chat-id {} and remaining-notifications {}",
                    poolId, chatId, value);
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            this.namedParameterJdbcTemplate.update(
                    "insert into retiring_pools (chat_id, pool_id, remaining_notifications) values(:chat_id, :pool_id, :remaining_notifications)",
                    new MapSqlParameterSource(Map.of(
                            FIELD_CHAT_ID, chatId,
                            FIELD_POOL_ID, poolId,
                            FIELD_REMAINING_NOTIFICATIONS, value)),
                    keyHolder, new String[]{"id"});

            LOG.debug("Inserted new retiring pool {}, with chat-id {} and with key {}",
                    poolId, chatId, keyHolder.getKeyAs(Long.class));
        }
    }
}
