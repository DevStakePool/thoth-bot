package com.devpool.thothBot;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.telegram.command.*;
import com.devpool.thothBot.util.TelegramUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ForceReply;
import com.pengrad.telegrambot.request.SendMessage;
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
        TEST_USERS.add(new User(-3L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", 0, 0));
    }

    @MockBean
    private TelegramFacade telegramFacadeMock;

    @MockBean
    private TelegramBot telegramBotMock;

    @MockBean
    private KoiosFacade koiosFacade;

    @Captor
    private ArgumentCaptor<String> messageArgCaptor;

    @Captor
    private ArgumentCaptor<Long> chatIdArgCaptor;

    @Captor
    private ArgumentCaptor<SendMessage> sendMessageArgCaptor;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BackendServiceDouble backendServiceDouble;

    // Commands
    @Autowired
    private HelpCmd helpCmd;

    @Autowired
    private AccountInfoCmd infoCmd;

    @Autowired
    private SubscribeCmd subscribeCmd;

    @Autowired
    private UnsubscribeCmd unsubscribeCmd;

    @Autowired
    private StakeAddressCmd stakeCmd;

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
    public void userCommandHelpTest() throws Exception {
        // Testing Help command
        Update helpCmdUpdate = TelegramUtils.buildHelpCommandUpdate(false);
        Update startCmdUpdate = TelegramUtils.buildHelpCommandUpdate(true);
        this.helpCmd.execute(helpCmdUpdate, this.telegramBotMock);
        this.helpCmd.execute(startCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(2, sendMessages.size());
        for (SendMessage sendMessage : sendMessages) {
            LOG.debug("Message params: {}", sendMessage.getParameters());
            Map<String, Object> params = sendMessage.getParameters();
            Assertions.assertEquals(1683539744L, params.get("chat_id"));
            Assertions.assertEquals(Boolean.TRUE, params.get("disable_web_page_preview"));
            Assertions.assertEquals("HTML", params.get("parse_mode"));
            Assertions.assertTrue(params.get("text").toString().contains("THOTH BOT"));
            Assertions.assertTrue(params.get("text").toString().contains("/help or /start"));
        }
    }

    @Test
    public void userCommandInfoTest() throws Exception {
        // Testing Help command
        Update infoCmdUpdate = TelegramUtils.buildInfoCommandUpdate();
        this.infoCmd.execute(infoCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        Assertions.assertEquals((long) -2, params.get("chat_id"));
        Assertions.assertEquals(Boolean.TRUE, params.get("disable_web_page_preview"));
        Assertions.assertEquals("HTML", params.get("parse_mode"));
        Assertions.assertTrue(params.get("text").toString().contains("[DEV]"));
        Assertions.assertTrue(params.get("text").toString().contains("[SPKL]"));
        Assertions.assertTrue(params.get("text").toString().contains("Status: registered"));
        Assertions.assertTrue(params.get("text").toString().contains("Rewards: 5,569.97"));
        Assertions.assertTrue(params.get("text").toString().contains("Total Balance: 3,013.78"));
    }

    @Test
    public void userCommandSubscribeTest() throws Exception {
        // Testing Help command
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate();
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        Assertions.assertEquals(-1000L, params.get("chat_id"));
        Assertions.assertEquals(ForceReply.class, params.get("reply_markup").getClass());
        Assertions.assertTrue(params.get("text").toString().contains("Hi Alessio, please send your stake address"));
    }

    @Test
    public void userCommandUnsubscribeTest() throws Exception {
        // Testing Help command
        Update unsubscribeCmdUpdate = TelegramUtils.buildUnsubscribeCommandUpdate();
        this.unsubscribeCmd.execute(unsubscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        Assertions.assertEquals(-1000L, params.get("chat_id"));
        Assertions.assertEquals(ForceReply.class, params.get("reply_markup").getClass());
        Assertions.assertTrue(params.get("text").toString().contains("Hi Alessio, please specify your stake address"));
    }

    @Test
    public void scheduledNotificationsTest() throws Exception {
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(8))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<User> allUsers = this.userDao.getUsers();
        List<Long> allChatIds = allUsers.stream().map(u -> u.getChatId()).sorted().collect(Collectors.toList());
        LOG.info("UserChatIDs={}", allChatIds);
        LOG.info("ChatIDs={}", this.chatIdArgCaptor.getAllValues().stream().sorted().collect(Collectors.toList()));

        int accountsTransactionsChecked = 0;
        int accountsRewardsChecked = 0;
        for (String msg : this.messageArgCaptor.getAllValues()) {
            LOG.debug("Message\n{}", msg);
            if (msg.contains("stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32")) {
                if (msg.contains("reward(s)")) {
                    Assertions.assertTrue(msg.contains("Catalyst Voting"));
                    Assertions.assertTrue(msg.contains("Epoch 341"));
                    Assertions.assertTrue(msg.contains("146.34"));
                    accountsRewardsChecked++;
                } else if (msg.contains("transaction(s)")) {
                    Assertions.assertTrue(msg.contains("Fee 0.17"));
                    Assertions.assertTrue(msg.contains("Input 1,221.32"));
                    Assertions.assertTrue(msg.contains("MIN 245.82"));
                    Assertions.assertTrue(msg.contains("thoth-bot 1"));
                    accountsTransactionsChecked++;
                } else {
                    Assertions.fail("Unknown message " + msg);
                }
            } else if (msg.contains("stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy")) {
                if (msg.contains("reward(s)")) {
                    Assertions.assertTrue(msg.contains("Catalyst Voting"));
                    Assertions.assertTrue(msg.contains("Epoch 341"));
                    Assertions.assertTrue(msg.contains("93.42"));
                    accountsRewardsChecked++;
                } else if (msg.contains("transaction(s)")) {
                    Assertions.assertTrue(msg.contains("Fee 0.58"));
                    Assertions.assertTrue(msg.contains("Input 1.38"));
                    Assertions.assertTrue(msg.contains("Output -1.35"));
                    Assertions.assertTrue(msg.contains("CashewF 373.00"));
                    Assertions.assertTrue(msg.contains("Output -4,200.18"));
                    accountsTransactionsChecked++;
                } else {
                    Assertions.fail("Unknown message " + msg);
                }
            } else if (msg.contains("stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr")) {
                if (msg.contains("reward(s)")) {
                    Assertions.assertTrue(msg.contains("Staking Rewards"));
                    Assertions.assertTrue(msg.contains("Epoch 341"));
                    Assertions.assertTrue(msg.contains("8.61"));
                    accountsRewardsChecked++;
                } else if (msg.contains("transaction(s)")) {
                    Assertions.assertTrue(msg.contains("2 new transaction(s)"));
                    Assertions.assertTrue(msg.contains("Output -9,872.71"));
                    Assertions.assertTrue(msg.contains("CULO 100,000"));
                    accountsTransactionsChecked++;
                } else {
                    Assertions.fail("Unknown message " + msg);
                }
            } else if (msg.contains("stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz")) {
                if (msg.contains("reward(s)")) {
                    Assertions.assertTrue(msg.contains("Staking Rewards"));
                    Assertions.assertTrue(msg.contains("Epoch 341"));
                    Assertions.assertTrue(msg.contains("1.18"));
                    Assertions.assertTrue(msg.contains("[DYNO]"));
                    accountsRewardsChecked++;
                } else if (msg.contains("transaction(s)")) {
                    Assertions.assertTrue(msg.contains("4 new transaction(s)"));
                    Assertions.assertTrue(msg.contains("hvMIN 245,820,436.00"));
                    Assertions.assertTrue(msg.contains("1612572528 1"));
                    accountsTransactionsChecked++;
                } else {
                    Assertions.fail("Unknown message " + msg);
                }
            } else {
                Assertions.fail("Unknown message " + msg);
            }
        }
        Assertions.assertEquals(4, accountsTransactionsChecked);
        Assertions.assertEquals(4, accountsRewardsChecked);
    }
}
