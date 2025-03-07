package com.devpool.thothBot;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.scheduler.GovernanceNewProposalsTask;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.AbstractIntegrationTest;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.response.SendResponse;
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
class GovNewProposalsIntegrationTest extends AbstractIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(GovNewProposalsIntegrationTest.class);
    private static final List<User> TEST_USERS = new ArrayList<>();

    static {
        TEST_USERS.add(new User(-2L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE, 0L));
        TEST_USERS.add(new User(-3L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE, 0L));
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
    private GovernanceNewProposalsTask governanceNewProposalsTask;

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
    void scheduledNotificationsNewGovActionsIdempotentTest() {
        // Running it multiple times
        this.governanceNewProposalsTask.run();
        this.governanceNewProposalsTask.run();
        this.governanceNewProposalsTask.run();

        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(20))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());
        List<String> allMessages = messageArgCaptor.getAllValues();
        assertEquals(20, allMessages.size());
        LOG.debug("All Messages: \n{}", allMessages);
        var messages = retrieveMessageByString(allMessages, "gov_action1pvv5wmjqhwa4u85vu9f4ydmzu2mgt8n7et967ph2urhx53r70xusqnmm525",null);
        assertEquals(2, messages.size());
        for (String m : messages) {
            assertTrue(m.contains("HardForkInitiation"));
            assertTrue(m.contains("epoch 536"));
            assertTrue(m.contains("Hard Fork to Protocol Version 10"));
            assertTrue(m.contains("The Cardano mainnet protocol will be upgraded to Major Version 10 and Minor Version 0"));
            assertFalse(m.contains("Authors"));
        }

        messages = retrieveMessageByString(allMessages, "gov_action1js2s9v92zpxg2rge0y3jt9zy626he2m67x9kx9phw4r942kvsn6sqfym0d7",null);
        assertEquals(2, messages.size());
        for (String m : messages) {
            assertTrue(m.contains("ParameterChange"));
            assertTrue(m.contains("epoch 546"));
            assertTrue(m.contains("Decrease Treasury Tax from 20% to 10%"));
            assertTrue(m.contains("This governance proposal seeks to reduce the treasury cut from 20% to 10%"));
            assertFalse(m.contains("Authors"));
        }

        messages = retrieveMessageByString(allMessages, "gov_action1286ft23r7jem825s4l0y5rn8sgam0tz2ce04l7a38qmnhp3l9a6qqn850dw",null);
        assertEquals(2, messages.size());
        for (String m : messages) {
            assertTrue(m.contains("qqn850dw"));
            assertTrue(m.contains("Abstract not found"));
        }

        messages = retrieveMessageByString(allMessages, "gov_action1llcd7ezdx299xeep9azm4dvsvz7783qfrhykcu3sv2ykl4sewv2qq4myfpk",null);
        assertEquals(2, messages.size());
        for (String m : messages) {
            assertTrue(m.contains("Rename the Chang 2 Hard Fork to the Plomin Hard Fork"));
            assertTrue(m.contains("Authors Adam Dean, Adam Rusch"));
        }

        // check for null handles
        for (String m : allMessages) {
            assertFalse(m.contains("null"), "message contains 'null': " + m);
        }
    }

    private List<String> retrieveMessageByString(List<String> messages, String filter1, String filter2) {
        Objects.requireNonNull(messages);
        Objects.requireNonNull(filter1);
        List<String> matching;
        if (filter2 == null)
            matching = messages.stream().filter(m -> m.contains(filter1)).toList();
        else
            matching = messages.stream().filter(m -> m.contains(filter1) && m.contains(filter2)).toList();

        return matching;
    }
}
