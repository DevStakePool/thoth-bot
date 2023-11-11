package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.UTxO;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.network.model.Tip;
import rest.koios.client.backend.api.transactions.model.TxIO;
import rest.koios.client.backend.api.transactions.model.TxInfo;
import rest.koios.client.backend.factory.options.*;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Component
@ConfigurationProperties("thoth.dapps")
public class TransactionCheckerTaskV2 extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionCheckerTaskV2.class);
    private static final String DELEGATION_CERTIFICATE = "delegation";
    private static final String BLOCK_HEIGHT_FIELD = "block_height";
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
        Iterator<List<User>> batchIterator = batches(userDao.getUsers(), USERS_BATCH_SIZE).iterator();

        while (batchIterator.hasNext()) {
            List<User> usersBatch = batchIterator.next();
            List<User> stakeUsersBatch = usersBatch.stream().filter(User::isStakeAddress).collect(Collectors.toList());
            List<User> addrUsersBatch = usersBatch.stream().filter(User::isNormalAddress).collect(Collectors.toList());

            LOG.debug("Processing users batch size {}, stake batch {}, address batch{}", usersBatch.size(), stakeUsersBatch.size(), addrUsersBatch.size());

            processUsersBatch(stakeUsersBatch, addrUsersBatch);

        }

    }

    private void processUsersBatch(List<User> stakeUsersBatch, List<User> addrUsersBatch) {
        try {
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
                    // FIXME throw exception here
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
                    // FIXME throw exception here
                }

                for (UTxO uTxO : resp.getValue()) {
                    addressesUtxOs.computeIfAbsent(uTxO.getAddress(), u -> new ArrayList<>()).add(uTxO);
                }
            } while (resp.isSuccessful() && !resp.getValue().isEmpty());

            // Eventually it can be parallelized
            for (User u : stakeUsersBatch) {
                if (addressesUtxOs.containsKey(u.getAddress())) {
                    processUserTxs(u, addressesUtxOs.get(u.getAddress()), chainTipResp.getValue().getBlockNo());
                }
            }

            for (User u : addrUsersBatch) {
                if (addressesUtxOs.containsKey(u.getAddress())) {
                    processUserTxs(u, addressesUtxOs.get(u.getAddress()), chainTipResp.getValue().getBlockNo());
                }
            }
        } catch (ApiException e) {
            //TODO
        }
    }

    private void processUserTxs(User user, List<UTxO> uTxOS, Integer blockNo) throws ApiException {
        // Cleanup any eventual old TX. This is due to the fact that we collected TXs from other users too, in the same Koios call
        uTxOS.removeIf(utxo -> utxo.getBlockHeight() <= user.getLastBlockHeight());

        if (uTxOS.isEmpty()) {
            LOG.debug("No new TX found for user {} with block height {}. Updating the user with the last tip {}",
                    user.getAddress(), user.getLastBlockHeight(), blockNo);
            this.userDao.updateUserBlockHeight(user.getId(), blockNo);
            return;
        }

        // Get all UTxOs TX hashes
        List<String> allTxHashes = uTxOS.stream().map(UTxO::getTxHash).collect(Collectors.toList());
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

        for (TxInfo txInfo : txInfoResp.getValue()) {
            processTxForUser(txInfo, user);
        }
    }

    private void processTxForUser(TxInfo txInfo, User user) {
        // check input and output of the TX to determine the nature of the TX itself
        Set<String> allInputAddresses = txInfo.getInputs().stream().map(tx -> tx.getStakeAddr() != null ? tx.getStakeAddr() : tx.getPaymentAddr().getBech32()).collect(Collectors.toSet());
        Set<String> allOutputAddresses = txInfo.getOutputs().stream().map(tx -> tx.getStakeAddr() != null ? tx.getStakeAddr() : tx.getPaymentAddr().getBech32()).collect(Collectors.toSet());
        TxType txType = null;
        if (allInputAddresses.size() == 1 && allOutputAddresses.size() == 1 &&
                allInputAddresses.contains(user.getAddress()) && allOutputAddresses.contains(user.getAddress()))
            txType = TxType.TX_INTERNAL;
        else if (!allInputAddresses.contains(user.getAddress()))
            txType = TxType.TX_RECEIVED;
        else
            txType = TxType.TX_SENT;

        LOG.debug("User {} TX {} is of type {}",
                user.getAddress(), txInfo.getTxHash(), txType);


    }

    public <T> Stream<List<T>> batches(List<T> source, int length) {
        if (length <= 0) throw new IllegalArgumentException("length cannot be negative, length=" + length);
        int size = source.size();
        if (size == 0) return Stream.empty();

        int fullChunks = (size - 1) / length;
        return IntStream.range(0, fullChunks + 1).mapToObj(n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length));
    }

    public Map<String, String> getContracts() {
        return contracts;
    }

    public void setContracts(Map<String, String> contracts) {
        this.contracts = contracts;
    }
}
