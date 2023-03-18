package com.devpool.thothBot.oracle;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Oracle implementation based on CoinGecko.
 * See <a href="https://www.coingecko.com/en/api/documentation">API Documentation</a>
 */
@Component
public class CoinGeckoCardanoOracle implements ICardanoOracle, Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CoinGeckoCardanoOracle.class);
    private static final String CARDANO_PRICE_DOLLAR_ENDPOINT = "https://api.coingecko.com/api/v3/simple/price?ids=cardano&vs_currencies=usd&precision=4";
    private static final Long CARDANO_PRICE_MAX_AGE = 3600000L; // 1h

    private WebClient webClient;
    private AtomicReference<Double> latestCardanoPrice;
    private AtomicLong latestCardanoPriceUpdateTimestamp;

    private ScheduledExecutorService scheduledExecutorService;

    @PostConstruct
    public void post() throws Exception {
        this.latestCardanoPrice = new AtomicReference<>();
        this.latestCardanoPriceUpdateTimestamp = new AtomicLong(-1);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofMillis(10000))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(10000, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(10000, TimeUnit.MILLISECONDS)));

        this.webClient = WebClient.builder()
                .baseUrl(CARDANO_PRICE_DOLLAR_ENDPOINT)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();


        this.scheduledExecutorService = Executors.newScheduledThreadPool(1,
                new CustomizableThreadFactory("CoinGeckoOracleExecutor"));
        this.scheduledExecutorService.scheduleWithFixedDelay(this, 1, 60, TimeUnit.SECONDS);

        LOG.info("CoinGecko Oracle created");
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down executor services");
        this.scheduledExecutorService.shutdown();
    }

    @Override
    public void run() {
        try {
            Mono<Object> response = webClient.get()
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Object.class);
            Object data = response.block();
            Map<String, Object> mapData = (Map<String, Object>) data;
            Map<String, Object> priceMap = (Map<String, Object>) mapData.get("cardano");
            Double usdPrice = (Double) priceMap.get("usd");
            this.latestCardanoPrice.set(usdPrice);
            this.latestCardanoPriceUpdateTimestamp.set(System.currentTimeMillis());
            LOG.debug("Got new Cardano price {} USD", this.latestCardanoPrice.get());
        } catch (WebClientRequestException e) {
            LOG.warn("CoinGecko REST call exception {}", e);
        } catch (Exception e) {
            LOG.error("Unexpected error while getting the cardano price form CoinGecko", e);
        }
    }

    @Override
    public Double getPriceUsd() {
        if (Math.abs(this.latestCardanoPriceUpdateTimestamp.get() - System.currentTimeMillis()) > CARDANO_PRICE_MAX_AGE) {
            LOG.warn("The Cardano price {} is older than 1h ({} ms) and therefore not considered valid",
                    this.latestCardanoPrice.get(), CARDANO_PRICE_MAX_AGE);
            return null;
        }
        return this.latestCardanoPrice.get();
    }
}
