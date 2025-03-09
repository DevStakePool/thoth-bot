package com.devpool.thothBot;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.telegram.command.AccountInfoCmd;
import com.devpool.thothBot.telegram.command.AddressCmd;
import com.devpool.thothBot.telegram.command.HelpCmd;
import com.devpool.thothBot.telegram.command.SubscribeCmd;
import com.devpool.thothBot.util.AbstractIntegrationTest;
import com.devpool.thothBot.util.TelegramUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext
class IntegrationTest extends AbstractIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTest.class);
    private static List<User> TEST_USERS = new ArrayList<>();

    static {
        TEST_USERS.add(new User(-1L, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr", 0, 0, 0L, 0L));
        TEST_USERS.add(new User(-2L, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32", 0, 0, 0L, 0L));
        TEST_USERS.add(new User(-2L, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz", 0, 0, 0L, 0L));
        TEST_USERS.add(new User(-3L, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy", 0, 0, 0L, 0L));
        TEST_USERS.add(new User(-4L, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m", 0, 0, 0L, 0L));
        TEST_USERS.add(new User(-5L, "addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv", 0, 0, 0L, 0L));
        // Issue #43
        TEST_USERS.add(new User(-43L, "stake1u8656c05pay70xtpcwp3dqgu4jwullv6qu9e50ykn59lz7g7vzwt7", 0, 0, 0L, 0L));
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

        assertEquals(2, sendMessages.size());
        for (SendMessage sendMessage : sendMessages) {
            LOG.debug("Message params: {}", sendMessage.getParameters());
            Map<String, Object> params = sendMessage.getParameters();
            assertEquals(1683539744L, params.get("chat_id"));
            assertInstanceOf(LinkPreviewOptions.class, params.get("link_preview_options"));
            assertEquals(Boolean.TRUE, ((LinkPreviewOptions) params.get("link_preview_options")).isDisabled());
            assertEquals("HTML", params.get("parse_mode"));
            assertTrue(params.get("text").toString().contains("THOTH BOT"));
            assertTrue(params.get("text").toString().contains("/help or /start"));
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

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        assertEquals(1683539744L, params.get("chat_id"));
        assertInstanceOf(LinkPreviewOptions.class, params.get("link_preview_options"));
        assertEquals(Boolean.TRUE, ((LinkPreviewOptions) params.get("link_preview_options")).isDisabled());
        assertEquals("HTML", params.get("parse_mode"));
        assertTrue(params.get("text").toString().contains("THOTH BOT"));
        assertTrue(params.get("text").toString().contains("/help or /start"));
        // We got an admin commmand
        assertTrue(params.get("text").toString().contains("[ADMIN]"));
        assertTrue(params.get("text").toString().contains("/notifyall"));
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

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        assertEquals((long) -2, params.get("chat_id"));
        assertInstanceOf(LinkPreviewOptions.class, params.get("link_preview_options"));
        assertEquals(Boolean.TRUE, ((LinkPreviewOptions) params.get("link_preview_options")).isDisabled());
        assertEquals("HTML", params.get("parse_mode"));
        assertTrue(params.get("text").toString().contains("[DEV]"));
        assertTrue(params.get("text").toString().contains("pool15fxktqvd92sq8plh3rjdrksumt9p8rzsayfk4akv2hng5r8ukha"));
        assertTrue(params.get("text").toString().contains("Status: registered"));
        assertTrue(params.get("text").toString().contains("Rewards: 156.35"));
        assertTrue(params.get("text").toString().contains("$0x616461"));
        assertTrue(params.get("text").toString().contains("Total Balance: 3,037.79"));
        assertTrue(params.get("text").toString().contains("drep_always_abstain")); // The DRep name
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

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();
        assertEquals((long) -4, params.get("chat_id"));
        assertInstanceOf(LinkPreviewOptions.class, params.get("link_preview_options"));
        assertEquals(Boolean.TRUE, ((LinkPreviewOptions) params.get("link_preview_options")).isDisabled());
        assertEquals("HTML", params.get("parse_mode"));
        assertTrue(params.get("text").toString().contains("$badfriends"));
        assertTrue(params.get("text").toString().contains("Balance: 176.00"));
        assertTrue(params.get("text").toString().contains("Stake Address: NO"));
        assertTrue(params.get("text").toString().contains("Script Address: YES"));
        assertTrue(params.get("text").toString().contains("UTXOs: 112"));
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

        assertEquals(1, sendMessages.size());
        SendMessage sendMessage = sendMessages.get(0);
        LOG.debug("Message params: {}", sendMessage.getParameters());
        Map<String, Object> params = sendMessage.getParameters();

        assertEquals(-1000L, params.get("chat_id"));
        assertEquals(ForceReply.class, params.get("reply_markup").getClass());
        assertTrue(params.get("text").toString().contains("Hi Alessio, please send your address"));
    }

    @Test
    public void scheduledNotificationsTest() throws Exception {
        Mockito.verify(this.telegramFacadeMock,
                        Mockito.timeout(60 * 1000)
                                .times(70))
                .sendMessageTo(this.chatIdArgCaptor.capture(), this.messageArgCaptor.capture());

        List<User> allUsers = this.userDao.getUsers();
        List<Long> allChatIds = allUsers.stream().map(User::getChatId).sorted().collect(Collectors.toList());
        LOG.info("UserChatIDs={}", allChatIds);
        LOG.info("ChatIDs={}", this.chatIdArgCaptor.getAllValues().stream().sorted().collect(Collectors.toList()));

        // Check if we got all the expected TXs
        // This is calculated using the script calculate_expected_telegram_messages.sh
        //Expected messages regarding staking rewards: 2
        //Expected transactions for account stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr: 17
        //Expected transactions for account stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32: 30
        //Expected transactions for account stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz: 45
        //Expected transactions for account stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy: 23
        //Expected transactions for addresses addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv: 5
        //Expected transactions for addresses addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m: 58
        //Total expected transactions for accounts+addresses 178


        // Numbers below differs from this due to the data added from issues

        List<String> allMessages = messageArgCaptor.getAllValues();

        assertEquals(17, countTxForAddress(allMessages, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr"));
        assertEquals(31, countTxForAddress(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32")); // FIXME The script somehow gives only 30
        assertEquals(45, countTxForAddress(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz"));
        assertEquals(23, countTxForAddress(allMessages, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy"));
        assertEquals(4, countTxForAddress(allMessages, "stake1u8656c05pay70xtpcwp3dqgu4jwullv6qu9e50ykn59lz7g7vzwt7"));
        assertEquals(5, countTxForAddress(allMessages, "addr1qy2jt0qpqz2z2z9zx5w4xemekkce7yderz53kjue53lpqv90lkfa9sgrfjuz6uvt4uqtrqhl2kj0a9lnr9ndzutx32gqleeckv"));
        assertEquals(58, countTxForAddress(allMessages, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m"));
        assertEquals(4, allMessages.stream().filter(m -> m.contains("reward(s)")).count()); // FIXME 4 is probably correct. Check why the script gives you 2!
        // TX internal, empty
        String message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "5ef51012dcaa1811606a97f200892c0545d034ee45d1ae3651da62a863d25f72");
        assertTrue(message.contains("Fee 0.17"));
        assertTrue(message.contains("Internal Funds"));

        // TX internal, pool delegation
        message = retrieveMessageByString(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz",
                "559508ea65e38bcf208b7155b0e5ca9aeade561c6a86d5a29f8dd9d97c660027");
        assertTrue(message.contains("Internal Funds"));
        assertTrue(message.contains("[DYNO]"));

        // TX sent funds, with a message, with withdrawal
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "fcb3febffe0ca9a204bc1358a1f3a3ab439457d95b079755172a69b367d98768");
        assertTrue(message.contains("Fee 0.23"));
        assertTrue(message.contains("DEV pool patron rewards for epoch 498"));
        assertTrue(message.contains("Sent -126.00"));
        assertTrue(message.contains("Withdrawal 431.96"));

        // TX catalyst new airdrop method
        message = retrieveMessageByString(allMessages, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy",
                "e77ee4530e329c0ac0ed9b3c92c7eabc15b2cb5a0e502bdf5ff09287f0881c1c");
        assertTrue(message.contains("Received 16.97"));
        assertTrue(message.contains("Fee 0.87"));
        assertTrue(message.contains("Fund10 Voter rewards"));

        // TX received funds, 1 token
        message = retrieveMessageByString(allMessages, "addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m",
                "9f8067d6565c5e189551b9ca820ff27ec87dc955420ab9d8a6ce4107d5d27743");
        assertTrue(message.contains("wide open 1"));
        assertTrue(message.contains("Received 1.04"));

        // TX sent funds, sent 1 asset (3 tokens)
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "69f7fe60f8265cce68027db3b7f0451cbab46f183f62c40ef5bd847eb496a632");
        assertTrue(message.contains("Fee 0.22"));
        assertTrue(message.contains("DEV -3"));
        assertTrue(message.contains("Sent -126.00"));

        // TX with DRep delegation to always abstain
        message = retrieveMessageByString(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz",
                "9c4ece21935395a36c64234ccd4937ee06404c6d292fa9317b08986af0aca599");
        assertTrue(message.contains("Internal Funds"));
        assertTrue(message.contains("Delegated to")); // Pool delegation
        assertTrue(message.contains("DRep delegation to drep_always_abstain"));

        // TX with DRep delegation to valid DRep
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "5ef51012dcaa1811606a97f200892c0545d034ee45d1ae3651da62a863d25f72");
        assertTrue(message.contains("Internal Funds"));
        assertTrue(message.contains("DRep delegation to"));
        assertTrue(message.contains("drep1g0g4ntlgxfnanvtqaa405y3ruszqyfhx46snptktzwg8xvtce0u"));
        // TX sent funds, sent many assets
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "0554d580306ccdab5267f17110d91d55ee048d971353feb64525e20ef2a5abbe");
        assertTrue(message.contains("Fee 0.44"));
        assertTrue(message.contains("Sent -8.34"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 3156 -1"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 1571 -1"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 410 -1"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 5455 -1"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 1501 -1"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 837 -1"));

        // TX sent funds, received token from jpeg store contract
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "3b78e91d43299f624ceeb8fc55cf01d489c2c906a7cdd499c16bb9a5bdbf04af");
        assertTrue(message.contains("Fee 0.40"));
        assertTrue(message.contains("Sent -8.61"));
        assertTrue(message.contains("JpegStore"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 6716 1"));

        // TX received funds, jpeg store multiple (2) contracts
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "d5072a58f447c1822f46a90e9f17ab5433df01592fb2c9339061ed474a21acdb");
        assertTrue(message.contains("Fee 0.38"));
        assertTrue(message.contains("Received 2.31"));
        message = message.substring(message.indexOf("d5072a58f447c1822f46a90e9f17ab5433df01592fb2c9339061ed474a21acdb"),
                message.lastIndexOf("Teddy"));
        assertEquals(2, message.split("\\[JpegStore]").length - 1);

        // TX with ada handle
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "0554d580306ccdab5267f17110d91d55ee048d971353feb64525e20ef2a5abbe");
        assertTrue(message.contains("$alessio.dev"));

        // TX sent funds and received tokens
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "11710bb4d8add0a3a33aa5ced3c1567e809de2dca213d737d463d9bd7c1bb90b");
        assertTrue(message.contains("Received 1.12"));
        assertTrue(message.contains("Fee 0.27"));
        assertTrue(message.contains("Received Funds and Received Tokens"));
        assertTrue(message.contains("Cardano Summit 2023 NFT 6599 1"));

        // Pool operator rewards
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv");
        assertTrue(message.contains("[DEV]"));
        assertTrue(message.contains("Epoch 369"));
        assertTrue(message.contains("Pool Operator Rewards 371.18"));

        // Staking rewards
        message = retrieveMessageByString(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz",
                "pool19v4jgpxxl72eaq8pefctatvts3gkhtyzd8nwvzskkm3mkdv2tpy");
        assertTrue(message.contains("pool1...mkdv2tpy"));
        assertTrue(message.contains("Epoch 369"));
        assertTrue(message.contains("Staking Rewards 0.73"));

        // TX received funds, received tokens
        message = retrieveMessageByString(allMessages, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy",
                "083e302d0c5d11c07fa642c18df8fa290632c24157c676a754a5a763605ebe26");
        assertTrue(message.contains("MACH 3,947"));
        assertTrue(message.contains("1.73"));
        assertTrue(message.contains("Received Funds and Received Tokens"));
        Assertions.assertFalse(message.contains("[JpegStore]"));
        assertEquals(2, message.split("MACH").length);
        Assertions.assertFalse(message.contains("Withdrawal"));

        // Issue 38 TX with withdrawals
        message = retrieveMessageByString(allMessages, "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy",
                "054ac848b8bb5a30d39ed1352bc4441f59599cce9ad3bb2b06fb46e54270e606");
        assertTrue(message.contains("Sent -6.50"));
        assertTrue(message.contains("Withdrawal 98.32"));

        // Withdrawals but not for this stake address
        /* Can't find this utxo
        message = retrieveMessageByString(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz",
                "e98566e3d80b89fb96d852bd765cc8e88f28518944a60e168dbc879beea56f5");
        Assertions.assertFalse(message.contains("Withdrawal"));
        Assertions.assertTrue(message.contains("Received 300"));
        */

        // TX with no input but just output (simple received NFT + funds from a friend)
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "2c122a05b4cc3121e2e7225374ae8cfe74d947aa8169264878d2fd1dc0a40702");
        assertTrue(message.contains("Received Funds and Received Tokens"));
        assertTrue(message.contains("Fee 0.17"));
        assertTrue(message.contains("Received 1.19"));
        assertTrue(message.contains("CARDANO PETS-0026 1"));

        // Issue #43
        message = retrieveMessageByString(allMessages, "stake1u8656c05pay70xtpcwp3dqgu4jwullv6qu9e50ykn59lz7g7vzwt7",
                "f5401d48ac42a1199c8fbb214e63e4f350ee5a4f099ff460ca7f8f7bdcfabd4c");

        assertTrue(message.contains("Sent Funds and Sent Tokens"));
        assertTrue(message.contains("Djed USD -746.00"));
        assertTrue(message.contains("-1.19"));
        Assertions.assertFalse(message.contains("iETH"));

        // Issue #39
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "0a416d362c9e1884292c4160254a7a8afc4b3921c783114d3d7574a8087ba3da");
        assertTrue(message.contains("Sent Funds and Sent Tokens"));
        assertTrue(message.contains("Dexhunter Trade"));
        assertTrue(message.contains("Empowa -3,025.28"));
        assertTrue(message.contains("Sent -14"));
        assertTrue(message.contains("Fee 0.25"));

        // Issue #47
        message = retrieveMessageByString(allMessages, "stake1u8656c05pay70xtpcwp3dqgu4jwullv6qu9e50ykn59lz7g7vzwt7",
                "779133d969dc88440d18741dc17e536b8b1b21ac0fdb431f4d2850f028839d81");
        assertTrue(message.contains("Sent Funds, Sent and Received Tokens"));
        assertTrue(message.contains("Fee 0.32"));
        assertTrue(message.contains("Sent -2.00"));
        assertTrue(message.contains("iUSD -2,369.08"));
        assertTrue(message.contains("qiUSD 116,002.71"));

        // Issue #47 - received funds and sent tokens
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "24188c3d4aa6efad2c1250705a9ee5f8acd8c59cf9e4eebf9541477af7b10d15");
        assertTrue(message.contains("Received Funds and Sent Tokens"));
        assertTrue(message.contains("Received 16.07"));
        assertTrue(message.contains("Fee 0.40"));
        assertTrue(message.contains("MalTheTrader12416 -1"));

        // Issue #49 - zero value assets
        message = retrieveMessageByString(allMessages, "stake1u8656c05pay70xtpcwp3dqgu4jwullv6qu9e50ykn59lz7g7vzwt7",
                "6ad2da8864edf66da2090de526a9c8851c41331c319896ba6d65c7a2278ecba6");
        assertTrue(message.contains("qiUSD -115,880.08"));
        assertTrue(message.contains("qDJED -1,446,879.76"));
        Assertions.assertFalse(message.contains("0.00"));

        message = retrieveMessageByString(allMessages, "stake1u8656c05pay70xtpcwp3dqgu4jwullv6qu9e50ykn59lz7g7vzwt7",
                "4e00fca28aaa0c5a3907290ee5f94d4c265f0f5e950585b3627d64018b5633df");
        assertTrue(message.contains("Fee 0.28"));
        assertTrue(message.contains("Sent -3,016.00"));
        assertTrue(message.contains("AdaMarkets_3"));
        Assertions.assertFalse(message.contains("0.00"));

        // Assert governance vote notifications
        message = retrieveMessageByString(allMessages, "stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32",
                "drep1yfpazkd0aqex0kd3vrhk47sjy0jqgq3xu6h2zv9wevfequcxyynwv");
        assertTrue(message.contains("$alessio.dev"));
        assertTrue(message.contains("ucxyynwv"));
        assertTrue(message.contains("78ea58d7"));
        assertTrue(message.contains("Yes"));
        assertTrue(message.contains("e035c916"));
        assertTrue(message.contains("Abstain"));

        message = retrieveMessageByString(allMessages, "stake1u8lffpd48ss4f2pe0rhhj4n2edkgwl38scl09f9f43y0azcnhxhwr",
                "drep1y2m0g4r66pyaw3p7u454wc0p4f0ygm8ueaev0mgd3tvwm7sskqwqp");
        assertTrue(message.contains("CardanoYoda"));
        assertTrue(message.contains("424dc1db"));
        assertTrue(message.contains("Yes"));

        // Internal with metadata
        message = retrieveMessageByString(allMessages, "stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz",
                "3d7d75beafc89efdc06dfadd0823b357bdb0b7c4ed22cea31eb77105d7df1738");
        assertTrue(message.contains("Internal Funds"));
        assertTrue(message.contains("With Metadata"));

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

        assertEquals(1, matching.size(),
                String.format("Expected only 1 match, using filter1=%s and filter2=%s, but got %d", filter1, filter2, matching.size()));

        return matching.get(0);
    }

    private int countTxForAddress(List<String> messages, String address) {
        List<String> messagesForAccount = messages.stream()
                .filter(m -> m.contains(address)).toList();

        return messagesForAccount.stream().mapToInt(m -> m.split("Fee").length - 1).sum();
    }
}
