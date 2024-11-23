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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Oracle implementation based on CoinPaprika.
 * See <a href="https://api.coinpaprika.com/">API Documentation</a>
 */
@Component
public class CoinPaprikaOracle implements ICardanoOracle, Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CoinPaprikaOracle.class);
    private static final String CARDANO_PRICE_DOLLAR_ENDPOINT = "https://api.coinpaprika.com/v1/coins/ada-cardano/markets?quotes=USD";
    private static final Long CARDANO_PRICE_MAX_AGE = 3600000L; // 1h

    private static final List<String> SELECTED_EXCHANGES_IDS = List.of("coinbase", "kraken", "binance-us", "okx", "bibox", "coinex");
    private static final List<String> SELECTED_PAIRS = List.of("ADA/USD", "ADA/USDT", "ADA/USDC");
    private WebClient webClient;
    private AtomicReference<Double> latestCardanoPrice;
    private AtomicLong latestCardanoPriceUpdateTimestamp;

    private ScheduledExecutorService scheduledExecutorService;

    @PostConstruct
    public void post() {
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
                new CustomizableThreadFactory("CoinPaprikaThread"));
        this.scheduledExecutorService.scheduleWithFixedDelay(this, 1, 900, TimeUnit.SECONDS);

        LOG.info("CoinPaprika Oracle created");
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
            List<Map<String, Object>> listData = (List<Map<String, Object>>) data;

            if (listData == null) return;

            List<Map<String, Object>> selectedExchanges = listData.stream()
                    .filter(q -> SELECTED_EXCHANGES_IDS.contains(q.get("exchange_id")) && SELECTED_PAIRS.contains(q.get("pair")))
                    .collect(Collectors.toList());

            double avgPrice = 0.0;
            int found = 0;
            for (Map<String, Object> selectedExchange : selectedExchanges) {
                Map<String, Object> quotes = (Map<String, Object>) selectedExchange.get("quotes");
                if (quotes == null)
                    continue;
                Map<String, Double> priceQuotes = (Map<String, Double>) quotes.get("USD");
                if (priceQuotes == null)
                    continue;
                if (priceQuotes.containsKey("price")) {
                    avgPrice += priceQuotes.get("price");
                    found++;
                }
            }

            if (found > 0) {
                avgPrice = avgPrice / found;
                LOG.debug("Computing ADA price using {} quotes from {} exchanges. Price {}",
                        found, selectedExchanges.size(), avgPrice);
            } else {
                LOG.warn("Could not find any ADA/USD* price from the data retrieved");
                return;
            }

            this.latestCardanoPrice.set(avgPrice);
            this.latestCardanoPriceUpdateTimestamp.set(System.currentTimeMillis());
            LOG.debug("Got new Cardano price {} USD", this.latestCardanoPrice.get());
        } catch (WebClientRequestException e) {
            LOG.warn("CoinPaprika REST call exception {}", e, e);
        } catch (Exception e) {
            LOG.error("Unexpected error while getting the cardano price form CoinPaprika", e);
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
