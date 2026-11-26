package com.devpool.thothBot;

import com.devpool.thothBot.dao.PoolBlocksDao;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.doubles.koios.BlockServiceDouble;
import com.devpool.thothBot.doubles.koios.NetworkServiceDouble;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.scheduler.PoolBlocksCheckerTask;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.AbstractIntegrationTest;
import com.pengrad.telegrambot.TelegramBot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "thoth.scheduler.initial-delay-secs=999",
        "thoth.admin.user-id=" + PoolBlocksIntegrationTest.ADMIN_ID,
        "thoth.pool-blocks.pool-ids=" + PoolBlocksIntegrationTest.POOL_ID
})
@DirtiesContext
class PoolBlocksIntegrationTest extends AbstractIntegrationTest {
    static final long ADMIN_ID = 4242L;
    static final String POOL_ID = "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv";
    private static final String OTHER_POOL_ID = "pool12wpfng6cu7dz38yduaul3ngfm44xhv5xmech68m5fwe4wu77udd";
    private static final int EPOCH = 371;

    @MockitoBean
    private TelegramFacade telegramFacadeMock;

    @MockitoBean
    private TelegramBot telegramBotMock;

    @MockitoBean
    private KoiosFacade koiosFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PoolBlocksDao poolBlocksDao;

    @Autowired
    private PoolBlocksCheckerTask poolBlocksCheckerTask;

    @BeforeEach
    void beforeEach() {
        jdbcTemplate.update("DELETE FROM pool_blocks");
        System.setProperty(NetworkServiceDouble.TIP_EPOCH_NO_SYS_VAR_KEY, String.valueOf(EPOCH));
        BlockServiceDouble.BLOCKS.clear();
        // Old epoch: must never be notified on the first run
        BlockServiceDouble.BLOCKS.add(BlockServiceDouble.block(POOL_ID, EPOCH - 5, 900));
        BlockServiceDouble.BLOCKS.add(BlockServiceDouble.block(POOL_ID, EPOCH, 1000));
        BlockServiceDouble.BLOCKS.add(BlockServiceDouble.block(POOL_ID, EPOCH, 1010));
        // Other pool
        BlockServiceDouble.BLOCKS.add(BlockServiceDouble.block(OTHER_POOL_ID, EPOCH, 1005));
        Mockito.when(this.koiosFacade.getKoiosService()).thenReturn(new BackendServiceDouble());
        ReflectionTestUtils.setField(this.poolBlocksCheckerTask, "adminUserId", ADMIN_ID);
    }

    @AfterEach
    void afterEach() {
        System.clearProperty(NetworkServiceDouble.TIP_EPOCH_NO_SYS_VAR_KEY);
        BlockServiceDouble.BLOCKS.clear();
    }

    @Test
    void notifiesNewBlocksOfCurrentEpochOnlyOnce() {
        this.poolBlocksCheckerTask.run();

        var chatIds = ArgumentCaptor.forClass(Long.class);
        var messages = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.telegramFacadeMock, Mockito.times(2)).sendMessageTo(chatIds.capture(), messages.capture());
        assertTrue(chatIds.getAllValues().stream().allMatch(id -> id == ADMIN_ID));
        assertTrue(messages.getAllValues().get(0).contains("Block 1000"));
        assertTrue(messages.getAllValues().get(0).contains("Epoch 371"));
        assertTrue(messages.getAllValues().get(0).contains("https://cardanoscan.io/block/1000"));
        assertTrue(messages.getAllValues().get(0).contains("Height: 1000"));
        assertTrue(messages.getAllValues().get(0).contains("Absolute slot: 170001000"));
        assertTrue(messages.getAllValues().get(0).contains("Transactions: 12"));
        assertTrue(messages.getAllValues().get(0).contains("Op cert counter: 7"));
        assertTrue(messages.getAllValues().get(0).contains("Size: 45.2 KB (46285 bytes)"));
        assertTrue(messages.getAllValues().get(1).contains("Block 1010"));
        assertEquals(Optional.of(1010L), this.poolBlocksDao.getLastBlockHeight(POOL_ID));

        // Nothing new: no further notification
        Mockito.clearInvocations(this.telegramFacadeMock);
        this.poolBlocksCheckerTask.run();
        Mockito.verify(this.telegramFacadeMock, Mockito.never()).sendMessageTo(Mockito.anyLong(), Mockito.anyString());

        // New block minted in the next epoch, plus a late block of the previous epoch
        System.setProperty(NetworkServiceDouble.TIP_EPOCH_NO_SYS_VAR_KEY, String.valueOf(EPOCH + 1));
        BlockServiceDouble.BLOCKS.add(BlockServiceDouble.block(POOL_ID, EPOCH, 1020));
        BlockServiceDouble.BLOCKS.add(BlockServiceDouble.block(POOL_ID, EPOCH + 1, 1030));
        this.poolBlocksCheckerTask.run();
        var lateMessages = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.telegramFacadeMock, Mockito.times(2)).sendMessageTo(Mockito.eq(ADMIN_ID), lateMessages.capture());
        assertTrue(lateMessages.getAllValues().get(0).contains("Block 1020"));
        assertTrue(lateMessages.getAllValues().get(1).contains("Block 1030"));
        assertEquals(Optional.of(1030L), this.poolBlocksDao.getLastBlockHeight(POOL_ID));
    }

    @Test
    void noAdminNoNotification() {
        ReflectionTestUtils.setField(this.poolBlocksCheckerTask, "adminUserId", 0L);
        this.poolBlocksCheckerTask.run();
        Mockito.verify(this.telegramFacadeMock, Mockito.never()).sendMessageTo(Mockito.anyLong(), Mockito.anyString());
        assertEquals(Optional.empty(), this.poolBlocksDao.getLastBlockHeight(POOL_ID));
    }
}
