package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.MaxRegistrationsExceededException;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAddress;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.common.TxHash;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.api.transactions.model.TxCertificate;
import rest.koios.client.backend.api.transactions.model.TxIO;
import rest.koios.client.backend.api.transactions.model.TxInfo;
import rest.koios.client.backend.api.transactions.model.TxPlutusContract;
import rest.koios.client.backend.factory.options.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Component
public class TransactionCheckerTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionCheckerTask.class);
    private static final String DELEGATION_CERTIFICATE = "delegation";
    @Value("${thoth.test.allow-jumbo-message}")
    private Boolean allowJumboMessage;

    public enum TxType {
        TX_RECEIVED("Received"),
        TX_SENT("Sent"),
        TX_INTERNAL("Internal");

        private String humanReadableText;

        public String getHumanReadableText() {
            return this.humanReadableText;
        }

        TxType(String humanReadableText) {
            this.humanReadableText = humanReadableText;
        }
    }

    @Autowired
    private TelegramFacade telegramFacade;

    @Autowired
    private AssetFacade assetFacade;

    private final Timer performanceSampler = new Timer("Transaction Checker Sampler", true);
    private Instant lastSampleInstant;
    private long txCounter;
    private long usersCounter;
    private long assetsCacheCounter;

    @Autowired
    private MetricsHelper metricsHelper;

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
            this.assetsCacheCounter = this.assetFacade.countTotalCachedAssets();

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
                this.metricsHelper.hitGauge("total_users", this.usersCounter);
                this.metricsHelper.hitGauge("cached_assets", this.assetsCacheCounter);
                LOG.trace("Calculated new gauge sample for TX processing: {} tx/sec, {} user(s), {} cached asset(s)",
                        txPerSec, this.usersCounter, this.assetsCacheCounter);
            }
        }
    }

    @PreDestroy
    public void preDestroy() {
        this.performanceSampler.cancel();
    }

    @Override
    public void run() {
        LOG.info("Checking activities for {} wallets", this.userDao.getUsers().size());

        try {
            Iterator<List<User>> batchIterator = batches(userDao.getUsers(), USERS_BATCH_SIZE).iterator();
            while (batchIterator.hasNext()) {
                List<User> usersBatch = batchIterator.next();
                List<User> stakeUsersBatch = usersBatch.stream().filter(u -> u.isStakeAddress()).collect(Collectors.toList());
                List<User> addrUsersBatch = usersBatch.stream().filter(u -> !u.isStakeAddress()).collect(Collectors.toList());

                LOG.debug("Processing users batch size {}, stake batch {}, address batch{}",
                        usersBatch.size(), stakeUsersBatch.size(), addrUsersBatch.size());

                // Retrieve addresses from the stake users batches
                Result<List<AccountAddress>> accountAddrResult;
                long offset = 0;
                do {
                    Options options = Options.builder()
                            .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                            .option(Offset.of(offset))
                            .build();
                    offset += DEFAULT_PAGINATION_SIZE;

                    accountAddrResult = this.koiosFacade.getKoiosService().getAccountService().getAccountAddresses(
                            stakeUsersBatch.stream().map(u -> u.getAddress()).collect(Collectors.toList()), false, false, options);
                    if (!accountAddrResult.isSuccessful()) {
                        LOG.warn("The call to get the account addresses for stake addresses {} was not successful due to {} ({})",
                                stakeUsersBatch.stream().map(u -> u.getAddress()).collect(Collectors.toList()),
                                accountAddrResult.getResponse(), accountAddrResult.getCode());
                        continue;
                    }

                    // Attach the addresses to the corresponding user(s) (stake addr batch)
                    for (AccountAddress accountAddress : accountAddrResult.getValue()) {
                        stakeUsersBatch.stream().filter(u -> u.getAddress().equals(accountAddress.getStakeAddress()))
                                .collect(Collectors.toList())
                                .forEach(u -> u.setAccountAddresses(accountAddress.getAddresses()));
                    }

                    // Attach the single address
                    addrUsersBatch.forEach(au -> au.setAccountAddresses(List.of(au.getAddress())));

                } while (accountAddrResult != null && accountAddrResult.isSuccessful() && !accountAddrResult.getValue().isEmpty());

                checkTransactionsForUsers(usersBatch);
            }
        } catch (Throwable t) {
            LOG.error("Caught throwable while checking wallet transaction", t);
        }
    }

    private void checkTransactionsForUsers(List<User> users) throws ApiException, MaxRegistrationsExceededException {
        LOG.debug("Checking transactions for batch of users {}", users.size());

        // Get ADA Handles
        Map<String, String> handles = getAdaHandleForAccount(users.stream().map(User::getAddress).collect(Collectors.toList()).toArray(new String[0]));

        for (User u : users) {
            try {
                if (u.getAccountAddresses() == null)
                    continue; // The previous call failed, probably due to unavailability of Koios

                // Retrieve all TXs with pagination starting from the last block height
                Result<List<TxHash>> txResult;
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
                if (maxBlockHeight.isEmpty()) {
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

                Result<List<TxInfo>> txInfoResult = this.koiosFacade.getKoiosService().getTransactionsService().getTransactionInformation(
                        allTx.stream().map(tx -> tx.getTxHash()).collect(Collectors.toList()), options);

                if (!txInfoResult.isSuccessful()) {
                    LOG.warn("The call to get the transaction information {} for user {} was not successful due to {} ({})",
                            allTx.stream().map(tx -> tx.getTxHash()).collect(Collectors.joining(",")),
                            u, txInfoResult.getResponse(), txInfoResult.getCode());
                    continue;
                }

                StringBuilder messageBuilder = new StringBuilder();
                messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                        .append(u.isStakeAddress() ? CARDANO_SCAN_STAKE_KEY : CARDANO_SCAN_ADDR_KEY)
                        .append(u.getAddress())
                        .append("\">")
                        .append(handles.get(u.getAddress()))
                        .append("</a>\n")
                        .append(EmojiParser.parseToUnicode(":envelope: "))
                        .append(txInfoResult.getValue().size())
                        .append(" new transaction(s)\n\n");

                if (txInfoResult.getValue().isEmpty()) {
                    LOG.warn("TX Info empty. Probably db-sync did not complete adding this part. Let's try later");
                    continue;
                }

                int processed = 0;

                for (TxInfo txInfo : txInfoResult.getValue()) {
                    // Understand if it's a reception or send by looking at the inputs
                    // Check if it's an internal TX where all inputs and outputs belong to the user account
                    TxType txType;
                    boolean isInternalTx = u.getAccountAddresses().containsAll(txInfo.getInputs().stream().map(tx -> tx.getPaymentAddr().getBech32()).collect(Collectors.toList())) &&
                            u.getAccountAddresses().containsAll(txInfo.getOutputs().stream().map(tx -> tx.getPaymentAddr().getBech32()).collect(Collectors.toList()));
                    if (isInternalTx)
                        txType = TxType.TX_INTERNAL;
                    else if (txInfo.getInputs().stream().filter(tx -> u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32())).count() == 0)
                        txType = TxType.TX_RECEIVED;
                    else
                        txType = TxType.TX_SENT;

                    LOG.trace("Type {} \n {}", txType, txInfo);
                    Double fee = Long.valueOf(txInfo.getFee()) / LOVELACE;

                    List<TxIO> accountOutputs = null;
                    List<TxIO> accountInputs = txInfo.getInputs().stream()
                            .filter(tx -> u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32()))
                            .collect(Collectors.toList());

                    switch (txType) {
                        case TX_INTERNAL: {
                            accountOutputs = Collections.emptyList();
                            accountInputs = Collections.emptyList();
                            break;
                        }
                        case TX_RECEIVED: {
                            accountOutputs = txInfo.getOutputs().stream()
                                    .filter(tx -> (u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32())))
                                    .collect(Collectors.toList());
                            break;
                        }
                        case TX_SENT: {
                            accountOutputs = txInfo.getOutputs().stream()
                                    .filter(tx -> (!u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32())))
                                    .collect(Collectors.toList());
                            break;
                        }
                    }

                    List<rest.koios.client.backend.api.common.Asset> allAssets = accountOutputs.stream()
                            .flatMap(tx -> tx.getAssetList().stream()).collect(Collectors.toList());

                    LOG.debug("All assets:\n{}", allAssets);
                    Double receivedOrSentFunds = accountOutputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;

                    // If it's a SENT funds you need to subtract the value of receivedOrSentFunds to the sub of the input ones
                    if (txType == TxType.TX_SENT && !accountInputs.isEmpty()) {
                        accountOutputs = txInfo.getOutputs().stream().filter(
                                        tx -> u.getAccountAddresses().contains(tx.getPaymentAddr().getBech32()))
                                .collect(Collectors.toList());
                        Double inputFunds = accountInputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
                        Double outputFunds = accountOutputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
                        receivedOrSentFunds = inputFunds - outputFunds;
                    }
                    if (txType == TxType.TX_SENT)
                        receivedOrSentFunds *= -1.0d;

                    // Check for certificates in case it's a delegation TX
                    String delegateToPoolName = null;
                    String delegateToPoolId = null;
                    if (txType == TxType.TX_INTERNAL && txInfo.getCertificates() != null && !txInfo.getCertificates().isEmpty()) {
                        LOG.debug("The TX {} has {} certificates", txInfo.getTxHash(), txInfo.getCertificates().size());
                        Optional<TxCertificate> delegationCertOpt = txInfo.getCertificates().stream().filter(c -> c.getType().equals(DELEGATION_CERTIFICATE)).findFirst();
                        if (delegationCertOpt.isEmpty())
                            LOG.debug("None of the TX {} certificates are of type {}", txInfo.getTxHash(), DELEGATION_CERTIFICATE);
                        else {
                            delegateToPoolId = delegationCertOpt.get().getInfo().getPoolIdBech32();
                            LOG.debug("New delegation for TX {} on pool-id {}", txInfo.getTxHash(), delegateToPoolId);
                            List<PoolInfo> poolInfoList = null;

                            try {
                                Result<List<PoolInfo>> poolInfoRes = this.koiosFacade.getKoiosService().getPoolService().getPoolInformation(Arrays.asList(delegateToPoolId), options);
                                if (poolInfoRes.isSuccessful())
                                    poolInfoList = poolInfoRes.getValue();
                                else
                                    LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());
                            } catch (ApiException e) {
                                LOG.warn("Cannot retrieve pool information: {}", e);
                            }

                            delegateToPoolName = getPoolName(poolInfoList, delegateToPoolId);
                        }
                    }

                    LOG.debug("fee={} ADA, {}={} ADA", fee, txType, receivedOrSentFunds);
                    String fundsTokenText = String.format("Funds %s", allAssets.isEmpty() ? "" : "and Tokens");
                    switch (txType) {
                        case TX_RECEIVED:
                            messageBuilder.append(EmojiParser.parseToUnicode(":arrow_heading_down: "));
                            break;
                        case TX_SENT:
                            messageBuilder.append(EmojiParser.parseToUnicode(":arrow_heading_up: "));
                            break;
                        case TX_INTERNAL:
                            messageBuilder.append(EmojiParser.parseToUnicode(":repeat: "));
                            fundsTokenText = String.format("Transfer %s", allAssets.isEmpty() ? "" : "and Tokens");

                            break;
                    }
                    Double latestCardanoPriceUsd = this.oracle.getPriceUsd();
                    double usdVal = -1;

                    messageBuilder.append("<a href=\"").append(CARDANO_SCAN_TX).append(txInfo.getTxHash()).append("\">")
                            .append(txType.getHumanReadableText()).append(" ").append(fundsTokenText).append("</a>")
                            .append(" <i>")
                            .append(TX_DATETIME_FORMATTER.format(LocalDateTime.ofEpochSecond(txInfo.getTxTimestamp(), 0, ZoneOffset.UTC)))
                            .append("</i>")
                            .append("\n")
                            .append(EmojiParser.parseToUnicode(":small_blue_diamond:"))
                            .append("Fee ").append(String.format("%,.2f", fee)).append(ADA_SYMBOL);

                    // USD value fees
                    if (latestCardanoPriceUsd != null) {
                        messageBuilder.append(" (")
                                .append(String.format("%,.2f $", fee * latestCardanoPriceUsd))
                                .append(")");
                    }

                    if (txType != TxType.TX_INTERNAL) {
                        messageBuilder
                                .append(EmojiParser.parseToUnicode("\n:small_blue_diamond:"))
                                .append(txType == TxType.TX_RECEIVED ? "Input " : "Output ").append(String.format("%,.2f", receivedOrSentFunds))
                                .append(ADA_SYMBOL);

                        // USD value
                        if (latestCardanoPriceUsd != null) {
                            messageBuilder.append(" (")
                                    .append(String.format("%,.2f $", receivedOrSentFunds * latestCardanoPriceUsd))
                                    .append(")");
                        }
                    }

                    // Plutus contract?
                    if (txInfo.getPlutusContracts() != null && !txInfo.getPlutusContracts().isEmpty()) {
                        messageBuilder.append(EmojiParser.parseToUnicode("\n:page_with_curl: Plutus Contracts:"));

                        for (TxPlutusContract plutusContract : txInfo.getPlutusContracts()) {
                            messageBuilder
                                    .append(EmojiParser.parseToUnicode("\n\t:black_small_square:"))
                                    .append(plutusContract.getValidContract() ? "Valid" : "Invalid")
                                    .append(" with size ").append(plutusContract.getSize()).append(" byte(s) ");
                        }
                    }

                    // delegation?
                    if (delegateToPoolName != null && delegateToPoolId != null) {
                        messageBuilder.append(EmojiParser.parseToUnicode("\n:classical_building:"))
                                .append(" Delegated to ").append("<a href=\"").
                                append(CARDANO_SCAN_STAKE_POOL).append(delegateToPoolId).append("\">")
                                .append(delegateToPoolName).append("</a>");
                    }

                    // Any assets?
                    if (!allAssets.isEmpty()) {
                        for (rest.koios.client.backend.api.common.Asset asset : allAssets) {
                            Object assetQuantity = this.assetFacade.getAssetQuantity(
                                    asset.getPolicyId(), asset.getAssetName(), Long.parseLong(asset.getQuantity()));

                            messageBuilder.append(EmojiParser.parseToUnicode("\n:small_orange_diamond:"))
                                    .append(hexToAscii(asset.getAssetName()))
                                    .append(" ")
                                    .append(this.assetFacade.formatAssetQuantity(assetQuantity));
                        }
                    }

                    messageBuilder.append("\n\n"); // Some padding between TXs
                    processed++;

                    if (!this.allowJumboMessage && messageBuilder.toString().length() >= MAX_MSG_PAYLOAD_SIZE) {
                        messageBuilder.append("\n").append(txInfoResult.getValue().size() - processed).append(" more...");
                        break;
                    }
                }

                this.telegramFacade.sendMessageTo(u.getChatId(), messageBuilder.toString());

                // Update the user with the new block height plus 1 to avoid picking the last TX
                this.userDao.updateUserBlockHeight(u.getId(), maxBlockHeight.get().getBlockHeight() + 1);
            } catch (Throwable t) {
                LOG.error("Cannot process account {} due to exception {}", u.getAddress(), t, t);
            }
        }
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
