package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.KoiosResponseException;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.Asset;
import rest.koios.client.backend.api.base.common.UTxO;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.network.model.Tip;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.api.transactions.model.*;
import rest.koios.client.backend.factory.options.*;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties("thoth.dapps")
public class TransactionCheckerTaskV2 extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionCheckerTaskV2.class);
    private static final String DELEGATION_CERTIFICATE = "delegation";
    private static final String BLOCK_HEIGHT_FIELD = "block_height";
    private static final int MAX_TX_IN_TELEGRAM_NOTIFICATION = 3;
    @Value("${thoth.test.allow-jumbo-message}")
    private Boolean allowJumboMessage;

    private Map<String, String> contracts;

    @Autowired
    private TelegramFacade telegramFacade;

    @Autowired
    private AssetFacade assetFacade;

    private final Timer performanceSampler = new Timer("Transaction Checker Sampler", true);

    @Autowired
    private MetricsHelper metricsHelper;

    public enum TxType {
        TX_RECEIVED("Received"), TX_SENT("Sent"), TX_INTERNAL("Internal");

        private String humanReadableText;

        public String getHumanReadableText() {
            return this.humanReadableText;
        }

        TxType(String humanReadableText) {
            this.humanReadableText = humanReadableText;
        }
    }

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
            long subscriptionsCounter = this.userDao.countSubscriptions();
            long uniqueUsersCounter = this.userDao.countUniqueUsers();
            long assetsCacheCounter = this.assetFacade.countTotalCachedAssets();

            // Update gauge metric
            this.metricsHelper.hitGauge("total_subscriptions", subscriptionsCounter);
            this.metricsHelper.hitGauge("cached_assets", assetsCacheCounter);
            this.metricsHelper.hitGauge("unique_users", uniqueUsersCounter);
            LOG.trace("Calculated new gauge sample for TX processing: {} subscription(s), {} cached asset(s), {} user(s)",
                    subscriptionsCounter, assetsCacheCounter, uniqueUsersCounter);
        }
    }

    @PreDestroy
    public void preDestroy() {
        this.performanceSampler.cancel();
    }

    @Override
    public void run() {
        LOG.info("Checking activities for {} wallets", this.userDao.getUsers().size());
        Iterator<List<User>> batchIterator = CollectionsUtil.batchesList(userDao.getUsers(), this.usersBatchSize).iterator();

        while (batchIterator.hasNext()) {
            List<User> usersBatch = batchIterator.next();
            List<User> addrUsersBatch = usersBatch.stream().filter(User::isNormalAddress).collect(Collectors.toList());
            List<User> stakeUsersBatch = usersBatch.stream().filter(User::isStakeAddress).collect(Collectors.toList());

            LOG.debug("Processing users batch size {}, stake batch {}, address batch{}",
                    usersBatch.size(), stakeUsersBatch.size(), addrUsersBatch.size());
            try {
                processUsersBatch(stakeUsersBatch, addrUsersBatch);
            } catch (Exception e) {
                LOG.error("Error while processing user batch", e);
            }
        }
    }

    private void processUsersBatch(List<User> stakeUsersBatch, List<User> addrUsersBatch) throws KoiosResponseException, ApiException {
        // get the network last tip
        Result<Tip> chainTipResp = this.koiosFacade.getKoiosService().getNetworkService().getChainTip();
        if (!chainTipResp.isSuccessful()) {
            LOG.error("Could not get the chain tip for the processing of the batch user. Code {}, Response {}",
                    chainTipResp.getCode(), chainTipResp.getResponse());
            return;
        }

        // Among this batch of users, get the smallest block height. Old TXs will be filtered via software later to avoid
        // duplication of notifications
        Optional<User> lowestBlockHeightForStake = stakeUsersBatch.stream().min(Comparator.comparing(User::getLastBlockHeight));
        Optional<User> lowestBlockHeightForAddr = addrUsersBatch.stream().min(Comparator.comparing(User::getLastBlockHeight));

        // address -> list of UTxOS
        Map<String, List<UTxO>> addressesUtxOs = new HashMap<>();

        Result<List<UTxO>> resp;
        long offset = 0;
        // First staking addresses
        do {
            int blockHeight = 0;
            if (lowestBlockHeightForStake.isPresent())
                blockHeight = lowestBlockHeightForStake.get().getLastBlockHeight();

            Options options = Options.builder()
                    .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                    .option(Offset.of(offset)).
                    option(Filter.of(BLOCK_HEIGHT_FIELD, FilterType.GT, Integer.toString(blockHeight)))
                    .option(Order.by(BLOCK_HEIGHT_FIELD, SortType.DESC)).build();
            offset += DEFAULT_PAGINATION_SIZE;

            // Retrieve all UTXOs
            resp = this.koiosFacade.getKoiosService().getAccountService().getAccountUTxOs(
                    stakeUsersBatch.stream().map(User::getAddress).collect(Collectors.toList()), false, options);

            if (!resp.isSuccessful()) {
                LOG.warn("Failed to retrieve staking address UTXOs. Code {}, Response {}",
                        resp.getCode(), resp.getResponse());
                throw new KoiosResponseException(String.format("Failed to retrieve staking address UTXOs. Code %d, Response %s",
                        resp.getCode(), resp.getResponse()));
            }

            for (UTxO uTxO : resp.getValue()) {
                addressesUtxOs.computeIfAbsent(uTxO.getStakeAddress(), u -> new ArrayList<>()).add(uTxO);
            }
        } while (resp.isSuccessful() && !resp.getValue().isEmpty());

        // Same for the normal address
        offset = 0;
        do {
            int blockHeight = 0;
            if (lowestBlockHeightForAddr.isPresent())
                blockHeight = lowestBlockHeightForAddr.get().getLastBlockHeight();

            Options options = Options.builder()
                    .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                    .option(Offset.of(offset))
                    .option(Filter.of(BLOCK_HEIGHT_FIELD, FilterType.GT, Integer.toString(blockHeight)))
                    .option(Order.by(BLOCK_HEIGHT_FIELD, SortType.DESC)).build();
            offset += DEFAULT_PAGINATION_SIZE;

            // Retrieve all UTXOs
            resp = this.koiosFacade.getKoiosService().getAddressService().getAddressUTxOs(
                    addrUsersBatch.stream().map(User::getAddress).collect(Collectors.toList()), false, options);

            if (!resp.isSuccessful()) {
                LOG.warn("Failed to retrieve normal address UTXOs. Code {}, Response {}",
                        resp.getCode(), resp.getResponse());
                throw new KoiosResponseException(String.format("Failed to retrieve normal address UTXOs. Code %d, Response %s",
                        resp.getCode(), resp.getResponse()));
            }

            for (UTxO uTxO : resp.getValue()) {
                addressesUtxOs.computeIfAbsent(uTxO.getAddress(), u -> new ArrayList<>()).add(uTxO);
            }
        } while (resp.isSuccessful() && !resp.getValue().isEmpty());

        // Get ADA Handles
        List<String> userAddresses = stakeUsersBatch.stream().map(User::getAddress).collect(Collectors.toList());
        userAddresses.addAll(addrUsersBatch.stream().map(User::getAddress).collect(Collectors.toList()));
        Map<String, String> handles = getAdaHandleForAccount(userAddresses.toArray(new String[0]));

        // Eventually it can be parallelized
        for (User u : stakeUsersBatch) {
            if (addressesUtxOs.containsKey(u.getAddress())) {
                processUserTxs(u, addressesUtxOs.get(u.getAddress()), chainTipResp.getValue().getBlockNo(), handles);
            }
        }

        for (User u : addrUsersBatch) {
            if (addressesUtxOs.containsKey(u.getAddress())) {
                processUserTxs(u, addressesUtxOs.get(u.getAddress()), chainTipResp.getValue().getBlockNo(), handles);
            }
        }
    }

    private void processUserTxs(User user, List<UTxO> uTxOS, Integer blockNo, Map<String, String> handles) throws ApiException {
        try {
            // Cleanup any eventual old TX. This is due to the fact that we collected TXs from other users too, in the same Koios call
            uTxOS.removeIf(utxo -> utxo.getBlockHeight() <= user.getLastBlockHeight());

            if (uTxOS.isEmpty()) {
                LOG.debug("No new TX found for user {} with last block height {}. Updating the user {} with the last block height from the tip {}",
                        user.getAddress(), user.getLastBlockHeight(), user.getId(), blockNo);
                this.userDao.updateUserBlockHeight(user.getId(), blockNo);
                return;
            }

            // Get all UTxOs TX hashes
            List<String> allTxHashes = uTxOS.stream().map(UTxO::getTxHash).distinct().collect(Collectors.toList());
            LOG.debug("Getting TX information for {} TX(s), for a the user with address {}",
                    allTxHashes.size(), user.getAddress());
            if (LOG.isTraceEnabled())
                LOG.trace("Getting TX information for the following TXs {}", allTxHashes);
            // No need to do multi queries here unless you got 1000+ transactions since the last check
            Options options = Options.builder().option(Limit.of(DEFAULT_PAGINATION_SIZE)).option(Offset.of(0)).build();
            Result<List<TxInfo>> txInfoResp = this.koiosFacade.getKoiosService().getTransactionsService().getTransactionInformation(allTxHashes, options);

            if (!txInfoResp.isSuccessful()) {
                LOG.error("Cannot get the TXs information (total of {} UTXOs) for the user {} using block height {}",
                        uTxOS.size(), user.getAddress(), blockNo);
                return;
            }
            LOG.debug("Got all TXs {} for the user {}",
                    txInfoResp.getValue().size(), user.getAddress());

            List<StringBuilder> txBuilders = new ArrayList<>();
            for (TxInfo txInfo : txInfoResp.getValue()) {
                StringBuilder sb = processTxForUser(txInfo, user);
                txBuilders.add(sb);
            }

            if (!txInfoResp.getValue().isEmpty()) {
                // update the highest block for the user
                Optional<TxInfo> maxBlockHeight = txInfoResp.getValue().stream().max(Comparator.comparing(TxInfo::getBlockHeight));

                if (maxBlockHeight.isPresent()) {
                    // Update the user with the new block height plus 1 to avoid picking the last TX
                    this.userDao.updateUserBlockHeight(user.getId(), maxBlockHeight.get().getBlockHeight() + 1);
                    LOG.debug("Updated last block height to {} for user {}",
                            maxBlockHeight.get().getBlockHeight() + 1, user.getId());
                } else {
                    LOG.error("Can't find max block height among {} TX(s) for user {}",
                            txInfoResp.getValue().size(), user.getId());
                }
            }

            // compose telegram messages and send them for the user
            notifyTelegramUser(txBuilders, user, handles);
        } catch (Exception e) {
            LOG.error("Exception while processing {} TX(s) for the user {}",
                    uTxOS.size(), user, e);
        }
    }

    private StringBuilder processTxForUser(TxInfo txInfo, User user) {
        // check input and output of the TX to determine the nature of the TX itself
        Set<String> allInputAddresses = txInfo.getInputs().stream().map(tx -> tx.getStakeAddr() != null ? tx.getStakeAddr() : tx.getPaymentAddr().getBech32()).collect(Collectors.toSet());
        Set<String> allOutputAddresses = txInfo.getOutputs().stream().map(tx -> tx.getStakeAddr() != null ? tx.getStakeAddr() : tx.getPaymentAddr().getBech32()).collect(Collectors.toSet());

        TxType txType;
        if (allInputAddresses.size() == 1 && allOutputAddresses.size() == 1 &&
                allInputAddresses.contains(user.getAddress()) && allOutputAddresses.contains(user.getAddress()))
            txType = TxType.TX_INTERNAL;
        else if (!allInputAddresses.contains(user.getAddress()))
            txType = TxType.TX_RECEIVED;
        else
            txType = TxType.TX_SENT;

        LOG.debug("User {} TX {} is of type {}",
                user.getAddress(), txInfo.getTxHash(), txType);

        Double fee = Long.parseLong(txInfo.getFee()) / LOVELACE;

        double totalWithdrawals = 0d;
        if (txInfo.getWithdrawals() != null && !txInfo.getWithdrawals().isEmpty()) {
            for (TxWithdrawal withdrawal : txInfo.getWithdrawals()) {
                // Let's check if the withdrawals was for us
                if (withdrawal.getStakeAddr().equals(user.getAddress()))
                    totalWithdrawals += Long.parseLong(withdrawal.getAmount()) / LOVELACE;
            }
            LOG.debug("Found {} ADA withdrawal for TX {}", totalWithdrawals, txInfo.getTxHash());

        }

        // We check the inputs and outputs
        List<TxIO> accountOutputs = Collections.emptyList();
        List<TxIO> accountInputs = txInfo.getInputs().stream()
                .filter(tx -> user.getAddress().equals(tx.getStakeAddr()) || user.getAddress().equals(tx.getPaymentAddr().getBech32()))
                .collect(Collectors.toList());

        switch (txType) {
            case TX_INTERNAL: {
                accountInputs = Collections.emptyList();
                break;
            }
            case TX_RECEIVED: {
                accountOutputs = txInfo.getOutputs().stream()
                        .filter(tx -> user.getAddress().equals(tx.getStakeAddr()) || user.getAddress().equals(tx.getPaymentAddr().getBech32()))
                        .collect(Collectors.toList());
                break;
            }
            case TX_SENT: {
                accountOutputs = txInfo.getOutputs().stream()
                        .filter(tx -> !user.getAddress().equals(tx.getStakeAddr()) && !user.getAddress().equals(tx.getPaymentAddr().getBech32()))
                        .collect(Collectors.toList());
                break;
            }
        }

        // We need to check if there are new assets that we received, even if it's a "sent" TX
        // We make a diff between all input and output assets that belong to this account. The new ones are the received new assets
        Set<Asset> inputAssets = txInfo.getInputs().stream()
                .filter(tx -> user.getAddress().equals(tx.getStakeAddr()) || user.getAddress().equals(tx.getPaymentAddr().getBech32()))
                .flatMap(io -> io.getAssetList().stream()).collect(Collectors.toSet());
        Set<Asset> outputAssets = txInfo.getOutputs().stream()
                .filter(tx -> user.getAddress().equals(tx.getStakeAddr()) || user.getAddress().equals(tx.getPaymentAddr().getBech32()))
                .flatMap(io -> io.getAssetList().stream()).collect(Collectors.toSet());

        outputAssets.removeIf(a -> inputAssets.stream().map(Asset::getFingerprint).anyMatch(x -> x.equals(a.getFingerprint())));

        List<Asset> allAssets = accountOutputs.stream().flatMap(tx -> tx.getAssetList().stream()).collect(Collectors.toList());

        LOG.debug("All assets:\n{}\noutput assets:\n{}", allAssets, outputAssets);

        double receivedOrSentFunds = accountOutputs.stream().mapToLong(tx -> Long.parseLong(tx.getValue())).sum() / LOVELACE;

        // If it's a SENT funds you need to subtract the value of receivedOrSentFunds to the sub of the input ones
        if (txType == TxType.TX_SENT && !accountInputs.isEmpty()) {
            List<TxIO> txOutputsBelongingToTheUser = txInfo.getOutputs().stream()
                    .filter(tx -> user.getAddress().equals(tx.getStakeAddr()) || user.getAddress().equals(tx.getPaymentAddr().getBech32()))
                    .collect(Collectors.toList());

            Double inputFunds = accountInputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
            Double outputFunds = txOutputsBelongingToTheUser.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
            receivedOrSentFunds = inputFunds - outputFunds - fee;
        }

        if (txType == TxType.TX_SENT) receivedOrSentFunds *= -1.0d;

        receivedOrSentFunds -= totalWithdrawals;

        // Check for certificates in case it's a delegation TX
        String delegateToPoolName = null;
        String delegateToPoolId = null;
        if (txType == TxType.TX_INTERNAL && txInfo.getCertificates() != null && !txInfo.getCertificates().isEmpty()) {
            LOG.debug("The TX {} has {} certificates", txInfo.getTxHash(), txInfo.getCertificates().size());
            Optional<TxCertificate> delegationCertOpt = txInfo.getCertificates().stream().filter(c -> c.getType().equals(DELEGATION_CERTIFICATE)).findFirst();
            if (delegationCertOpt.isEmpty())
                LOG.debug("None of the TX {} certificates are of type {}", txInfo.getTxHash(), DELEGATION_CERTIFICATE);
            else {
                Options options = Options.builder().option(Limit.of(DEFAULT_PAGINATION_SIZE)).option(Offset.of(0)).build();
                delegateToPoolId = delegationCertOpt.get().getInfo().getPoolIdBech32();
                LOG.debug("New delegation for TX {} on pool-id {}", txInfo.getTxHash(), delegateToPoolId);
                List<PoolInfo> poolInfoList = null;

                try {
                    Result<List<PoolInfo>> poolInfoRes = this.koiosFacade.getKoiosService().getPoolService().getPoolInformation(Arrays.asList(delegateToPoolId), options);
                    if (poolInfoRes.isSuccessful()) poolInfoList = poolInfoRes.getValue();
                    else LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());
                } catch (ApiException e) {
                    LOG.warn("Cannot retrieve pool information: {}", e, e);
                }

                delegateToPoolName = getPoolName(poolInfoList, delegateToPoolId);
            }
        }

        LOG.debug("fee={} ADA, {}={} ADA", fee, txType, receivedOrSentFunds);
        Double latestCardanoPriceUsd = this.oracle.getPriceUsd();

        // Check for any metadata worth showing
        String metadataMessage = null;
        if (txInfo.getMetadata() != null && !(txInfo.getMetadata() instanceof NullNode)) {
            JsonNode jsonMetadata = txInfo.getMetadata();
            // See if there's a "msg"
            JsonNode msgNode = jsonMetadata.findValue("msg");
            if (msgNode != null && msgNode.isArray()) {
                ArrayNode msgArray = (ArrayNode) msgNode;
                if (!msgArray.isEmpty())
                    metadataMessage = msgArray.get(0).asText();

            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("TX {} Amount {} Fees {} USD Price {} Pool Delegation {} ({}) Message {} Assets {}",
                    txInfo.getTxHash(), receivedOrSentFunds, fee, latestCardanoPriceUsd, delegateToPoolName, delegateToPoolId,
                    metadataMessage, allAssets.stream().map(Asset::getFingerprint).collect(Collectors.joining("\n")));
        }

        StringBuilder sb = new StringBuilder();
        renderSingleTransactionMessage(sb, txInfo, allAssets, outputAssets, txType, latestCardanoPriceUsd, fee,
                receivedOrSentFunds, delegateToPoolName, delegateToPoolId, metadataMessage, totalWithdrawals);
        return sb;
    }

    public void notifyTelegramUser(List<StringBuilder> txBuilders, User user, Map<String, String> handles) {
        Iterator<List<StringBuilder>> batches = CollectionsUtil.batchesList(txBuilders, MAX_TX_IN_TELEGRAM_NOTIFICATION).iterator();

        while (batches.hasNext()) {
            List<StringBuilder> batch = batches.next();
            StringBuilder messageBuilder = renderTransactionMessageHeader(user, handles, batch.size());
            for (StringBuilder m : batch) {
                if (messageBuilder.toString().length() >= MAX_MSG_PAYLOAD_SIZE) {
                    messageBuilder.delete(MAX_MSG_PAYLOAD_SIZE - 10, messageBuilder.length() - 1);
                    messageBuilder.append("\nmore...");
                    break;
                }
                messageBuilder.append(m.toString());
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Telegram message for chat-id {}: {}", user.getChatId(), messageBuilder);
            }

            // Notify the user
            this.telegramFacade.sendMessageTo(user.getChatId(), messageBuilder.toString());
        }
    }

    private StringBuilder renderTransactionMessageHeader(User u, Map<String, String> handles, int noTxs) {
        // Message header
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                .append(u.isStakeAddress() ? CARDANO_SCAN_STAKE_KEY : CARDANO_SCAN_ADDR_KEY)
                .append(u.getAddress()).append("\">")
                .append(handles.get(u.getAddress()))
                .append("</a>\n")
                .append(EmojiParser.parseToUnicode(":envelope: "))
                .append(noTxs)
                .append(" new transaction(s)\n\n");

        return messageBuilder;
    }

    private StringBuilder renderSingleTransactionMessage(StringBuilder messageBuilder, TxInfo txInfo,
                                                         List<Asset> allAssets, Set<Asset> outputAssets, TxType txType,
                                                         Double latestCardanoPriceUsd, Double fee, double receivedOrSentFunds,
                                                         String delegateToPoolName, String delegateToPoolId, String metadataMessage,
                                                         double totalWithdrawals) {
        String fundsTokenText = String.format("Funds %s", allAssets.isEmpty() ? "" : "and Tokens");
        if (!outputAssets.isEmpty())
            fundsTokenText = "Funds and Received Tokens";

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

        // TX header
        messageBuilder.append("<a href=\"")
                .append(CARDANO_SCAN_TX)
                .append(txInfo.getTxHash()).append("\">")
                .append(txType.getHumanReadableText())
                .append(" ")
                .append(fundsTokenText)
                .append("</a>")
                .append(" <i>")
                .append(TX_DATETIME_FORMATTER.format(LocalDateTime.ofEpochSecond(txInfo.getTxTimestamp(), 0, ZoneOffset.UTC)))
                .append("</i>")
                .append("\n")
                .append(EmojiParser.parseToUnicode(":small_blue_diamond:"))
                .append("Fee ")
                .append(String.format("%,.2f", fee))
                .append(ADA_SYMBOL);

        // USD value fees
        if (latestCardanoPriceUsd != null) {
            messageBuilder.append(" (").append(String.format("%,.2f $", fee * latestCardanoPriceUsd)).append(")");
        }

        // Received/Sent funds
        if (txType != TxType.TX_INTERNAL) {
            messageBuilder.append(EmojiParser.parseToUnicode("\n:small_blue_diamond:"))
                    .append(txType == TxType.TX_RECEIVED ? "Received " : "Sent ")
                    .append(String.format("%,.2f", receivedOrSentFunds)).append(ADA_SYMBOL);

            // USD value if any
            if (latestCardanoPriceUsd != null) {
                messageBuilder
                        .append(" (")
                        .append(String.format("%,.2f $", receivedOrSentFunds * latestCardanoPriceUsd))
                        .append(")");
            }
        }

        if (totalWithdrawals > 0) {
            // We got some withdrawals
            messageBuilder.append(EmojiParser.parseToUnicode("\n:small_red_triangle_down: Withdrawal "))
                    .append(String.format("%,.2f", totalWithdrawals)).append(ADA_SYMBOL);

            // USD value if any
            if (latestCardanoPriceUsd != null) {
                messageBuilder
                        .append(" (")
                        .append(String.format("%,.2f $", totalWithdrawals * latestCardanoPriceUsd))
                        .append(")");
            }
        }

        // Plutus contract?
        if (txInfo.getPlutusContracts() != null && !txInfo.getPlutusContracts().isEmpty()) {
            messageBuilder.append(EmojiParser.parseToUnicode("\n:page_with_curl: Plutus Contracts:"));

            for (TxPlutusContract plutusContract : txInfo.getPlutusContracts()) {
                messageBuilder.append(EmojiParser.parseToUnicode("\n\t:black_small_square:"));

                if (plutusContract.getAddress() != null && this.contracts.containsKey(plutusContract.getAddress())) {
                    messageBuilder.append(" [").append(this.contracts.get(plutusContract.getAddress())).append("] ");
                }

                messageBuilder.append(Boolean.TRUE.equals(plutusContract.getValidContract()) ? "Valid" : "Invalid")
                        .append(" with size ").append(plutusContract.getSize()).append(" byte(s) ");

            }
        }

        // delegation?
        if (delegateToPoolName != null && delegateToPoolId != null) {
            messageBuilder
                    .append(EmojiParser.parseToUnicode("\n:classical_building:"))
                    .append(" Delegated to ")
                    .append("<a href=\"")
                    .append(CARDANO_SCAN_STAKE_POOL)
                    .append(delegateToPoolId)
                    .append("\">")
                    .append(delegateToPoolName)
                    .append("</a>");
        }

        // Message on metadata?
        if (metadataMessage != null) {
            messageBuilder
                    .append(EmojiParser.parseToUnicode("\n:speech_balloon:"))
                    .append(metadataMessage);
        }

        // Any assets?
        allAssets.addAll(outputAssets);
        Collection<Asset> uniqueAssets = allAssets.stream().collect(Collectors.toMap(Asset::getFingerprint, a -> a, (a, b) -> a)).values();

        if (!uniqueAssets.isEmpty()) {
            for (Asset asset : uniqueAssets) {
                Object assetQuantity = null;
                String assetName = hexToAscii(asset.getAssetName(), asset.getPolicyId());
                try {
                    assetQuantity = this.assetFacade.getAssetQuantity(asset.getPolicyId(), asset.getAssetName(), Long.parseLong(asset.getQuantity()));
                    assetName = this.assetFacade.getAssetDisplayName(asset.getPolicyId(), asset.getAssetName());
                } catch (ApiException e) {
                    LOG.warn("Could not get the asset quantity for asset {}/{}: {}",
                            asset.getPolicyId(), asset.getAssetName(), e.toString());
                }

                messageBuilder
                        .append(EmojiParser.parseToUnicode("\n:small_orange_diamond:"))
                        .append(assetName).append(" ")
                        .append(this.assetFacade.formatAssetQuantity(assetQuantity));
            }
        }

        messageBuilder.append("\n\n"); // Some padding between TXs

        return messageBuilder;
    }

    public Map<String, String> getContracts() {
        return contracts;
    }

    public void setContracts(Map<String, String> contracts) {
        this.contracts = contracts;
    }
}
