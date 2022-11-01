package com.devpool.thothBot;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootTest
public class IntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTest.class);
    private static List<User> TEST_USERS = new ArrayList<>();

    static {
        TEST_USERS.add(new User(-1L, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", 0, 0));
        TEST_USERS.add(new User(-2L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", 0, 0));
        TEST_USERS.add(new User(-2L, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz", 0, 0));
    }

    @MockBean
    private TelegramFacade telegramFacadeMock;

    @MockBean
    private KoiosFacade koiosFacade;

    @Captor
    private ArgumentCaptor<String> messageArgCaptor;

    @Captor
    private ArgumentCaptor<Long> chatIdArgCaptor;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BackendServiceDouble backendServiceDouble;

    @BeforeEach
    public void beforeEach() throws Exception {
        this.backendServiceDouble = new BackendServiceDouble();

        // Purge data
        int affectedRows = jdbcTemplate.update("DELETE FROM users");
        LOG.info("Deleted {} rows in table users", affectedRows);

        affectedRows = jdbcTemplate.update("DELETE FROM assets");
        LOG.info("Deleted {} rows in table assets", affectedRows);

        // Add test data
        for (User testUser : TEST_USERS) {
            LOG.debug("Adding user {}", testUser);
            this.userDao.addNewUser(testUser);
        }

        Mockito.when(this.koiosFacade.getKoiosService()).thenReturn(this.backendServiceDouble);
    }

    @Test
    public void scheduledNotificationsTest() throws Exception {
        Mockito.verify(this.telegramFacadeMock, Mockito.timeout(70 * 1000).times(6)).sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<User> allUsers = this.userDao.getUsers();
        List<Long> allChatIds = allUsers.stream().map(u -> u.getChatId()).sorted().collect(Collectors.toList());
        LOG.info("UserChatIDs={}", allChatIds);
        LOG.info("ChatIDs={}", this.chatIdArgCaptor.getAllValues().stream().sorted().collect(Collectors.toList()));

        for (String msg : this.messageArgCaptor.getAllValues()) {
            LOG.debug("Message\n{}", msg);
            if (msg.contains("stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr")) {
                // Verify account 1
                if (msg.contains("reward(s)")) {
                    Assertions.assertTrue(msg.contains("Staking Rewards"));
                    Assertions.assertTrue(msg.contains("Epoch 341"));
                    Assertions.assertTrue(msg.contains("8.61"));
                } else if (msg.contains("transaction(s)")) {
                    Assertions.assertTrue(msg.contains("Fee 0.23"));
                    Assertions.assertTrue(msg.contains("Input 1.86"));
                } else {
                    Assertions.fail("Unknown message " + msg);
                }
            } else if (msg.contains("stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32")) {
                // Verify account 2
                if (msg.contains("reward(s)")) {
                    Assertions.assertTrue(msg.contains("Catalyst Voting"));
                    Assertions.assertTrue(msg.contains("Epoch 341"));
                    Assertions.assertTrue(msg.contains("146.34"));
                } else if (msg.contains("transaction(s)")) {
                    Assertions.assertTrue(msg.contains("Fee 0.47"));
                    Assertions.assertTrue(msg.contains("Input 2.03"));
                    Assertions.assertTrue(msg.contains("Output -2.38"));
                    Assertions.assertTrue(msg.contains("AncerstorEggFB 1"));
                    Assertions.assertTrue(msg.contains("Empowa 20.00"));
                } else {
                    Assertions.fail("Unknown message " + msg);
                }
            } else if (msg.contains("stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz")) {
                // Verify account 3
                if (msg.contains("reward(s)")) {
                    Assertions.assertTrue(msg.contains("Staking Rewards 1.18"));
                    Assertions.assertTrue(msg.contains("Epoch 341"));
                    Assertions.assertTrue(msg.contains("[DYNO]"));
                } else if (msg.contains("transaction(s)")) {
                    Assertions.assertTrue(msg.contains("3 new transaction(s)"));
                    Assertions.assertTrue(msg.contains("Output -2.23"));
                    Assertions.assertTrue(msg.contains("MIN 245.82"));
                } else {
                    Assertions.fail("Unknown message " + msg);
                }
            } else {
                Assertions.fail("Unknown message " + msg);
            }
        }
    }
}
