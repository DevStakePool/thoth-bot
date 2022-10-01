package com.devpool.thothBot.dao;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.MaxRegistrationsExceededException;
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
import java.util.Collection;
import java.util.Map;

@Repository
public class UserDao {
    private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${thoth.max.user.registrations:5}")
    private Integer maxUserRegistrations;

    @PostConstruct
    public void post() {
        LOG.info("User DAO initialised");
    }

    public Collection<User> getUsers() {
        SqlRowSet rs = this.jdbcTemplate.queryForRowSet(
                "select id, chat_id, stake_addr, last_block_height from users");
        Map<Long, User> users = new HashedMap<>();
        while (rs.next()) {
            Long userId = rs.getLong("id");
            User u = users.get(userId);
            if (u == null) {
                u = new User();
                u.setId(rs.getLong("id"));
                u.setChatId(rs.getLong("chat_id"));
                u.setStakeAddr(rs.getString("stake_addr"));
                u.setLastBlockHeight(rs.getInt("last_block_height"));
                users.put(userId, u);
            }
        }
        return users.values();
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
                "insert into users (chat_id, stake_addr, last_block_height) values (:chat_id, :stake_addr, :last_block_height)",
                new MapSqlParameterSource(Map.of(
                        "chat_id", user.getChatId(),
                        "stake_addr", user.getStakeAddr(),
                        "last_block_height", user.getLastBlockHeight())), keyHolder, new String[]{"id"});

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

    public boolean removeStakeAddress(Long chatId, String stakeAddr) {
        int removedRows = this.namedParameterJdbcTemplate.update(
                "delete from users where chat_id = :chat_id and stake_addr = :stake_addr;",
                Map.of("chat_id", chatId,
                        "stake_addr", stakeAddr));

        if (removedRows > 1)
            LOG.error("Unexpected deletion of stake address {} for chat-id {}. The expected removed rows was 1 but got {}",
                    stakeAddr, chatId, removedRows);

        if (removedRows == 0)
            LOG.warn("Cannot remove the stake address {} with chat-id {}. Entry not found", stakeAddr, chatId);

        return removedRows == 1;
    }
}
