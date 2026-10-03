package com.devpool.thothBot.koios;

import com.devpool.thothBot.monitoring.MetricsHelper;
import io.micrometer.core.instrument.Tag;
import rest.koios.client.backend.factory.BackendService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps a {@link BackendService} and counts every Koios API call, tagged by service (account, address, ...) and
 * method. It also tracks the highest number of calls seen in any rolling {@value PeakWindow#WINDOW_SECONDS} seconds
 * window, which is the unit used by the Koios tiers rate limits.
 */
public final class CountingBackendService {
    public static final String CALLS_COUNTER = "koios_api_calls";
    public static final String PEAK_GAUGE = "koios_api_peak_hits_per_10s";
    public static final String TAG_SERVICE = "service";
    public static final String TAG_METHOD = "method";

    private CountingBackendService() {
    }

    public static BackendService wrap(BackendService delegate, MetricsHelper metricsHelper) {
        return wrap(delegate, metricsHelper, new PeakWindow());
    }

    static BackendService wrap(BackendService delegate, MetricsHelper metricsHelper, PeakWindow peakWindow) {
        Map<String, Object> subServices = new ConcurrentHashMap<>();
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
                return invoke(method, delegate, args);

            // BackendService only exposes getXxxService() methods returning the sub-services
            Object subService = invoke(method, delegate, args);
            if (subService == null || !method.getReturnType().isInterface())
                return subService;

            String serviceName = serviceName(method.getName());
            return subServices.computeIfAbsent(serviceName,
                    k -> wrapSubService(subService, method.getReturnType(), serviceName, metricsHelper, peakWindow));
        };

        return (BackendService) Proxy.newProxyInstance(BackendService.class.getClassLoader(),
                new Class<?>[]{BackendService.class}, handler);
    }

    private static Object wrapSubService(Object subService, Class<?> serviceInterface, String serviceName,
                                         MetricsHelper metricsHelper, PeakWindow peakWindow) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() != Object.class) {
                metricsHelper.incrementCounter(CALLS_COUNTER,
                        Tag.of(TAG_SERVICE, serviceName), Tag.of(TAG_METHOD, method.getName()));
                long peak = peakWindow.hit(System.currentTimeMillis());
                metricsHelper.hitGauge(PEAK_GAUGE, peak);
            }
            return invoke(method, subService, args);
        };

        return Proxy.newProxyInstance(serviceInterface.getClassLoader(), new Class<?>[]{serviceInterface}, handler);
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause(); // Let the caller see the original exception, e.g. ApiException
        }
    }

    /**
     * getAccountService -> account
     */
    static String serviceName(String methodName) {
        String name = methodName.startsWith("get") ? methodName.substring(3) : methodName;
        if (name.endsWith("Service"))
            name = name.substring(0, name.length() - "Service".length());
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Counts the hits per second in a ring of buckets and remembers the highest total of the last
     * {@value WINDOW_SECONDS} seconds ever seen.
     */
    static final class PeakWindow {
        static final int WINDOW_SECONDS = 10;

        private final long[] bucketSecond = new long[WINDOW_SECONDS];
        private final long[] bucketHits = new long[WINDOW_SECONDS];
        private long peak;

        synchronized long hit(long nowMillis) {
            long second = nowMillis / 1000;
            int idx = (int) (second % WINDOW_SECONDS);
            if (bucketSecond[idx] != second) {
                bucketSecond[idx] = second;
                bucketHits[idx] = 0;
            }
            bucketHits[idx]++;

            long total = 0;
            for (int i = 0; i < WINDOW_SECONDS; i++) {
                if (second - bucketSecond[i] < WINDOW_SECONDS)
                    total += bucketHits[i];
            }
            peak = Math.max(peak, total);
            return peak;
        }
    }
}
