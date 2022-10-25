package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.Asset;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.MaxRegistrationsExceededException;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.TxHash;
import rest.koios.client.backend.api.account.model.AccountAddress;
import rest.koios.client.backend.api.asset.model.AssetInformation;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.transactions.model.TxAsset;
import rest.koios.client.backend.api.transactions.model.TxIO;
import rest.koios.client.backend.api.transactions.model.TxInfo;
import rest.koios.client.backend.factory.options.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Component
public class TransactionCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionCheckerTask.class);
    private static final long DEFAULT_PAGINATION_SIZE = 1000;
    private static final double LOVELACE = 1000000.0;
    private static final String ADA_SYMBOL = " " + '\u20B3';
    private static final String CARDANO_SCAN_TX = "https://cardanoscan.io/transaction/";
    public static final String CARDANO_SCAN_STAKE_KEY = "https://cardanoscan.io/stakekey/";
    private static final int USERS_BATCH_SIZE = 50;
    private static final DateTimeFormatter TX_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy, hh:mm a");

    private final Timer performanceSampler = new Timer("Transaction Checker Sampler", true);
    private Instant lastSampleInstant;
    private long txCounter;
    private long usersCounter;

    @Value("${thoth.test-mode:false}")
    private Boolean testMode;

    @Autowired
    private MetricsHelper metricsHelper;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private TelegramFacade telegramFacade;

    @PostConstruct
    public void post() {
        // Create performance samples
        performanceSampler.schedule(new TimerTask() {
            @Override
            public void run() {
                sampleMetrics();
            }
        }, 1000, 5000);
    }

    private void sampleMetrics() {
        synchronized (this.performanceSampler) {
            Instant now = Instant.now();
            this.usersCounter = this.userDao.countUsers();
            if (this.lastSampleInstant == null) {
                this.lastSampleInstant = now;
                this.txCounter = 0;
            } else {
                long txCounterCurr = this.txCounter;
                this.txCounter = 0;
                int millis = (int) (now.toEpochMilli() - lastSampleInstant.toEpochMilli());
                lastSampleInstant = now;
                double txPerSec = (txCounterCurr / (millis / 1000.0));
                // Update gauge metric
                this.metricsHelper.hitGauge("tx_per_sec", (long) txPerSec);
                this.metricsHelper.hitGauge("total_users", usersCounter);
                LOG.trace("Calculated new gauge sample for TX processing: {} tx/sec, {} user(s)", txPerSec, usersCounter);
            }
        }
    }

    @PreDestroy
    public void preDestroy() {
        this.performanceSampler.cancel();
    }

    @Override
    public void run() {
        LOG.debug("Starting thread in {} mode", this.testMode ? "TEST" : "PRODUCTION");

        LOG.info("Checking activities for {} wallets", this.userDao.getUsers().size());

        try {
            Iterator<List<User>> batchIterator = batches(userDao.getUsers(), USERS_BATCH_SIZE).iterator();
            while (batchIterator.hasNext()) {
                List<User> usersBatch = batchIterator.next();
                LOG.debug("Processing users batch size {}", usersBatch.size());

                Result<List<AccountAddress>> accountAddrResult;
                long offset = 0;
                do {
                    Options options = options = Options.builder()
                            .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                            .option(Offset.of(offset))
                            .build();
                    offset += DEFAULT_PAGINATION_SIZE;

                    accountAddrResult = this.koiosFacade.getKoiosService().getAccountService().getAccountAddresses(
                            usersBatch.stream().map(u -> u.getStakeAddr()).collect(Collectors.toList()), null, options);
                    if (!accountAddrResult.isSuccessful()) {
                        LOG.warn("The call to get the account addresses for stake addresses {} was not successful due to {} ({})",
                                usersBatch.stream().map(u -> u.getStakeAddr()).collect(Collectors.toList()),
                                accountAddrResult.getResponse(), accountAddrResult.getCode());
                        continue;
                    }

                    // Attach the addresses to the corresponding user(s)
                    for (AccountAddress accountAddress : accountAddrResult.getValue()) {
                        usersBatch.stream().filter(u -> u.getStakeAddr().equals(accountAddress.getStakeAddress()))
                                .collect(Collectors.toList())
                                .forEach(u -> u.setAccountAddresses(accountAddress.getAddresses()));
                    }
                } while (accountAddrResult != null && accountAddrResult.isSuccessful() && !accountAddrResult.getValue().isEmpty());

                checkTransactionsForUsers(usersBatch);
            }
        } catch (Throwable t) {
            LOG.error("Caught throwable while checking wallet transaction", t);
        }
    }

    private void checkTransactionsForUsers(List<User> users) throws ApiException, MaxRegistrationsExceededException {
        LOG.debug("Checking transactions for batch of users {}", users.size());
        for (User u : users) {
            // Retrieve all TXs with pagination starting from the last block height
            Result<List<TxHash>> txResult = null;
            List<TxHash> allTx = new ArrayList<>();
            long offset = 0;
            do {
                Options options = Options.builder()
                        .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                        .option(Offset.of(offset))
                        .option(Order.by("block_height", SortType.DESC))
                        .build();
                offset += DEFAULT_PAGINATION_SIZE;

                txResult = this.koiosFacade.getKoiosService().getAddressService().getAddressTransactions(
                        u.getAccountAddresses(), u.getLastBlockHeight(), options);

                LOG.trace("TXs {} {}:  {}", txResult.getCode(), txResult.getResponse(), txResult.getValue());

                if (!txResult.isSuccessful()) {
                    LOG.warn("The call to get the transactions for user {} was not successful due to {} ({})",
                            u, txResult.getResponse(), txResult.getCode());
                    continue;
                }

                allTx.addAll(txResult.getValue());
            } while (txResult != null && txResult.isSuccessful() && !txResult.getValue().isEmpty());

            // update the highest block for the user
            Optional<TxHash> maxBlockHeight = allTx.stream().max((o1, o2) -> {
                if (o1.getBlockHeight() < o2.getBlockHeight()) return -1;
                if (o1.getBlockHeight() > o2.getBlockHeight()) return 1;
                return 0;
            });

            // Update metrics about TXs
            synchronized (this.performanceSampler) {
                this.txCounter += allTx.size();
            }

            // We have an empty result (no TX to process)
            if (maxBlockHeight.isEmpty() && !testMode) {
                // nothing to do for this user
                LOG.debug("Nothing to do for the user {}. No TXs found", u);
                continue;
            }

            // Now we need to analyse all the TXs and notify the user.
            // No need to do multi queries here unless you got 1000+ transactions since the last check
            Options options = Options.builder()
                    .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                    .option(Offset.of(0))
                    .build();

            Result<List<TxInfo>> txInfoResult = null;
            // Test data
            if (testMode) {
                List<String> testTxs;
                if (u.getStakeAddr().equals("stake1u8uekde7k8x8n9lh0zjnhymz66sqdpa0ms02z8cshajptac0d3j32")) {
                    testTxs = Arrays.asList("570d5996d8ac85f1f019a6ccb8b8b926b32839a59e58b6376dc62d78944a4501", // RECEIVED
                            "81dcc3b330aa8b24375232c8191702f6c34858e5004be9d7dbea29bc36f20e2b", // SENT
                            "43e091f2833595bab44516521e55e9967a603015ed539d4ba003b21b959de3a8", // SENT + Tokens
                            "73be7fb0d15a5d63a4d5e5ca10df55d9fcaba7a3b6a885acc3d79a0506d0a12b", // Received + Tokens
                            "ba38cd0fca387c28987696dff1af4545f9698cb9f184dcd7609112c4e185bae5", // Issue here to investigate
                            "91c38684180b6b0334583b86e90dc23e7c4309a409373840cc4b225bf619f47f", // Received + Tokens
                            "55b58c4b566fd02019967096db5e5b3a96922eb0bf419d386788516a0e2c9ff3");

                } else if (u.getStakeAddr().equals("stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz")) {
                    testTxs = Arrays.asList("a5383435fb2ccab887ad227cbcd084332e0c9e82594a1a0d128e9f64b22317ef",
                            "6c54b27ed8e102b8f0891c7e84d1587d7188cd43f79bcc7e2897a004dc9ac879");
                } else {
                    LOG.error("We have no test data for this stake address {}", u.getStakeAddr());
                    continue;
                }

                txInfoResult = this.koiosFacade.getKoiosService().getTransactionsService().getTransactionInformation(
                        testTxs, options);
                LOG.warn("TEST MODE transactions: {}", txInfoResult);

            } else {
                txInfoResult = this.koiosFacade.getKoiosService().getTransactionsService().getTransactionInformation(
                        allTx.stream().map(tx -> tx.getTxHash()).collect(Collectors.toList()), options);
            }

            if (!txInfoResult.isSuccessful()) {
                LOG.warn("The call to get the transaction information {} for user {} was not successful due to {} ({})",
                        allTx.stream().map(tx -> tx.getTxHash()).collect(Collectors.joining(",")),
                        u, txInfoResult.getResponse(), txInfoResult.getCode());
                continue;
            }

            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                    .append(CARDANO_SCAN_STAKE_KEY)
                    .append(u.getStakeAddr())
                    .append("\">")
                    .append(shortenStakeAddr(u.getStakeAddr()))
                    .append("</a>\n")
                    .append(EmojiParser.parseToUnicode(":envelope: "))
                    .append(txInfoResult.getValue().size())
                    .append(" new transaction(s)\n\n");

            if (txInfoResult.getValue().isEmpty()) {
                LOG.warn("TX Info empty. Probably db-sync did not complete adding this part. Let's try later");
                continue;
            }

            for (TxInfo txInfo : txInfoResult.getValue()) {
                // Understand if it's a reception or send by looking at the inputs
                boolean isReceiveTx = txInfo.getInputs().stream().filter(tx -> u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32())).count() == 0;
                LOG.trace("TX {}\n{}", isReceiveTx ? "RECEIVED FUNDS" : "SENT FUNDS", txInfo);
                Double fee = Long.valueOf(txInfo.getFee()) / LOVELACE;
                List<TxIO> accountOutputs = txInfo.getOutputs().stream().filter(
                                tx -> (isReceiveTx ?
                                        u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32()) :
                                        !u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32())))
                        .collect(Collectors.toList());
                List<TxIO> accountInputs = txInfo.getInputs().stream().filter(
                                tx -> u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32()))
                        .collect(Collectors.toList());
                List<TxAsset> allAssets = accountOutputs.stream().flatMap(tx -> tx.getAssetList().stream()).collect(Collectors.toList());

                LOG.debug("All assets:\n{}", allAssets);
                Double receivedOrSentFunds = accountOutputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;

                // If it's a SET funds you need to subtract the value of receivedOrSentFunds to the sub of the input ones
                if (!isReceiveTx && !accountInputs.isEmpty()) {
                    accountOutputs = txInfo.getOutputs().stream().filter(
                                    tx -> u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32()))
                            .collect(Collectors.toList());
                    Double inputFunds = accountInputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
                    Double outputFunds = accountOutputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
                    receivedOrSentFunds = inputFunds - outputFunds;
                }
                if (!isReceiveTx)
                    receivedOrSentFunds *= -1.0d;

                Date txTime = new Date(txInfo.getTxTimestamp());
                LOG.debug("fee={} ADA, {}}={} ADA", fee, (isReceiveTx ? "received" : "sent"), receivedOrSentFunds);
                String fundsTokenText = String.format("Funds %s ", allAssets.isEmpty() ? "" : " and Tokens");

                messageBuilder.append(EmojiParser.parseToUnicode(isReceiveTx ? ":arrow_heading_down: " : ":arrow_heading_up: "))
                        .append("<a href=\"").append(CARDANO_SCAN_TX).append(txInfo.getTxHash()).append("\">")
                        .append(isReceiveTx ? "Received " : "Sent ").append(fundsTokenText).append("</a>")
                        .append(" <i>")
                        .append(TX_DATETIME_FORMATTER.format(LocalDateTime.ofEpochSecond(txInfo.getTxTimestamp(), 0, ZoneOffset.UTC)))
                        .append("</i>")
                        .append("\n")
                        .append(EmojiParser.parseToUnicode(":small_blue_diamond:"))
                        .append("Fee ").append(String.format("%,.2f", fee)).append(ADA_SYMBOL)
                        .append(EmojiParser.parseToUnicode("\n:small_blue_diamond:"))
                        .append(isReceiveTx ? "Input " : "Output ").append(String.format("%,.2f", receivedOrSentFunds))
                        .append(ADA_SYMBOL);

                // Any assets?
                if (!allAssets.isEmpty()) {
                    for (TxAsset asset : allAssets) {
                        Optional<Asset> cachedAsset = this.assetsDao.getAssetInformation(asset.getPolicyId(), asset.getAssetName());
                        Object assetQuantity = Long.valueOf(asset.getQuantity());
                        if (cachedAsset.isEmpty()) {
                            // We need to get the decimals for the asset. Note, this will be cached
                            Result<AssetInformation> assetInfoResult = this.koiosFacade.getKoiosService().getAssetService().getAssetInformation(asset.getPolicyId(), asset.getAssetName());
                            if (!assetInfoResult.isSuccessful()) {
                                LOG.warn("Failed to retrieve asset {} information from KOIOS, due to {} ({})",
                                        asset.getPolicyId(), assetInfoResult.getResponse(), assetInfoResult.getCode());
                            }

                            if (assetInfoResult.isSuccessful() && assetInfoResult.getValue().getTokenRegistryMetadata() != null) {
                                assetQuantity = Long.valueOf(asset.getQuantity()) / (1.0 * Math.pow(10, assetInfoResult.getValue().getTokenRegistryMetadata().getDecimals()));
                            }

                            // Cache it for the future
                            this.assetsDao.addNewAsset(asset.getPolicyId(), asset.getAssetName(),
                                    assetInfoResult.getValue().getTokenRegistryMetadata() == null ? -1 :
                                            assetInfoResult.getValue().getTokenRegistryMetadata().getDecimals());
                        } else {
                            // We have it cached
                            if (cachedAsset.get().getDecimals() != -1)
                                assetQuantity = Long.valueOf(asset.getQuantity()) / (1.0 * Math.pow(10, cachedAsset.get().getDecimals()));
                        }

                        messageBuilder.append(EmojiParser.parseToUnicode("\n:small_orange_diamond:"))
                                .append(hexToAscii(asset.getAssetName()))
                                .append(" ")
                                .append(assetQuantity instanceof Double ?
                                        String.format("%,.2f", assetQuantity) :
                                        String.format("%,d", assetQuantity));
                    }
                }

                messageBuilder.append("\n\n"); // Some padding between TXs
            }

            this.telegramFacade.sendMessageTo(u.getChatId(), messageBuilder.toString());

            // Update the user with the new block height plus 1 to avoid picking the last TX
            if (!testMode)
                this.userDao.updateUserBlockHeight(u.getId(), maxBlockHeight.get().getBlockHeight() + 1);
        }
    }

    public static String shortenStakeAddr(String stakeAddr) {
        return "stake1u..." + stakeAddr.substring(stakeAddr.length() - 8);
    }

    private static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");

        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }

        return output.toString();
    }

    public <T> Stream<List<T>> batches(List<T> source, int length) {
        if (length <= 0)
            throw new IllegalArgumentException("length cannot be negative, length=" + length);
        int size = source.size();
        if (size <= 0)
            return Stream.empty();

        int fullChunks = (size - 1) / length;
        return IntStream.range(0, fullChunks + 1).mapToObj(
                n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length));
    }
}
