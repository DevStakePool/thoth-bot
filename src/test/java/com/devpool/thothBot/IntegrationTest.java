package com.devpool.thothBot;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.Asset;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

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
        TEST_USERS.add(new User(-4L, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", 0, 0));
        TEST_USERS.add(new User(-5L, "addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv", 0, 0));
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

    @BeforeEach
    public void beforeAll() throws Exception {
        SendResponse respMock = Mockito.mock(SendResponse.class);
        Mockito.when(respMock.isOk()).thenReturn(true);
        Mockito.when(this.telegramBotMock.execute(Mockito.any())).thenReturn(respMock);
    }

    @Test
    public void userCommandHelpTest() throws Exception {
        // Testing Help command
        Update helpCmdUpdate = TelegramUtils.buildHelpCommandUpdate(false, "ironman");
        Update startCmdUpdate = TelegramUtils.buildHelpCommandUpdate(true, "thor");
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
            Assertions.assertFalse(params.get("text").toString().contains("[ADMIN]"));
            Assertions.assertFalse(params.get("text").toString().contains("/notifyall"));
        }
    }

    @Test
    public void userCommandHelpAsAdminTest() throws Exception {
        // Testing Help command
        Update helpCmdUpdate = TelegramUtils.buildHelpCommandUpdate(false, "test_admin");
        this.helpCmd.execute(helpCmdUpdate, this.telegramBotMock);
        Mockito.verify(this.telegramBotMock,
                        Mockito.timeout(10 * 1000)
                                .times(1))
                .execute(this.sendMessageArgCaptor.capture());
        List<SendMessage> sendMessages = this.sendMessageArgCaptor.getAllValues();

        Assertions.assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        Assertions.assertEquals(1683539744L, params.get("chat_id"));
        Assertions.assertEquals(Boolean.TRUE, params.get("disable_web_page_preview"));
        Assertions.assertEquals("HTML", params.get("parse_mode"));
        Assertions.assertTrue(params.get("text").toString().contains("THOTH BOT"));
        Assertions.assertTrue(params.get("text").toString().contains("/help or /start"));
        // We got an admin commmand
        Assertions.assertTrue(params.get("text").toString().contains("[ADMIN]"));
        Assertions.assertTrue(params.get("text").toString().contains("/notifyall"));
    }

    @Test
    public void userCommandInfoStakeAddrTest() throws Exception {
        // Testing Help command
        Update infoCmdUpdate = TelegramUtils.buildInfoCommandUpdate("-2");
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
        Assertions.assertTrue(params.get("text").toString().contains("[HAMDA]"));
        Assertions.assertTrue(params.get("text").toString().contains("Status: registered"));
        Assertions.assertTrue(params.get("text").toString().contains("Rewards: 29,581.45"));
        Assertions.assertTrue(params.get("text").toString().contains("$0x616461"));
        Assertions.assertTrue(params.get("text").toString().contains("Total Balance: 3,002.33"));
    }

    @Test
    public void userCommandInfoNormalAddrTest() throws Exception {
        // Testing Help command
        Update infoCmdUpdate = TelegramUtils.buildInfoCommandUpdate("-4");
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
        Assertions.assertEquals((long) -4, params.get("chat_id"));
        Assertions.assertEquals(Boolean.TRUE, params.get("disable_web_page_preview"));
        Assertions.assertEquals("HTML", params.get("parse_mode"));
        Assertions.assertTrue(params.get("text").toString().contains("$badfriends"));
        Assertions.assertTrue(params.get("text").toString().contains("Balance: 176.39"));
        Assertions.assertTrue(params.get("text").toString().contains("Stake Address: NO"));
        Assertions.assertTrue(params.get("text").toString().contains("Script Address: YES"));
        Assertions.assertTrue(params.get("text").toString().contains("UTXOs: 112"));
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
        Assertions.assertTrue(params.get("text").toString().contains("Hi Alessio, please send your address"));
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
        Assertions.assertTrue(params.get("text").toString().contains("Hi Alessio, please specify your address"));
    }

    @Test
    public void scheduledNotificationsTest() throws Exception {
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(78))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<User> allUsers = this.userDao.getUsers();
        List<Long> allChatIds = allUsers.stream().map(User::getChatId).sorted().collect(Collectors.toList());
        LOG.info("UserChatIDs={}", allChatIds);
        LOG.info("ChatIDs={}", this.chatIdArgCaptor.getAllValues().stream().sorted().collect(Collectors.toList()));

        // Check if we got all the expected TXs
        // This is calculated using the script calculate_expected_telegram_messages.sh
        //Expected messages regarding staking rewards: 4
        //Expected transactions for account stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr: 8
        //Expected transactions for account stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32: 86
        //Expected transactions for account stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz: 41
        //Expected transactions for account stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy: 16
        //Expected transactions for addresses addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv: 5
        //Expected transactions for addresses addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m: 58
        //Total expected transactions for accounts+addresses 214

        List<String> allMessages = messageArgCaptor.getAllValues();

        Assertions.assertEquals(8, countTxForAddress(allMessages, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr"));
        Assertions.assertEquals(86, countTxForAddress(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32"));
        Assertions.assertEquals(41, countTxForAddress(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz"));
        Assertions.assertEquals(16, countTxForAddress(allMessages, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy"));
        Assertions.assertEquals(5, countTxForAddress(allMessages, "addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv"));
        Assertions.assertEquals(58, countTxForAddress(allMessages, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m"));
        Assertions.assertEquals(4, allMessages.stream().filter(m -> m.contains("reward(s)")).count());
        // TX internal, empty
        String message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "3af819e5583709c9e7b5b84614c60015b9bf10deb2b20756118cba707e531e53");
        Assertions.assertTrue(message.contains("Fee 0.18"));

        // TX internal, pool delegation
        message = retrieveMessageByString(allMessages, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr",
                "3f31f56afbfa4c4bdd7c33d1f1d4ae0cedece2fa2bfb2934b914ea5e0dfb0142");
        Assertions.assertTrue(message.contains("[DEV]"));

        // TX sent funds, with message
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "773b01dfcd7a0398a8576129410084c8797906b913bdf6437289daebb672f085");
        Assertions.assertTrue(message.contains("Fee 0.20"));
        Assertions.assertTrue(message.contains("DEV Pool patron rewards for epoch 377"));
        Assertions.assertTrue(message.contains("Sent -55.00"));

        // TX catalyst new airdrop method
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "19c57aec14b9cefd4b1025c09c64bf857a4d2e3c0ee184d62b2eca8dfceb929b");
        Assertions.assertTrue(message.contains("59.54"));
        Assertions.assertTrue(message.contains("Fund10 Voter rewards"));

        // TX received funds, 1 token
        message = retrieveMessageByString(allMessages, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", "9f8067d6565c5e189551b9ca820ff27ec87dc955420ab9d8a6ce4107d5d27743");
        Assertions.assertTrue(message.contains("wide open 1"));
        Assertions.assertTrue(message.contains("Received 1.04"));

        // TX received funds, with message
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "773b01dfcd7a0398a8576129410084c8797906b913bdf6437289daebb672f085");
        Assertions.assertTrue(message.contains("DEV Pool patron rewards for epoch 377"));
        Assertions.assertTrue(message.contains("Sent -55.00"));
        Assertions.assertTrue(message.contains("Fee 0.20"));

        // TX sent funds, sent 1 asset
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "2148c8689b89825055e863142ad502e17969c4543cbc6b532bd78bc2b7c2c250");
        Assertions.assertTrue(message.contains("Fee 0.26"));
        Assertions.assertTrue(message.contains("Cardano Summit 2023 NFT 4326"));
        Assertions.assertTrue(message.contains("Sent -1.39"));

        // TX sent funds, sent many assets
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "3e2e4a2b7d78bc5994773805f1376d790c8169b63297d50ef4842e22aafb1f29");
        Assertions.assertTrue(message.contains("Fee 0.39"));
        Assertions.assertTrue(message.contains("Sent -5.57"));
        Assertions.assertTrue(message.contains("Cardano Summit 2023 NFT 1939 1"));
        Assertions.assertTrue(message.contains("Cardano Summit 2023 NFT 3022 1"));
        Assertions.assertTrue(message.contains("Cardano Summit 2023 NFT 2884 1"));
        Assertions.assertTrue(message.contains("Cardano Summit 2023 NFT 5802 1"));

        // TX sent funds, jpeg store contract
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "e9f577499a692fc07491cd7de013ea2c3b3a37b3df616aeb39f807ed5ced8d24");
        Assertions.assertTrue(message.contains("Fee 0.46"));
        Assertions.assertTrue(message.contains("Sent -13.61"));
        Assertions.assertTrue(message.contains("JpegStore"));

        // TX received funds, jpeg store multiple (2) contracts
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "b6170c1c89f91bb5f76c0810889ea110f34b63e7fde25b37abe269256ac2f45a");
        Assertions.assertTrue(message.contains("Fee 1.11"));
        Assertions.assertTrue(message.contains("Received 18.00"));
        message = message.substring(message.indexOf("b6170c1c89f91bb5f76c0810889ea110f34b63e7fde25b37abe269256ac2f45a"));
        Assertions.assertEquals(2, message.split("JpegStore").length - 1);

        // TX with ada handle
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", "a6abf48aad975fac80416ce79f9a7969fe05e13a37eb8be1e917d5d84d6044");
        Assertions.assertTrue(message.contains("$alessio.dev"));

        // TX sent funds and tokens (with token hash)
        message = retrieveMessageByString(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz", "69c2f2f96305b5d1eb46eb5180f9dfb0409c54919d2463ae61becf34570e504a");
        Assertions.assertTrue(message.contains("...9d660cf6a2 498,221,110"));
        Assertions.assertTrue(message.contains("hvADA 2,528,098"));

        // Pool operator rewards
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv");
        Assertions.assertTrue(message.contains("[DEV]"));
        Assertions.assertTrue(message.contains("Epoch 369"));
        Assertions.assertTrue(message.contains("Pool Operator Rewards 371.18"));

        // Staking rewards
        message = retrieveMessageByString(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz",
                "pool19v4jgpxxl72eaq8pefctatvts3gkhtyzd8nwvzskkm3mkdv2tpy");
        Assertions.assertTrue(message.contains("pool1...mkdv2tpy"));
        Assertions.assertTrue(message.contains("Epoch 369"));
        Assertions.assertTrue(message.contains("Staking Rewards 0.73"));

        // TX sent funds, received tokens
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "e9b067658de4e5b08d07dcffd9c063c08167cc03d88525b260a3942ff63a0b26");
        Assertions.assertTrue(message.contains("Cardano Summit 2023 NFT 7469 1"));
        Assertions.assertTrue(message.contains("-96.61"));
        Assertions.assertTrue(message.contains("Sent Funds and Received Tokens"));
        Assertions.assertTrue(message.contains("[JpegStore]"));

        // TX received funds, received tokens
        message = retrieveMessageByString(allMessages, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy",
                "083e302d0c5d11c07fa642c18df8fa290632c24157c676a754a5a763605ebe26");
        Assertions.assertTrue(message.contains("MACH 3,947.00"));
        Assertions.assertTrue(message.contains("1.73"));
        Assertions.assertTrue(message.contains("Received Funds and Received Tokens"));
        Assertions.assertFalse(message.contains("[JpegStore]"));
        Assertions.assertEquals(2, message.split("MACH").length);
        Assertions.assertFalse(message.contains("Withdrawal"));

        // Issue 38 TX with withdrawals
        message = retrieveMessageByString(allMessages, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy",
                "054ac848b8bb5a30d39ed1352bc4441f59599cce9ad3bb2b06fb46e54270e606");
        Assertions.assertTrue(message.contains("Sent -6.50"));
        Assertions.assertTrue(message.contains("Withdrawal 98.32"));

        // check for null handles
        for (String m : allMessages) {
            Assertions.assertFalse(m.contains("null"), "message contains 'null': " + m);
        }
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

    private int countTxForAddress(List<String> messages, String address) {
        List<String> messagesForAccount = messages.stream()
                .filter(m -> m.contains(address)).collect(Collectors.toList());

        return messagesForAccount.stream().mapToInt(m -> m.split("Fee").length - 1).sum();
    }
}
