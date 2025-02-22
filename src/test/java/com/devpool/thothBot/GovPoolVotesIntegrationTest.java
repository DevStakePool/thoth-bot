package com.devpool.thothBot;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.scheduler.GovernanceSpoVotesCheckerTask;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.response.SendResponse;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("no-scheduler")
@DirtiesContext
class GovPoolVotesIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(GovPoolVotesIntegrationTest.class);
    private static final List<User> TEST_USERS = new ArrayList<>();

    static {
        TEST_USERS.add(new User(-2L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE));
        TEST_USERS.add(new User(-3L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE));
    }

    @MockitoBean
    private TelegramFacade telegramFacadeMock;

    @MockitoBean
    private TelegramBot telegramBotMock;

    @MockitoBean
    private KoiosFacade koiosFacade;

    @Captor
    private ArgumentCaptor<String> messageArgCaptor;

    @Captor
    private ArgumentCaptor<Long> chatIdArgCaptor;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BackendServiceDouble backendServiceDouble;

    @Autowired
    private GovernanceSpoVotesCheckerTask governanceSpoVotesCheckerTask;

    @BeforeEach
    public void beforeEach() throws Exception {
        this.backendServiceDouble = new BackendServiceDouble(BackendServiceDouble.BackendBehavior.SIMULATE_RETIRING_POOLS);

        // Purge data
        int affectedRows = jdbcTemplate.update("DELETE FROM users");
        LOG.info("Deleted {} rows in table users", affectedRows);

        affectedRows = jdbcTemplate.update("DELETE FROM assets");
        LOG.info("Deleted {} rows in table assets", affectedRows);

        affectedRows = jdbcTemplate.update("DELETE FROM retiring_pools");
        LOG.info("Deleted {} rows in table retiring_pools", affectedRows);

        affectedRows = jdbcTemplate.update("DELETE FROM pool_votes");
        LOG.info("Deleted {} rows in table pool_votes", affectedRows);


        // Add test data
        for (User testUser : TEST_USERS) {
            LOG.debug("Adding user {}", testUser);
            this.userDao.addNewUser(testUser);
        }

        Mockito.when(this.koiosFacade.getKoiosService()).thenReturn(this.backendServiceDouble);
    }

    @BeforeEach
    public void beforeAll() throws Exception {
        // Reset captors
        this.messageArgCaptor = ArgumentCaptor.forClass(String.class);
        this.chatIdArgCaptor = ArgumentCaptor.forClass(Long.class);

        SendResponse respMock = Mockito.mock(SendResponse.class);
        Mockito.when(respMock.isOk()).thenReturn(true);
        Mockito.when(this.telegramBotMock.execute(Mockito.any())).thenReturn(respMock);
    }

    @Test
    void scheduledNotificationsVotingPoolsIdempotentTest() {
        // Running it multiple times
        this.governanceSpoVotesCheckerTask.run();
        this.governanceSpoVotesCheckerTask.run();
        this.governanceSpoVotesCheckerTask.run();

        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(2))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());
        List<String> allMessages = messageArgCaptor.getAllValues();
        assertEquals(2, allMessages.size());

        var message = retrieveMessageByString(allMessages, "pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd",
                "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy");
        assertTrue(message.contains("[APEX]"));
        assertTrue(message.contains("stake1...yqhf9jhy"));
        assertTrue(message.contains("Yes"));
        assertTrue(message.contains("gov_action1pvv5wmjqhwa4u85vu9f4ydmzu2mgt8n7et967ph2urhx53r70xusqnmm525"));

        message = retrieveMessageByString(allMessages, "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv",
                "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32");
        assertTrue(message.contains("[DEV]"));
        assertTrue(message.contains("alessio.dev"));
        assertTrue(message.contains("Yes"));
        assertTrue(message.contains("gov_action1pvv5wmjqhwa4u85vu9f4ydmzu2mgt8n7et967ph2urhx53r70xusqnmm525"));

        // check for null handles
        for (String m : allMessages) {
            assertFalse(m.contains("null"), "message contains 'null': " + m);
        }
    }

    @Test
    void scheduledNotificationsVotingPoolsWithNewSubscriptionTest() {
        // Running it scheduler
        this.governanceSpoVotesCheckerTask.run();

        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(20 * 1000)
                                .times(2))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());
        List<String> allMessages = messageArgCaptor.getAllValues();
        assertEquals(2, allMessages.size());

        var message = retrieveMessageByString(allMessages, "pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd",
                "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy");
        assertTrue(message.contains("[APEX]"));
        assertTrue(message.contains("stake1...yqhf9jhy"));
        assertTrue(message.contains("Yes"));
        assertTrue(message.contains("gov_action1pvv5wmjqhwa4u85vu9f4ydmzu2mgt8n7et967ph2urhx53r70xusqnmm525"));

        message = retrieveMessageByString(allMessages, "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv",
                "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32");
        assertTrue(message.contains("[DEV]"));
        assertTrue(message.contains("alessio.dev"));
        assertTrue(message.contains("Yes"));
        assertTrue(message.contains("gov_action1pvv5wmjqhwa4u85vu9f4ydmzu2mgt8n7et967ph2urhx53r70xusqnmm525"));

        // New subscription
        var testUser = new User(-2L, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz", Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);

        LOG.debug("Adding new subscription {}", testUser);
        this.userDao.addNewUser(testUser);

        // Run scheduler again
        this.governanceSpoVotesCheckerTask.run();

        // we should still have 2 notifications
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(20 * 1000)
                                .times(2))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        // check for null handles
        for (String m : allMessages) {
            assertFalse(m.contains("null"), "message contains 'null': " + m);
        }
    }

    private String retrieveMessageByString(List<String> messages, String filter1, String filter2) {
        Objects.requireNonNull(messages);
        Objects.requireNonNull(filter1);
        List<String> matching;
        if (filter2 == null)
            matching = messages.stream().filter(m -> m.contains(filter1)).toList();
        else
            matching = messages.stream().filter(m -> m.contains(filter1) && m.contains(filter2)).toList();

        Assertions.assertEquals(1, matching.size(),
                String.format("Expected only 1 match, using filter1=%s and filter2=%s, but got %d", filter1, filter2, matching.size()));

        return matching.getFirst();
    }
}
