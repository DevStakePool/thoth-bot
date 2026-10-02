package com.devpool.thothBot;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.AbstractIntegrationTest;
import com.pengrad.telegrambot.TelegramBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext
class UserActivationIntegrationTest extends AbstractIntegrationTest {
    private static final long CHAT_A = -101L;
    private static final long CHAT_B = -102L;
    private static final String STAKE_1 = "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr";
    private static final String STAKE_2 = "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32";

    @MockitoBean
    private TelegramFacade telegramFacadeMock;

    @MockitoBean
    private TelegramBot telegramBotMock;

    @MockitoBean
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void beforeEach() {
        jdbcTemplate.update("DELETE FROM users");
        userDao.addNewUser(new User(CHAT_A, STAKE_1, 10, 5, 100L, 100L));
        userDao.addNewUser(new User(CHAT_A, STAKE_2, 10, 5, 100L, 100L));
        userDao.addNewUser(new User(CHAT_B, STAKE_1, 10, 5, 100L, 100L));
    }

    @Test
    void deactivatedChatIsExcludedEverywhere() {
        assertEquals(2, userDao.deactivateChat(CHAT_A));

        assertEquals(1, userDao.getUsers().size());
        assertEquals(CHAT_B, userDao.getUsers().getFirst().getChatId());
        assertEquals(1, userDao.countSubscriptions());
        assertEquals(1, userDao.countUniqueUsers());
        assertEquals(1, userDao.countInactiveUsers());
        assertTrue(userDao.hasInactiveSubscriptions(CHAT_A));
        assertFalse(userDao.hasInactiveSubscriptions(CHAT_B));

        // Idempotent
        assertEquals(0, userDao.deactivateChat(CHAT_A));
    }

    @Test
    void reactivateRestoresSubscriptionsAndFastForwardsCheckpoints() {
        userDao.deactivateChat(CHAT_A);

        assertEquals(2, userDao.reactivateChat(CHAT_A, 5000, 400, 9999L));

        var users = userDao.getUsers();
        assertEquals(3, users.size());
        users.stream().filter(u -> u.getChatId() == CHAT_A).forEach(u -> {
            assertEquals(5000, u.getLastBlockHeight());
            assertEquals(400, u.getLastEpochNumber());
            assertEquals(9999L, u.getLastGovVotesBlockTime());
            assertEquals(9999L, u.getLastGovActionBlockTime());
        });
        // The other chat is untouched
        users.stream().filter(u -> u.getChatId() == CHAT_B).forEach(u -> {
            assertEquals(10, u.getLastBlockHeight());
            assertEquals(100L, u.getLastGovVotesBlockTime());
        });
        assertFalse(userDao.hasInactiveSubscriptions(CHAT_A));
        assertEquals(0, userDao.countInactiveUsers());
    }

    @Test
    void reactivateWithoutTipKeepsBlockHeightAndEpoch() {
        userDao.deactivateChat(CHAT_A);

        assertEquals(2, userDao.reactivateChat(CHAT_A, null, null, 9999L));

        userDao.getUsers().stream().filter(u -> u.getChatId() == CHAT_A).forEach(u -> {
            assertEquals(10, u.getLastBlockHeight());
            assertEquals(5, u.getLastEpochNumber());
            assertEquals(9999L, u.getLastGovVotesBlockTime());
        });
    }

    @Test
    void reactivateDoesNothingForActiveChat() {
        assertEquals(0, userDao.reactivateChat(CHAT_B, 5000, 400, 9999L));
        assertEquals(10, userDao.getUsers().stream().filter(u -> u.getChatId() == CHAT_B).findFirst().orElseThrow().getLastBlockHeight());
    }
}
