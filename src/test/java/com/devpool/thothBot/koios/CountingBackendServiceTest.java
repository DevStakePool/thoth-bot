package com.devpool.thothBot.koios;

import com.devpool.thothBot.doubles.koios.BackendServiceDouble;
import com.devpool.thothBot.monitoring.MetricsHelper;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import rest.koios.client.backend.factory.BackendService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CountingBackendServiceTest {

    @Test
    void countsCallsByServiceAndMethodAndDelegates() throws Exception {
        MetricsHelper metrics = Mockito.mock(MetricsHelper.class);
        BackendService service = CountingBackendService.wrap(new BackendServiceDouble(), metrics);

        var tip = service.getNetworkService().getChainTip();
        service.getNetworkService().getChainTip();
        service.getEpochService().getLatestEpochInfo();

        // The call is delegated and the result returned untouched
        assertNotNull(tip.getValue());
        assertEquals(1234, tip.getValue().getBlockNo());

        Mockito.verify(metrics, Mockito.times(2)).incrementCounter(CountingBackendService.CALLS_COUNTER,
                Tag.of(CountingBackendService.TAG_SERVICE, "network"),
                Tag.of(CountingBackendService.TAG_METHOD, "getChainTip"));
        Mockito.verify(metrics, Mockito.times(1)).incrementCounter(CountingBackendService.CALLS_COUNTER,
                Tag.of(CountingBackendService.TAG_SERVICE, "epoch"),
                Tag.of(CountingBackendService.TAG_METHOD, "getLatestEpochInfo"));
    }

    @Test
    void getterOfTheServiceIsNotCounted() {
        MetricsHelper metrics = Mockito.mock(MetricsHelper.class);
        BackendService service = CountingBackendService.wrap(new BackendServiceDouble(), metrics);

        service.getNetworkService();
        service.getAddressService();

        Mockito.verifyNoInteractions(metrics);
    }

    @Test
    void exceptionsAreNotWrapped() {
        MetricsHelper metrics = Mockito.mock(MetricsHelper.class);
        BackendService failing = Mockito.mock(BackendService.class);
        var networkService = Mockito.mock(rest.koios.client.backend.api.network.NetworkService.class);
        Mockito.when(failing.getNetworkService()).thenReturn(networkService);
        try {
            Mockito.when(networkService.getChainTip()).thenThrow(
                    new rest.koios.client.backend.api.base.exception.ApiException("429 Too Many Requests."));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        BackendService service = CountingBackendService.wrap(failing, metrics);

        var e = org.junit.jupiter.api.Assertions.assertThrows(
                rest.koios.client.backend.api.base.exception.ApiException.class,
                () -> service.getNetworkService().getChainTip());
        assertEquals("429 Too Many Requests.", e.getMessage());
    }

    @Test
    void serviceNameIsDerivedFromTheGetter() {
        assertEquals("account", CountingBackendService.serviceName("getAccountService"));
        assertEquals("transactions", CountingBackendService.serviceName("getTransactionsService"));
    }

    @Test
    void peakWindowTracksTheBusiestTenSeconds() {
        var window = new CountingBackendService.PeakWindow();
        long t = 1_000_000_000L;

        // 5 hits in the first second, 5 in the 3rd second -> 10 in the window
        for (int i = 0; i < 5; i++) window.hit(t);
        long peak = 0;
        for (int i = 0; i < 5; i++) peak = window.hit(t + 2_000);
        assertEquals(10, peak);

        // 30 seconds later the old hits are out of the window, the peak is never lowered
        peak = window.hit(t + 32_000);
        assertEquals(10, peak);
    }
}
