package com.devpool.thothBot;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.scheduler.TransactionCheckerTaskV2;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.telegram.command.*;
import com.devpool.thothBot.telegram.command.admin.AdminNotifyAllCmd;
import com.devpool.thothBot.util.AbstractIntegrationTest;
import com.devpool.thothBot.util.TelegramUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.vdurmont.emoji.EmojiParser;
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

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("no-scheduler")
@DirtiesContext
class IntegrationNoSchedulerTest extends AbstractIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationNoSchedulerTest.class);
    private static List<User> TEST_USERS = new ArrayList<>();

    static {
        TEST_USERS.add(new User(-1L, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", 0, 0, 0L));
        TEST_USERS.add(new User(-2L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", 0, 0, 0L));
        //TEST_USERS.add(new User(-2L, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz", 0, 0, 0L)); // Reserved for Thoth NFTs tests
        TEST_USERS.add(new User(-2L, "stake1uxj8rc5aa4xkaejwmvx4gskyje6c283v7a7l6dyz5q2qjmqyxuqx9", 0, 0, 0L)); // Not present
        TEST_USERS.add(new User(-2L, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", 0, 0, 0L));
        TEST_USERS.add(new User(-3L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", 0, 0, 0L));
        TEST_USERS.add(new User(-4L, "stake1uyc8nhmxhnzsyc2s2kwdd2gy9k00ky0qakv58v5fusuve9sgealu4", 0, 0, 0L));
        TEST_USERS.add(new User(-1000L, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", 0, 0, 0L));
        TEST_USERS.add(new User(-1000L, "addr1q9pzugshkxdrtcmnwppsevp6s5709j4n4ud6q7yhj5ra8e2crqz3h0a46kcklgdaa4dfhdmjhgzy64tam76dxg68t55s9ua0sz", 0, 0, 0L)); // Not present
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
    private UnsubscribeSelectionCmd unsubscribeSelectionCmd;

    @Autowired
    private AddressCmd addressCmd;

    @Autowired
    private AssetsCmd assetsCmd;

    @Autowired
    private EpochCmd epochCmd;

    @Autowired
    private AssetsListCmd assetsListCmd;

    @Autowired
    private RewardsCmd rewardsCmd;

    @Autowired
    private AccountRewardsCmd accountRewardsCmd;

    @Autowired
    private AdminNotifyAllCmd adminNotifyAllCmd;

    @Autowired
    private TransactionCheckerTaskV2 transactionCheckerTaskV2;

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
    public void userCommandAddrForSubscribeNominalTest() throws Exception {
        // Testing Address command
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz", -1000);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -1000, params.get("chat_id"));
        Assertions.assertTrue(params.get("text").toString().contains("Please specify the operation first: /subscribe or /unsubscribe"));

        // First specify the /subscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate();
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());

        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        assertEquals(3, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("From now on you will receive updates")).count());
    }

    @Test
    public void userCommandInfoStakeAddrEmptyDataTest() throws Exception {
        // Testing Info command
        Update infoCmdUpdate = TelegramUtils.buildInfoCommandUpdate("-2");
        this.infoCmd.execute(infoCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        assertEquals((long) -2, params.get("chat_id"));
        assertInstanceOf(LinkPreviewOptions.class, params.get("link_preview_options"));
        assertEquals(Boolean.TRUE, ((LinkPreviewOptions)params.get("link_preview_options")).isDisabled());
        assertEquals("HTML", params.get("parse_mode"));
        Assertions.assertTrue(params.get("text").toString().contains("[DEV]"));
        assertEquals(3, params.get("text").toString().split("stakekey/stake1").length - 1);
        Assertions.assertTrue(params.get("text").toString().contains("Data will be available soon"));
        Assertions.assertTrue(params.get("text").toString().contains("CardanoYoda")); // DRep with name
        Assertions.assertTrue(params.get("text").toString().contains("PRIDE"));
    }

    @Test
    public void userCommandEpochInfoTest() throws Exception {
        // Testing Info command
        Update epochCommandUpdate = TelegramUtils.buildEpochCommandUpdate("-2");
        this.epochCmd.execute(epochCommandUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -2, params.get("chat_id"));
        assertInstanceOf(LinkPreviewOptions.class, params.get("link_preview_options"));
        assertEquals(Boolean.TRUE, ((LinkPreviewOptions)params.get("link_preview_options")).isDisabled());        assertEquals("HTML", params.get("parse_mode"));
        Assertions.assertTrue(params.get("text").toString().contains("ADA price"));
        Assertions.assertTrue(params.get("text").toString().contains("TXs count: 161116"));
        Assertions.assertTrue(params.get("text").toString().contains("Epoch 522"));
        Assertions.assertTrue(params.get("text").toString().contains("Total stake: 22.3B"));
    }

    @Test
    public void userCommandInfoNormalAddrEmptyDataTest() throws Exception {
        // Testing Help command
        Update infoCmdUpdate = TelegramUtils.buildInfoCommandUpdate("-1000");
        this.infoCmd.execute(infoCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        assertEquals((long) -1000, params.get("chat_id"));
        assertInstanceOf(LinkPreviewOptions.class, params.get("link_preview_options"));
        assertEquals(Boolean.TRUE, ((LinkPreviewOptions) params.get("link_preview_options")).isDisabled());
        assertEquals("HTML", params.get("parse_mode"));
        assertEquals(2, params.get("text").toString().split("address/addr1").length - 1);
        Assertions.assertTrue(params.get("text").toString().contains("Data will be available soon"));
    }

    @Test
    public void userCommandAddrForSubscribeStakingWithDevNominalTest() throws Exception {
        // Testing Address command
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", -1000);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -1000, params.get("chat_id"));
        Assertions.assertTrue(params.get("text").toString().contains("Please specify the operation first: /subscribe or /unsubscribe"));

        // First specify the /subscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate();
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());

        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        assertEquals(3, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("From now on you will receive updates")).count());
    }

    @Test
    public void userCommandAddrForSubscribeDoubleSubscriptionNominalTest() throws Exception {
        // Testing Address command
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", -1000);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -1000, params.get("chat_id"));
        Assertions.assertTrue(params.get("text").toString().contains("Please specify the operation first: /subscribe or /unsubscribe"));

        // First specify the /subscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate();
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());

        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        assertEquals(3, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("From now on you will receive updates")).count());
    }

    @Test
    public void userCommandAddrForSubscribeNotEnoughThothNFTsTest() throws Exception {
        // Custom Koios backend double
        this.backendServiceDouble = new BackendServiceDouble(BackendServiceDouble.BackendBehavior.DISABLE_THOTH_NFT_FOR_ACCOUNTS);
        Mockito.when(this.koiosFacade.getKoiosService()).thenReturn(this.backendServiceDouble);

        // Testing Address command
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", -4);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -4, params.get("chat_id"));
        Assertions.assertTrue(params.get("text").toString().contains("Please specify the operation first: /subscribe or /unsubscribe"));

        // First specify the /subscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate("-4");
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());

        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        assertEquals(3, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Max number of subscriptions exceeded")).count());
    }

    @Test
    public void userCommandAddrForSubscribeStealingNFTsTest() throws Exception {
        // Testing Address command
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", -1);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -1, params.get("chat_id"));
        Assertions.assertTrue(params.get("text").toString().contains("Please specify the operation first: /subscribe or /unsubscribe"));

        // First specify the /subscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate("-1");
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());

        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        assertEquals(3, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("The address addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m that you are trying to subscribe to, contains Thoth NFTs but it is currently used by another Telegram user.")).count());
    }

    @Test
    public void userCommandAddrInvalidForSubscribeTest() throws Exception {
        // Testing Address command
        String addr = "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv50000";
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                addr, -1000);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -1000, params.get("chat_id"));
        Assertions.assertTrue(params.get("text").toString().contains("Please specify the operation first: /subscribe or /unsubscribe"));

        // First specify the /subscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate();
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());

        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        assertEquals(3, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .equals("The provided address \"" + addr + "\" does not exist on-chain or it is not valid")).count());
    }

    @Test
    public void userCommandBaseAddrInvalidForSubscribeTest() throws Exception {
        // Testing Address command
        String addr = "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8f0000";
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                addr, -1000);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals((long) -1000, params.get("chat_id"));
        Assertions.assertTrue(params.get("text").toString().contains("Please specify the operation first: /subscribe or /unsubscribe"));

        // First specify the /subscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update subscribeCmdUpdate = TelegramUtils.buildSubscribeCommandUpdate();
        this.subscribeCmd.execute(subscribeCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());

        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.addressCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        assertEquals(3, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .equals("The provided address \"" + addr + "\" does not exist on-chain or it is not valid")).count());
    }

    @Test
    public void userCommandAddrForUnsubscribeTest() throws Exception {
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", -1000);

        // First specify the /unsubscribe
        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update unsubscribeCommandUpdate = TelegramUtils.buildUnsubscribeCommandUpdate();
        this.unsubscribeCmd.execute(unsubscribeCommandUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(argumentCaptor.capture());
        var capturedMessages = argumentCaptor.getAllValues();
        assertEquals(1, capturedMessages.size());

        // Get reply markup with inline keyboard
        var keyboard = (InlineKeyboardMarkup) capturedMessages.get(0).getParameters().get("reply_markup");
        assertEquals(1, keyboard.inlineKeyboard().length); // 1 row
        var row = keyboard.inlineKeyboard()[0];
        assertEquals(2, row.length); // 2 columns
        var callbackData = row[0].callbackData();
        assertNotNull(callbackData);
        assertTrue(callbackData.startsWith(UnsubscribeSelectionCmd.CMD_PREFIX));

        Update unsubscribeSelectionCmdUpdate = TelegramUtils.buildCallbackCommandUpdate(callbackData);
        this.unsubscribeSelectionCmd.execute(unsubscribeSelectionCmdUpdate, this.telegramBotMock);

        // Reset
        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());
        List<SendMessage> sendMessages = argumentCaptor.getAllValues();

        assertEquals(2, sendMessages.size());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please select an account")).count());
        assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("You have successfully unsubscribed the address")).count());
    }

    @Test
    public void userCommandAssetsForStakeAddressTest() throws Exception {
        // Testing Assets command
        Update assetsCmdUpdate = TelegramUtils.buildAssetsCommandUpdate("-2");
        Message messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMock.messageId()).thenReturn((int) (System.currentTimeMillis() / 1000));
        SendResponse sendRespMock = Mockito.mock(SendResponse.class);
        Mockito.when(sendRespMock.message()).thenReturn(messageMock);
        Mockito.when(this.telegramBotMock.execute(Mockito.any())).thenReturn(sendRespMock);
        this.assetsCmd.execute(assetsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sentMessages = this.sendMessageArgCaptor.getAllValues();
        assertEquals(1, sentMessages.size());
        SendMessage message = sentMessages.get(0);

        Map<String, Object> params = message.getParameters();
        Assertions.assertTrue(params.containsKey("text"));
        Assertions.assertTrue(params.containsKey("reply_markup"));

        assertEquals("Please select an account", params.get("text"));
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) params.get("reply_markup");
        assertEquals(2, markup.inlineKeyboard().length);
        InlineKeyboardButton[] firstRow = markup.inlineKeyboard()[0];
        assertEquals(2, firstRow.length);
        Assertions.assertFalse(firstRow[0].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[0].text().isEmpty());
        Assertions.assertFalse(firstRow[1].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[1].text().isEmpty());

        // We take the first button, so we'll get the list of assets of the account.
        String callbackCmd = firstRow[1].callbackData();
        if (firstRow[0].text().contains("$alessio.dev"))
            callbackCmd = firstRow[0].callbackData();
        LOG.info("Getting assets for account {}", callbackCmd);

        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update detailsCmdUpdate = TelegramUtils.buildCallbackCommandUpdate(callbackCmd);
        this.assetsListCmd.execute(detailsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());
        sentMessages = argumentCaptor.getAllValues();
        assertEquals(2, sentMessages.size());

        assertEquals(1,
                sentMessages.stream().map(m -> m.getParameters()
                                .getOrDefault("link_preview_options", new LinkPreviewOptions().isDisabled(false)))
                        .map(LinkPreviewOptions.class::cast)
                        .filter(LinkPreviewOptions::isDisabled).count());

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Assets for address $")).count());

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("<a href=\"https://pool.pm/asset1wwyy88f8u937hz7kunlkss7gu446p6ed5gdfp6\">SingularityNet AGIX Token</a> 0.00")));

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("<a href=\"https://pool.pm/asset1y5ek5facnmt2h5wa8n9wrl7qxu49fv8rmz8yte\">Cardano Summit 2023 NFT 2066</a> 1")));

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("Shown 10/47")));

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("Page 1/5")));

        // get assets as reply markup
        Optional<SendMessage> markupAssetsMsg = sentMessages.stream().filter(m -> m.getParameters().get("text")
                .toString().contains("Assets for address")).findFirst();
        Assertions.assertTrue(markupAssetsMsg.isPresent());
        InlineKeyboardMarkup inlineKeyboardMarkup = (InlineKeyboardMarkup) markupAssetsMsg.get().getParameters().get("reply_markup");
        assertEquals(1, inlineKeyboardMarkup.inlineKeyboard().length);

        assertEquals(1,
                Arrays.stream(inlineKeyboardMarkup.inlineKeyboard()).filter(i -> i[0].text().contains("PREV")).count());
        assertEquals(1,
                Arrays.stream(inlineKeyboardMarkup.inlineKeyboard()).filter(i -> i[1].text().contains("NEXT")).count());

    }

    @Test
    public void userCommandAssetsForNormalAddressTest() throws Exception {
        // Testing Assets command
        Update assetsCmdUpdate = TelegramUtils.buildAssetsCommandUpdate("-1000");
        Message messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMock.messageId()).thenReturn((int) (System.currentTimeMillis() / 1000));
        SendResponse sendRespMock = Mockito.mock(SendResponse.class);
        Mockito.when(sendRespMock.message()).thenReturn(messageMock);
        Mockito.when(this.telegramBotMock.execute(Mockito.any())).thenReturn(sendRespMock);
        this.assetsCmd.execute(assetsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sentMessages = this.sendMessageArgCaptor.getAllValues();
        assertEquals(1, sentMessages.size());
        SendMessage message = sentMessages.get(0);

        Map<String, Object> params = message.getParameters();
        Assertions.assertTrue(params.containsKey("text"));
        Assertions.assertTrue(params.containsKey("reply_markup"));

        assertEquals("Please select an account", params.get("text"));
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) params.get("reply_markup");
        assertEquals(1, markup.inlineKeyboard().length);
        InlineKeyboardButton[] firstRow = markup.inlineKeyboard()[0];
        assertEquals(2, firstRow.length);
        Assertions.assertFalse(firstRow[0].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[0].text().isEmpty());

        // We take the first button, so we'll get the list of assets of the account.
        String callbackCmd = firstRow[0].callbackData();
        LOG.info("Getting assets for account {}", callbackCmd);

        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update detailsCmdUpdate = TelegramUtils.buildCallbackCommandUpdate(callbackCmd);
        this.assetsListCmd.execute(detailsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());
        sentMessages = argumentCaptor.getAllValues();
        assertEquals(2, sentMessages.size());

        assertEquals(1,
                sentMessages.stream().map(m -> m.getParameters()
                                .getOrDefault("link_preview_options", new LinkPreviewOptions().isDisabled(false)))
                        .map(LinkPreviewOptions.class::cast)
                        .filter(LinkPreviewOptions::isDisabled).count());

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Assets for address $")).count());

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("<a href=\"https://pool.pm/asset1l5rt8a4jylqzk0scds7cen8k9lnhvsp8yzss3j\">CaseyBlackRed0465</a> 1")));

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("<a href=\"https://pool.pm/asset1sdzme5cnwgqk6u94k0fnlymenvnvfv3jm78dcz\">CLAY</a> 47,500.00")));

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("Shown 10/114")));

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("Page 1/12")));

        // get assets as reply markup
        Optional<SendMessage> markupAssetsMsg = sentMessages.stream().filter(m -> m.getParameters().get("text")
                .toString().contains("Assets for address")).findFirst();
        Assertions.assertTrue(markupAssetsMsg.isPresent());
        InlineKeyboardMarkup inlineKeyboardMarkup = (InlineKeyboardMarkup) markupAssetsMsg.get().getParameters().get("reply_markup");
        assertEquals(1, inlineKeyboardMarkup.inlineKeyboard().length);

        assertEquals(1,
                Arrays.stream(inlineKeyboardMarkup.inlineKeyboard()).filter(i -> i[0].text().contains("PREV")).count());
        assertEquals(1,
                Arrays.stream(inlineKeyboardMarkup.inlineKeyboard()).filter(i -> i[1].text().contains("NEXT")).count());

        // Next page
        Optional<InlineKeyboardButton> nextPage = Arrays.stream(inlineKeyboardMarkup.inlineKeyboard()[0]).filter(b -> b.text().contains("NEXT")).findFirst();
        Assertions.assertTrue(nextPage.isPresent());
        String nextPageCallback = nextPage.get().callbackData();
        ArgumentCaptor<SendMessage> argumentCaptorNextPage = ArgumentCaptor.forClass(SendMessage.class);
        Update detailsCmdUpdateNextPage = TelegramUtils.buildCallbackCommandUpdate(nextPageCallback);
        this.assetsListCmd.execute(detailsCmdUpdateNextPage, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptorNextPage.capture());
        sentMessages = argumentCaptorNextPage.getAllValues();
        assertEquals(3, sentMessages.size());

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("Shown 20/114")));

        Assertions.assertTrue(sentMessages.stream().map(m -> m.getParameters().get("text").toString())
                .anyMatch(t -> t.contains("Page 2/12")));

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("<a href=\"https://pool.pm/asset1vl0h0ew4rn9szfedn8d750w4tl46ynjzyja5ra\">CaseyPurple0362</a> 1")).count());
    }

    @Test
    public void userCommandNotifyAllAsAdminErrorCaseInvalidFormatTest() throws Exception {
        // Testing Help command
        Update notifyAllCmdUpdate = TelegramUtils.buildNotifyAllCommandUpdate("test_admin", "");
        this.adminNotifyAllCmd.execute(notifyAllCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();
        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        Assertions.assertTrue(params.get("text").toString().contains("Invalid format."));
    }

    @Test
    public void userCommandNotifyAllAsAdminErrorCaseNotAuthorizedTest() throws Exception {
        Update notifyAllCmdUpdate = TelegramUtils.buildNotifyAllCommandUpdate("john_doe", " Hello!");
        this.adminNotifyAllCmd.execute(notifyAllCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();
        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        Assertions.assertTrue(params.get("text").toString().contains("NOT AUTHORIZED"));
    }

    @Test
    public void userCommandNotifyAllAsAdminNominalCaseTest() throws Exception {
        String msg = " Good day everyone! Long life to Cardano! " + EmojiParser.parseToUnicode(":smile:")
                + "\nMulti line is also supported!\nAlessio " + EmojiParser.parseToUnicode(":wave:");

        Update notifyAllCmdUpdate = TelegramUtils.buildNotifyAllCommandUpdate("test_admin", msg);
        this.adminNotifyAllCmd.execute(notifyAllCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(7))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();
        // 6 = 2 to the admin + 5 user accounts
        assertEquals(7, sendMessages.size());

        for (SendMessage sendMessage : sendMessages) {
            LOG.debug("Message to chat {}: {}", sendMessage.getParameters().get("chat_id"), sendMessage.getParameters().get("text"));
        }

        // Expected the following chat IDs:
        //     1683539744 -> ADMIN (this call) x2 msg
        //     -2, -1000, -1, -3
        List<SendMessage> adminResponses = sendMessages.stream().filter(
                sm -> (Long) sm.getParameters().get("chat_id") == 1683539744L).collect(Collectors.toList());
        assertEquals(2, adminResponses.size());
        assertEquals(1, adminResponses.stream().filter(
                r -> r.getParameters().get("text").toString().contains("Ok, notifying all 5 user(s)")).count());
        assertEquals(1, adminResponses.stream().filter(
                r -> r.getParameters().get("text").toString().contains("All Done! Broadcast message(s) 5/5")).count());

        List<SendMessage> usersResponses = sendMessages.stream().filter(
                sm -> (Long) sm.getParameters().get("chat_id") != 1683539744L).collect(Collectors.toList());
        assertEquals(5, usersResponses.size());
        assertEquals(5, usersResponses.stream().filter(
                r -> r.getParameters().get("text").toString().startsWith("Good day everyone!")).count());

    }

    @Test
    void userCommandRewardsTest() throws Exception {
        // Testing Assets command
        Update rewardsCmdUpdate = TelegramUtils.buildRewardsCommandUpdate("-2");
        Message messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMock.messageId()).thenReturn((int) (System.currentTimeMillis() / 1000));
        SendResponse sendRespMock = Mockito.mock(SendResponse.class);
        Mockito.when(sendRespMock.message()).thenReturn(messageMock);
        Mockito.when(this.telegramBotMock.execute(Mockito.any())).thenReturn(sendRespMock);
        this.rewardsCmd.execute(rewardsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sentMessages = this.sendMessageArgCaptor.getAllValues();
        assertEquals(1, sentMessages.size());
        SendMessage message = sentMessages.get(0);

        Map<String, Object> params = message.getParameters();
        Assertions.assertTrue(params.containsKey("text"));
        Assertions.assertTrue(params.containsKey("reply_markup"));

        assertEquals("Please select an account", params.get("text"));
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) params.get("reply_markup");
        assertEquals(2, markup.inlineKeyboard().length);
        InlineKeyboardButton[] firstRow = markup.inlineKeyboard()[0];
        assertEquals(2, firstRow.length);
        Assertions.assertFalse(firstRow[0].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[0].text().isEmpty());
        Assertions.assertFalse(firstRow[1].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[1].text().isEmpty());

        // We take the first button, so we'll get the list of assets for the account.
        String callbackCmd = firstRow[1].callbackData();
        if (firstRow[0].text().contains("$alessio.dev"))
            callbackCmd = firstRow[0].callbackData();
        LOG.info("Getting assets for account {}", callbackCmd);

        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update callbackCmdUpdate = TelegramUtils.buildCallbackCommandUpdate(callbackCmd);
        this.accountRewardsCmd.execute(callbackCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());
        sentMessages = argumentCaptor.getAllValues();
        assertEquals(2, sentMessages.size());

        assertEquals(1,
                sentMessages.stream().map(m -> m.getParameters()
                                .getOrDefault("link_preview_options", new LinkPreviewOptions().isDisabled(false)))
                        .map(LinkPreviewOptions.class::cast)
                        .filter(LinkPreviewOptions::isDisabled).count());
        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Latest rewards for")).count());

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32")).count());

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("$alessio.dev")).count());

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv")).count());

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Epoch 518")).count());

        assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("177.64")).count());
    }

    @Test
    void longTextMultiTxTest() throws Exception {
        Map<String, String> handles = Collections.emptyMap();
        User user = new User(1000L, "XYZ_Address", 999, 999, 0L);
        List<StringBuilder> txBuilders = new ArrayList<>();
        txBuilders.add(createText("A"));
        txBuilders.add(createText("B"));
        txBuilders.add(createText("C"));
        transactionCheckerTaskV2.notifyTelegramUser(txBuilders, user, handles);

        // Verify
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());
        List<Long> chatIdArgCaptorAllValues = this.chatIdArgCaptor.getAllValues();
        List<String> messageArgCaptorAllValues = this.messageArgCaptor.getAllValues();
        assertEquals(1, messageArgCaptorAllValues.size());
        assertEquals(1, chatIdArgCaptorAllValues.size());
        assertEquals(1000L, chatIdArgCaptorAllValues.get(0));
        Assertions.assertTrue(messageArgCaptorAllValues.get(0).endsWith("more..."));
        Assertions.assertTrue(messageArgCaptorAllValues.get(0).length() < TransactionCheckerTaskV2.MAX_MSG_PAYLOAD_SIZE);
    }

    @Test
    void longTextSingleTxTest() throws Exception {
        Map<String, String> handles = Collections.emptyMap();
        User user = new User(1000L, "XYZ_Address", 999, 999, 0L);
        List<StringBuilder> txBuilders = new ArrayList<>();
        txBuilders.add(createText("A").append(createText("B")).append(createText("C")));
        transactionCheckerTaskV2.notifyTelegramUser(txBuilders, user, handles);

        // Verify
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());
        List<Long> chatIdArgCaptorAllValues = this.chatIdArgCaptor.getAllValues();
        List<String> messageArgCaptorAllValues = this.messageArgCaptor.getAllValues();
        assertEquals(1, messageArgCaptorAllValues.size());
        assertEquals(1, chatIdArgCaptorAllValues.size());
        assertEquals(1000L, chatIdArgCaptorAllValues.get(0));
        Assertions.assertTrue(messageArgCaptorAllValues.get(0).endsWith("more..."));
        Assertions.assertTrue(messageArgCaptorAllValues.get(0).length() < TransactionCheckerTaskV2.MAX_MSG_PAYLOAD_SIZE);
    }

    private StringBuilder createText(String prefix) {
        return new StringBuilder(prefix.repeat(2000));
    }
}
