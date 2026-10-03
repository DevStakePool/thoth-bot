package com.devpool.thothBot;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.koios.CountingBackendService;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.scheduler.TransactionCheckerTaskV2;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.AbstractIntegrationTest;
import com.pengrad.telegrambot.TelegramBot;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies how many Koios calls a run of the transaction checker needs, since the Koios tiers are limited by the
 * number of daily requests.
 */
@SpringBootTest(properties = "thoth.users-batch-size=1")
@DirtiesContext
class TransactionCheckerKoiosCallsTest extends AbstractIntegrationTest {

    @MockitoBean
    private TelegramFacade telegramFacadeMock;

    @MockitoBean
    private TelegramBot telegramBotMock;

    @MockitoBean
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionCheckerTaskV2 transactionCheckerTask;

    private MetricsHelper metricsMock;

    @BeforeEach
    public void beforeEach() {
        jdbcTemplate.update("DELETE FROM users");
        // Addresses unknown to the Koios double: nothing ever happens on them
        for (int i = 0; i < 3; i++)
            userDao.addNewUser(new User(-500L - i, "stake1unknownaddress" + i, 10, 5, 100L, 100L));

        this.metricsMock = Mockito.mock(MetricsHelper.class);
        Mockito.when(this.koiosFacade.getKoiosService())
                .thenReturn(CountingBackendService.wrap(new BackendServiceDouble(), this.metricsMock));
    }

    private void verifyCalls(int times, String service, String method) {
        Mockito.verify(this.metricsMock, Mockito.times(times)).incrementCounter(CountingBackendService.CALLS_COUNTER,
                Tag.of(CountingBackendService.TAG_SERVICE, service),
                Tag.of(CountingBackendService.TAG_METHOD, method));
    }

    @Test
    void quietRunAsksForTheChainTipOnceAndSkipsAdaHandles() {
        this.transactionCheckerTask.run();

        // 3 users, batch size 1 -> 3 batches, but the tip is the same for all of them
        verifyCalls(1, "network", "getChainTip");
        verifyCalls(3, "account", "getAccountUTxOs");
        // Nothing new, so no ADA Handle lookups and no calls for the (empty) address part of the batches
        verifyCalls(0, "account", "getAccountAssets");
        verifyCalls(0, "address", "getAddressAssets");
        verifyCalls(0, "address", "getAddressUTxOs");
    }
}
