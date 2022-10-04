package com.devpool.thothBot.koios;

import com.devpool.thothBot.monitoring.MetricsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.BackendFactory;
import rest.koios.client.backend.factory.BackendService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class KoiosFacade {
    private static final Logger LOG = LoggerFactory.getLogger(KoiosFacade.class);
    private BackendService koiosService;
    private final Timer performanceSampler = new Timer("Koios Facade Sampler", true);

    private long apiCalls;
    private Instant lastSampleInstant;

    @Autowired
    private MetricsHelper metricsHelper;

    @PostConstruct
    public void post() throws ApiException {
        this.koiosService = BackendFactory.getKoiosMainnetService();

        // Create performance samples
        performanceSampler.schedule(new TimerTask() {
            @Override
            public void run() {
                sampleMetrics();
            }
        }, 1000, 5000);

        LOG.info("KOIOS Facade initialised");
    }

    private void sampleMetrics() {
        synchronized (this.performanceSampler) {
            Instant now = Instant.now();
            if (this.lastSampleInstant == null) {
                this.lastSampleInstant = now;
                this.apiCalls = 0;
            } else {
                long apiCallsCurr = this.apiCalls;
                this.apiCalls = 0;
                int millis = (int) (now.toEpochMilli() - lastSampleInstant.toEpochMilli());
                lastSampleInstant = now;
                double apiCallsPerSecond = (apiCallsCurr / (millis / 1000.0));

                // Update gauge metric
                this.metricsHelper.hitGauge("koios_api_hits_per_sec", (long) apiCallsPerSecond);
                LOG.trace("Calculated new gauge sample for koios {} hits/sec", apiCallsPerSecond);
            }
        }
    }

    @PreDestroy
    public void preDestroy() {
        this.performanceSampler.cancel();
    }

    public BackendService getKoiosService() {
        this.metricsHelper.incrementCounter("koios_api_total_hits");
        synchronized (this.performanceSampler) {
            this.apiCalls++;
        }
        return koiosService;
    }
}
