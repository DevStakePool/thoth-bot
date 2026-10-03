package com.devpool.thothBot.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MetricsHelper {
    private static final String APPLICATION_KEY = "application";
    private static final String METRICS_NAME_PREFIX = "thoth_";
    private static final Logger LOG = LoggerFactory.getLogger(MetricsHelper.class);

    @Autowired
    private ApplicationContext applicationContext;

    // We can't use the @Autowired here otherwise we will lose the jvm stats
    // https://github.com/micrometer-metrics/micrometer/issues/823
    private volatile MeterRegistry meterRegistry;

    private Map<String, Timer> timersCache;
    private Map<String, Counter> countersCache;
    private Map<String, AtomicLong> gaugeCache;

    public MetricsHelper() {
        this.timersCache = new ConcurrentHashMap<>();
        this.countersCache = new ConcurrentHashMap<>();
        this.gaugeCache = new ConcurrentHashMap<>();
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> configurator(
            @Value("${spring.application.name}") String applicationName) {
        return (registry) -> registry.config().commonTags(APPLICATION_KEY, applicationName);
    }

    private void initMeterRegistry() {
        if (this.meterRegistry != null)
            return;

        this.meterRegistry = this.applicationContext.getBean(MeterRegistry.class);
        LOG.info("Metrics Helper initialised");
    }

    public void hitGauge(String gaugeName, long value) {
        initMeterRegistry();

        AtomicLong gaugeVal = this.gaugeCache.get(METRICS_NAME_PREFIX + gaugeName);
        if (gaugeVal == null) {
            gaugeVal = this.meterRegistry.gauge(METRICS_NAME_PREFIX + gaugeName, new AtomicLong(0));
            this.gaugeCache.put(METRICS_NAME_PREFIX + gaugeName, gaugeVal);
        }

        gaugeVal.set(value);
    }

    public void incrementCounter(String counterName, Double increment, Tag... tags) {
        initMeterRegistry();

        List<Tag> tagsList = Arrays.asList(tags);
        // The name is part of the key, otherwise counters with the same tags would share the same counter
        String cacheKey = counterName + tagsList;
        Counter cachedCounter = this.countersCache.get(cacheKey);
        if (cachedCounter == null) {
            cachedCounter = this.meterRegistry.counter(METRICS_NAME_PREFIX + counterName, tagsList);
            this.countersCache.put(cacheKey, cachedCounter);
        }

        cachedCounter.increment(increment);
    }

    public void incrementCounter(String counterName, Tag... tags) {
        incrementCounter(counterName, 1.0, tags);
    }

    public void recordTime(String timerName, long amount, TimeUnit timeUnit, Tag... tags) {
        initMeterRegistry();

        List<Tag> tagsList = Arrays.asList(tags);
        String cacheKey = timerName + tagsList;
        Timer cachedMethodTimer = this.timersCache.get(cacheKey);

        if (cachedMethodTimer == null) {
            cachedMethodTimer = this.meterRegistry.timer(METRICS_NAME_PREFIX + timerName, tagsList);
            this.timersCache.put(cacheKey, cachedMethodTimer);
        }

        cachedMethodTimer.record(amount, timeUnit);
    }

    public Timer registerNewTimer(Timer.Builder timerBuilder) {
        initMeterRegistry();
        return timerBuilder.register(this.meterRegistry);
    }
}