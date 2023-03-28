package com.devpool.thothBot.dao;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.MaxRegistrationsExceededException;
import com.devpool.thothBot.exceptions.UserNotFoundException;
import org.apache.commons.collections4.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Collectors;

@Repository
public class UserDao {
    private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${thoth.max-user-registrations:5}")
    private Integer maxUserRegistrations;

    @PostConstruct
    public void post() {
        LOG.info("User DAO initialised");
    }

    public List<User> getUsers() {
        // TODO eventually change the stake_addr to just addr (see issue #11)
        SqlRowSet rs = this.jdbcTemplate.queryForRowSet(
                "select id, chat_id, stake_addr, last_block_height, last_epoch_number from users");
        Map<Long, User> users = new HashedMap<>();
        while (rs.next()) {
            Long userId = rs.getLong("id");
            User u = users.get(userId);
            if (u == null) {
                u = new User();
                u.setId(userId);
                u.setChatId(rs.getLong("chat_id"));
                u.setAddress(rs.getString("stake_addr"));
                u.setLastBlockHeight(rs.getInt("last_block_height"));
                u.setLastEpochNumber(rs.getInt("last_epoch_number"));
                users.put(userId, u);
            }
        }
        return users.values().stream().collect(Collectors.toCollection(ArrayList::new));
    }

    public long countUsers() {
        return this.jdbcTemplate.queryForObject("select count(id) as users_counter from users", Long.class);
    }

    public void addNewUser(User user) throws MaxRegistrationsExceededException {
        // First, check if the user exists, and it has more than this.maxUserRegistrations address already registered
        Long counter = this.namedParameterJdbcTemplate.queryForObject("select count(u.id) as registrations from users as u where chat_id = :chat_id",
                new MapSqlParameterSource(Map.of("chat_id", user.getChatId())), Long.class);

        if (counter >= this.maxUserRegistrations) {
            MaxRegistrationsExceededException exception = new MaxRegistrationsExceededException(
                    "The max number of registered wallets (" + this.maxUserRegistrations + ") has been reached");
            exception.setMaxRegistrationsAllowed(this.maxUserRegistrations);
            throw exception;
        }

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                "insert into users (chat_id, stake_addr, last_block_height, last_epoch_number) values (:chat_id, :stake_addr, :last_block_height, :last_epoch_number)",
                new MapSqlParameterSource(Map.of(
                        "chat_id", user.getChatId(),
                        "stake_addr", user.getAddress(),
                        "last_block_height", user.getLastBlockHeight(),
                        "last_epoch_number", user.getLastEpochNumber())), keyHolder, new String[]{"id"});

        LOG.debug("Inserted new user with key {}: {}", keyHolder.getKeyAs(Long.class), user);
    }

    public void updateUserBlockHeight(Long id, Integer blockHeight) {
        int updatedNumOfRows = namedParameterJdbcTemplate.update(
                "update users set last_block_height = :last_block_height where id = :id",
                new MapSqlParameterSource(Map.of(
                        "last_block_height", blockHeight,
                        "id", id)));

        if (updatedNumOfRows != 1) {
            LOG.error("Unexpected updated number of rows for the user with id " + id +
                    ", when updating the block height. This is a bug!");
        } else {
            LOG.debug("Updated block height to {} for user ID {}", blockHeight, id);
        }
    }

    public void updateUserEpochNumber(Long id, Integer epochNumber) {
        int updatedNumOfRows = namedParameterJdbcTemplate.update(
                "update users set last_epoch_number = :last_epoch_number where id = :id",
                new MapSqlParameterSource(Map.of(
                        "last_epoch_number", epochNumber,
                        "id", id)));

        if (updatedNumOfRows != 1) {
            LOG.error("Unexpected updated number of rows for the user with id " + id +
                    ", when updating the epoch number. This is a bug!");
        } else {
            LOG.debug("Updated user epoch number to {} for user ID {}", epochNumber, id);
        }
    }

    public boolean removeAddress(Long chatId, String addr) {
        int removedRows = this.namedParameterJdbcTemplate.update(
                "delete from users where chat_id = :chat_id and stake_addr = :stake_addr;",
                Map.of("chat_id", chatId,
                        "stake_addr", addr));

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
                "select id, chat_id, stake_addr, last_block_height, last_epoch_number from users where id = :id",
                Map.of("id", id));

        if (!rs.next()) {
            LOG.warn("Cannot find the user with ID {}", id);
            throw new UserNotFoundException("Cannot find the user with ID " + id);
        }

        User u = new User();
        u.setId(rs.getLong("id"));
        u.setChatId(rs.getLong("chat_id"));
        u.setAddress(rs.getString("stake_addr"));
        u.setLastBlockHeight(rs.getInt("last_block_height"));
        u.setLastEpochNumber(rs.getInt("last_epoch_number"));

        return u;
    }
}
