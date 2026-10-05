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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When the admin user ID is configured the bot is private: the subscriptions of the other chats are ignored.
 */
@SpringBootTest(properties = "thoth.admin.user-id=-201")
@DirtiesContext
class AdminOnlyUsersIntegrationTest extends AbstractIntegrationTest {
    private static final long ADMIN_CHAT = -201L;
    private static final long OTHER_CHAT = -202L;

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
        userDao.addNewUser(new User(ADMIN_CHAT, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", 10, 5, 100L, 100L));
        userDao.addNewUser(new User(ADMIN_CHAT, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", 10, 5, 100L, 100L));
        userDao.addNewUser(new User(OTHER_CHAT, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", 10, 5, 100L, 100L));
    }

    @Test
    void onlyTheAdminSubscriptionsAreProcessed() {
        var users = userDao.getUsers();

        assertEquals(2, users.size());
        users.forEach(u -> assertEquals(ADMIN_CHAT, u.getChatId()));
        assertEquals(2, userDao.countSubscriptions());
        assertEquals(1, userDao.countUniqueUsers());
    }
}
