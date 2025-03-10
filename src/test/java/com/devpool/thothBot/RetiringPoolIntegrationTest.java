package com.devpool.thothBot;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.scheduler.RetiredPoolCheckerTask;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.AbstractIntegrationTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;

@SpringBootTest(properties = "thoth.scheduler.initial-delay-secs=999")
@DirtiesContext
class RetiringPoolIntegrationTest extends AbstractIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(RetiringPoolIntegrationTest.class);
    private static final List<User> TEST_USERS = new ArrayList<>();

    static {
        TEST_USERS.add(new User(-1L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", Integer.MAX_VALUE, 9999, Long.MAX_VALUE, Long.MAX_VALUE));
        TEST_USERS.add(new User(-1L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", Integer.MAX_VALUE, 9999, Long.MAX_VALUE, Long.MAX_VALUE));
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
    private RetiredPoolCheckerTask retiredPoolCheckerTask;

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
    void scheduledNotificationsRetiringPoolsTest() throws Exception {
        this.retiredPoolCheckerTask.run();
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(1))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<User> allUsers = this.userDao.getUsers();
        List<Long> allChatIds = allUsers.stream().map(User::getChatId).sorted().collect(Collectors.toList());
        LOG.info("UserChatIDs={}", allChatIds);
        LOG.info("ChatIDs={}", this.chatIdArgCaptor.getAllValues().stream().sorted().collect(Collectors.toList()));

        List<String> allMessages = messageArgCaptor.getAllValues();
        var message = retrieveMessageByString(allMessages, "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv",
                "retiring");
        assertTrue(message.contains("[DEV]"));
        assertTrue(message.contains("retiring in epoch 999"));

        message = retrieveMessageByString(allMessages, "pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd",
                "retired");
        assertTrue(message.contains("[APEX]"));
        assertTrue(message.contains("retired since epoch 666"));

        // check for null handles
        for (String m : allMessages) {
            assertFalse(m.contains("null"), "message contains 'null': " + m);
        }
    }

    @Test
    void scheduledNotificationsRetiringPoolsLastNotificationTest() throws Exception {
        var affectedRows = jdbcTemplate.update("INSERT INTO public.retiring_pools(chat_id, pool_id, remaining_notifications)\n" +
                "\tVALUES (-1, 'pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv', 1)");
        assertEquals(1, affectedRows);

        this.retiredPoolCheckerTask.run();

        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(1))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<User> allUsers = this.userDao.getUsers();
        List<Long> allChatIds = allUsers.stream().map(User::getChatId).sorted().collect(Collectors.toList());
        LOG.info("UserChatIDs={}", allChatIds);
        LOG.info("ChatIDs={}", this.chatIdArgCaptor.getAllValues().stream().sorted().collect(Collectors.toList()));

        List<String> allMessages = messageArgCaptor.getAllValues();
        var message = retrieveMessageByString(allMessages, "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv",
                "retiring");
        assertTrue(message.contains("[DEV]"));
        assertTrue(message.contains("retiring in epoch 999"));
        assertTrue(message.contains("This is the last WARNING"));

        message = retrieveMessageByString(allMessages, "pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd",
                "retired");
        assertTrue(message.contains("[APEX]"));
        assertTrue(message.contains("retired since epoch 666"));

        // check for null handles
        for (String m : allMessages) {
            assertFalse(m.contains("null"), "message contains 'null': " + m);
        }
    }

    @Test
    void scheduledNotificationsRetiringPoolsExpiredNotificationTest() throws Exception {
        var affectedRows = jdbcTemplate.update("INSERT INTO public.retiring_pools(chat_id, pool_id, remaining_notifications)\n" +
                "\tVALUES (-1, 'pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv', 0)");
        assertEquals(1, affectedRows);

        this.retiredPoolCheckerTask.run();

        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(1))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<User> allUsers = this.userDao.getUsers();
        List<Long> allChatIds = allUsers.stream().map(User::getChatId).sorted().collect(Collectors.toList());
        LOG.info("UserChatIDs={}", allChatIds);
        LOG.info("ChatIDs={}", this.chatIdArgCaptor.getAllValues().stream().sorted().collect(Collectors.toList()));

        List<String> allMessages = messageArgCaptor.getAllValues();
        var message = allMessages.stream().findFirst().orElseThrow();
        assertFalse(message.contains("[DEV]"));
        assertFalse(message.contains("retiring in epoch 999"));

        message = retrieveMessageByString(allMessages, "pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd",
                "retired");
        assertTrue(message.contains("[APEX]"));
        assertTrue(message.contains("retired since epoch 666"));

        // check for null handles
        for (String m : allMessages) {
            assertFalse(m.contains("null"), "message contains 'null': " + m);
        }
    }

    @Test
    void scheduledNotificationsRetiringPoolsNoNotificationTest() throws Exception {
        var affectedRows = jdbcTemplate.update("INSERT INTO public.retiring_pools(chat_id, pool_id, remaining_notifications)\n" +
                "\tVALUES (-1, 'pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv', 0)");
        assertEquals(1, affectedRows);

        affectedRows = jdbcTemplate.update("INSERT INTO public.retiring_pools(chat_id, pool_id, remaining_notifications)\n" +
                "\tVALUES (-1, 'pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd', 0)");
        assertEquals(1, affectedRows);

        this.retiredPoolCheckerTask.run();
        Thread.sleep(1000);
        Mockito.verify(this.telegramFacadeMock, never())
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());
    }

    private String retrieveMessageByString(List<String> messages, String filter1, String filter2) {
        Objects.requireNonNull(messages);
        Objects.requireNonNull(filter1);
        List<String> matching;
        if (filter2 == null)
            matching = messages.stream().filter(m -> m.contains(filter1)).collect(Collectors.toList());
        else
            matching = messages.stream().filter(m -> m.contains(filter1) && m.contains(filter2)).collect(Collectors.toList());

        Assertions.assertEquals(1, matching.size(),
                String.format("Expected only 1 match, using filter1=%s and filter2=%s, but got %d", filter1, filter2, matching.size()));

        return matching.get(0);
    }
}
