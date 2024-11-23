package com.devpool.thothBot;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.pengrad.telegrambot.TelegramBot;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("subscription")
public class IntegrationSubscriptionSchedulerTest {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationSubscriptionSchedulerTest.class);
    private static List<User> TEST_USERS = new ArrayList<>();

    static {
        /*
            stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr 2 tokens + 1 THOTH    DEV
            stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32 1 token  + 0 THOTH    DEV
            stake1uyc8nhmxhnzsyc2s2kwdd2gy9k00ky0qakv58v5fusuve9sgealu4 0 tokens + 2 THOTH-F  Not-Reg
            stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy 0 tokens + 0 THOTH    POOLXYZ
            stake1uyrx65wjqjgeeksd8hptmcgl5jfyrqkfq0xe8xlp367kphsckq250 0 tokens + 0 THOTH    POOLABC
            stake1uxh7lse77csz5x7fs6hgd9uc4z9w056jgrgme28pv4n3czs495erv 0 tokens + 1 THOTH    POOL111
            stake1uxw8wq6ceame0jh0ccj60gfyp0dwcneg422ktuz3kcd3s3srtfm9u 1 token  + 0 THOTH    POOL999
            addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m 10 tokens + 3 THOTH-F
         */

        /*
         * User -1 follows 4 accounts. None staking with DEV. But he has only 1 THOTH NFT. 2 will be removed
         */
        TEST_USERS.add(new User(-1L, "stake1uxh7lse77csz5x7fs6hgd9uc4z9w056jgrgme28pv4n3czs495erv", 0, 0, 0L));
        TEST_USERS.add(new User(-1L, "stake1uyrx65wjqjgeeksd8hptmcgl5jfyrqkfq0xe8xlp367kphsckq250", 0, 0, 0L));
        TEST_USERS.add(new User(-1L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", 0, 0, 0L));
        TEST_USERS.add(new User(-1L, "stake1uxw8wq6ceame0jh0ccj60gfyp0dwcneg422ktuz3kcd3s3srtfm9u", 0, 0, 0L));

        /*
         * User -2 follows 1 account staking with DEV and 1 non-staking with DEV. No THOTH NFTs. All good
         */
        TEST_USERS.add(new User(-2L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", 0, 0, 0L));
        TEST_USERS.add(new User(-2L, "stake1uyrx65wjqjgeeksd8hptmcgl5jfyrqkfq0xe8xlp367kphsckq250", 0, 0, 0L));

        /*
         * User -3 has plenty of NFTs, so enough to monitor 1 address + 2 accounts not staking with DEV. All good
         */
        TEST_USERS.add(new User(-3L, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", 0, 0, 0L));
        TEST_USERS.add(new User(-3L, "stake1uyrx65wjqjgeeksd8hptmcgl5jfyrqkfq0xe8xlp367kphsckq250", 0, 0, 0L));
        TEST_USERS.add(new User(-3L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", 0, 0, 0L));

        /*
         * User -4 is registered to 2 accounts both NOT staking to DEV, and it has no THOTH NFTs => 1 will be removed
         */
        TEST_USERS.add(new User(-4L, "stake1uyrx65wjqjgeeksd8hptmcgl5jfyrqkfq0xe8xlp367kphsckq250", 0, 0, 0L));
        TEST_USERS.add(new User(-4L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", 0, 0, 0L));
    }

    @MockBean
    private TelegramFacade telegramFacadeMock;

    @MockBean
    private TelegramBot telegramBotMock;

    @MockBean
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<String> messageArgCaptor;

    @Captor
    private ArgumentCaptor<Long> chatIdArgCaptor;

    private BackendServiceDouble backendServiceDouble;

    @BeforeEach
    public void beforeEach() throws Exception {
        this.backendServiceDouble = new BackendServiceDouble(BackendServiceDouble.BackendBehavior.SUBSCRIPTION_SCHEDULER_SCENARIO);

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
    public void testSubscriptionManagerScheduler() throws Exception {
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(2))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<String> allMessages = this.messageArgCaptor.getAllValues();
        List<Long> allChatIds = this.chatIdArgCaptor.getAllValues();
        LOG.info("all messages {}, all chat-ids {}", allMessages, allChatIds);

        Assertions.assertEquals(2, allMessages.size());

        // User -1 will get 2 subscription removed, while user -4 will get 1 removed
        int foundChatIds = 2;
        for (int i = 0; i < allChatIds.size(); i++) {
            Long chatId = allChatIds.get(i);
            String message = allMessages.get(i);
            Assertions.assertTrue(message.contains("you are missing Thoth NFTs"));

            if (chatId == -1L) {
                foundChatIds--;
                Assertions.assertFalse(message.contains("stake1uxh7lse77csz5x7fs6hgd9uc4z9w056jgrgme28pv4n3czs495erv"));
                Assertions.assertEquals(3, message.split("stake1").length);
            } else if (chatId == -4L) {
                foundChatIds--;
                Assertions.assertEquals(2, message.split("stake1").length);

            } else {
                Assertions.fail("Unexpected chat ID: " + chatId + " with message: " + message);
            }
        }

        Assertions.assertEquals(0, foundChatIds, "Did not find the correct number of chatIDs");
    }

}