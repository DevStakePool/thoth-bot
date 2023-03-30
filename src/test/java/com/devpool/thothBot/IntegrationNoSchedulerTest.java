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
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("no-scheduler")
public class IntegrationNoSchedulerTest {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationNoSchedulerTest.class);
    private static List<User> TEST_USERS = new ArrayList<>();

    static {
        TEST_USERS.add(new User(-1L, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", 0, 0));
        TEST_USERS.add(new User(-2L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", 0, 0));
        TEST_USERS.add(new User(-2L, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz", 0, 0));
        TEST_USERS.add(new User(-3L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", 0, 0));
        TEST_USERS.add(new User(-1000L, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", 0, 0));
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
    private AddressCmd stakeCmd;

    @Autowired
    private AssetsCmd assetsCmd;

    @Autowired
    private DetailsCmd detailsCmd;

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
    public void userCommandAddrForSubscribeTest() throws Exception {
        // Testing Address command
        Update addrCmdUpdate = TelegramUtils.buildAddrCommandUpdate(
                "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", -1000);
        this.stakeCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        Assertions.assertEquals((long) -1000, params.get("chat_id"));
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
        this.stakeCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sendMessages = argumentCaptor.getAllValues();

        Assertions.assertEquals(3, sendMessages.size());
        Assertions.assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Please specify the operation first")).count());
        Assertions.assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please send your address")).count());
        Assertions.assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("From now on you will receive updates")).count());
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
        argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        this.stakeCmd.execute(addrCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(2))
                .execute(argumentCaptor.capture());
        List<SendMessage> sendMessages = argumentCaptor.getAllValues();

        Assertions.assertEquals(2, sendMessages.size());
        Assertions.assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("please specify your address")).count());
        Assertions.assertEquals(1,
                sendMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("You have successfully unsubscribed the address")).count());
    }

    @Test
    public void userCommandAssetsForStakeAddressTest() throws Exception {
        // Testing Assets command
        Update assetsCmdUpdate = TelegramUtils.buildAssetsCommandUpdate("-2");
        this.assetsCmd.execute(assetsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sentMessages = this.sendMessageArgCaptor.getAllValues();
        Assertions.assertEquals(1, sentMessages.size());
        SendMessage message = sentMessages.get(0);

        Map<String, Object> params = message.getParameters();
        Assertions.assertTrue(params.containsKey("text"));
        Assertions.assertTrue(params.containsKey("reply_markup"));

        Assertions.assertEquals("Please select an account", params.get("text"));
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) params.get("reply_markup");
        Assertions.assertEquals(1, markup.inlineKeyboard().length);
        InlineKeyboardButton[] firstRow = markup.inlineKeyboard()[0];
        Assertions.assertEquals(2, firstRow.length);
        Assertions.assertFalse(firstRow[0].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[1].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[0].text().isEmpty());
        Assertions.assertFalse(firstRow[1].text().isEmpty());

        // We take the first button, so we'll get the list of assets of the account.
        String callbackCmd = firstRow[0].callbackData();
        LOG.info("Getting assets for account {}", callbackCmd);

        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update detailsCmdUpdate = TelegramUtils.buildDetailsCommandUpdate(callbackCmd);
        this.detailsCmd.execute(detailsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sentMessages = argumentCaptor.getAllValues();
        Assertions.assertEquals(3, sentMessages.size());

        Assertions.assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Processing...")).count());
        Assertions.assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Assets:")).count());
        Assertions.assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("CULO 100,000")).count());
    }

    @Test
    public void userCommandAssetsForNormalAddressTest() throws Exception {
        // Testing Assets command
        Update assetsCmdUpdate = TelegramUtils.buildAssetsCommandUpdate("-1000");
        this.assetsCmd.execute(assetsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sentMessages = this.sendMessageArgCaptor.getAllValues();
        Assertions.assertEquals(1, sentMessages.size());
        SendMessage message = sentMessages.get(0);

        Map<String, Object> params = message.getParameters();
        Assertions.assertTrue(params.containsKey("text"));
        Assertions.assertTrue(params.containsKey("reply_markup"));

        Assertions.assertEquals("Please select an account", params.get("text"));
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) params.get("reply_markup");
        Assertions.assertEquals(1, markup.inlineKeyboard().length);
        InlineKeyboardButton[] firstRow = markup.inlineKeyboard()[0];
        Assertions.assertEquals(1, firstRow.length);
        Assertions.assertFalse(firstRow[0].callbackData().isEmpty());
        Assertions.assertFalse(firstRow[0].text().isEmpty());

        // We take the first button, so we'll get the list of assets of the account.
        String callbackCmd = firstRow[0].callbackData();
        LOG.info("Getting assets for account {}", callbackCmd);

        ArgumentCaptor<SendMessage> argumentCaptor = ArgumentCaptor.forClass(SendMessage.class);
        Update detailsCmdUpdate = TelegramUtils.buildDetailsCommandUpdate(callbackCmd);
        this.detailsCmd.execute(detailsCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(3))
                .execute(argumentCaptor.capture());
        sentMessages = argumentCaptor.getAllValues();
        Assertions.assertEquals(3, sentMessages.size());

        Assertions.assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Processing...")).count());
        Assertions.assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("Assets:")).count());
        Assertions.assertEquals(1,
                sentMessages.stream().filter(m -> m.getParameters().get("text")
                        .toString().contains("ClayNationPitch21050 1")).count());
    }
}
