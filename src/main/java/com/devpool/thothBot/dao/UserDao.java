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
                "select id, chat_id, addr, last_block_height, last_epoch_number from users");
        Map<Long, User> users = new HashedMap<>();
        while (rs.next()) {
            Long userId = rs.getLong("id");
            User u = new User();
            u.setId(userId);
            u.setChatId(rs.getLong(FIELD_CHAT_ID));
            u.setAddress(rs.getString(FIELD_ADDR));
            u.setLastBlockHeight(rs.getInt(FIELD_LAST_BLOCK_HEIGHT));
            u.setLastEpochNumber(rs.getInt(FIELD_LAST_EPOCH_NUMBER));
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
                "insert into users (chat_id, addr, last_block_height, last_epoch_number) values (:chat_id, :addr, :last_block_height, :last_epoch_number)",
                new MapSqlParameterSource(Map.of(
                        FIELD_CHAT_ID, user.getChatId(),
                        FIELD_ADDR, user.getAddress(),
                        FIELD_LAST_BLOCK_HEIGHT, user.getLastBlockHeight(),
                        FIELD_LAST_EPOCH_NUMBER, user.getLastEpochNumber())), keyHolder, new String[]{"id"});

        LOG.debug("Inserted new user with key {}: {}", keyHolder.getKeyAs(Long.class), user);
    }

    public void updateUserBlockHeight(Long id, Integer blockHeight) {
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
                "select id, chat_id, addr, last_block_height, last_epoch_number from users where id = :id",
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

        return u;
    }
}
