package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.Asset;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.TxHash;
import rest.koios.client.backend.api.account.model.AccountAddress;
import rest.koios.client.backend.api.asset.model.AssetInformation;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.transactions.model.TxAsset;
import rest.koios.client.backend.api.transactions.model.TxIO;
import rest.koios.client.backend.api.transactions.model.TxInfo;
import rest.koios.client.backend.factory.options.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TransactionCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionCheckerTask.class);
    private static final long DEFAULT_PAGINATION_SIZE = 1000;
    private static final double LOVELACE = 1000000.0;
    private static final String ADA_SYMBOL = " " + '\u20B3';
    private static final boolean TEST_DATA = true;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private TelegramFacade telegramFacade;

    @Override
    public void run() {
        try {
            LOG.info("Checking activities for {} wallets", this.userDao.getUsers().size());
            for (User u : userDao.getUsers()) {
                // Retrieve all the wallet addresses
                Options options = Options.builder()
                        .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                        .option(Offset.of(0))
                        .build();

                Result<List<AccountAddress>> addressesRes = this.koiosFacade.getKoiosService().getAccountService().getAccountAddresses(u.getStakeAddr(), options);
                if (!addressesRes.isSuccessful()) {
                    LOG.warn("Cannot fetch addresses for user {}" + u);
                    continue;
                }

                List<String> addresses = addressesRes.getValue().stream().map(a -> a.getAddress()).collect(Collectors.toList());

                // Retrieve all TXs with pagination starting from the last block height
                Result<List<TxHash>> txResult = null;
                List<TxHash> allTx = new ArrayList<>();
                long offset = 0;
                do {
                    options = Options.builder()
                            .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                            .option(Offset.of(offset))
                            .option(Order.by("block_height", SortType.DESC))
                            .build();
                    offset += DEFAULT_PAGINATION_SIZE;

                    txResult = this.koiosFacade.getKoiosService().getAddressService().getAddressTransactions(addresses, u.getLastBlockHeight(), options);
                    LOG.trace("TXs {} {}:  {}", txResult.getCode(), txResult.getResponse(), txResult.getValue());

                    if (!txResult.isSuccessful()) {
                        LOG.warn("The call to get the transactions for user {} was not successful due to {} ({})",
                                u, txResult.getResponse(), txResult.getCode());
                    }

                    allTx.addAll(txResult.getValue());
                } while (txResult != null && txResult.isSuccessful() && !txResult.getValue().isEmpty());

                // update the highest block for the user
                Optional<TxHash> maxBlockHeight = allTx.stream().max((o1, o2) -> {
                    if (o1.getBlockHeight() < o2.getBlockHeight()) return -1;
                    if (o1.getBlockHeight() > o2.getBlockHeight()) return 1;
                    return 0;
                });

                // We have an empty result (no TX to process)
                if (maxBlockHeight.isEmpty()) {
                    // nothing to do for this user
                    LOG.debug("Nothing to do for the user {}. No TXs found", u);
                    continue;
                }

                // Now we need to analyse all the TXs and notify the user.
                // No need to do multi queries here unless you got 1000+ transactions since the last check
                options = Options.builder()
                        .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                        .option(Offset.of(0))
                        .build();

                Result<List<TxInfo>> txInfoResult = null;
                // Test data
                if (TEST_DATA) {
                    List<String> testTxs = Arrays.asList("570d5996d8ac85f1f019a6ccb8b8b926b32839a59e58b6376dc62d78944a4501", // RECEIVED
                            "81dcc3b330aa8b24375232c8191702f6c34858e5004be9d7dbea29bc36f20e2b", // SENT
                            "43e091f2833595bab44516521e55e9967a603015ed539d4ba003b21b959de3a8", // SENT + Tokens
                            "73be7fb0d15a5d63a4d5e5ca10df55d9fcaba7a3b6a885acc3d79a0506d0a12b", // Received + Tokens
                            "ba38cd0fca387c28987696dff1af4545f9698cb9f184dcd7609112c4e185bae5", // Issue here to investigate
                            "91c38684180b6b0334583b86e90dc23e7c4309a409373840cc4b225bf619f47f"); // Received + Tokens
                    txInfoResult = this.koiosFacade.getKoiosService().getTransactionsService().getTransactionInformation(
                            testTxs, options);
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
                for (TxInfo txInfo : txInfoResult.getValue()) {
                    // Understand if it's a reception or send by looking at the inputs
                    boolean isReceiveTx = txInfo.getInputs().stream().filter(tx -> addresses.contains(tx.getPaymentAddr().getBech32())).count() == 0;
                    LOG.trace("TX {}\n{}", isReceiveTx ? "RECEIVED FUNDS" : "SENT FUNDS", txInfo);
                    Double fee = Long.valueOf(txInfo.getFee()) / LOVELACE;
                    List<TxIO> accountOutputs = txInfo.getOutputs().stream().filter(
                                    tx -> (isReceiveTx ?
                                            addresses.contains(tx.getPaymentAddr().getBech32()) :
                                            !addresses.contains(tx.getPaymentAddr().getBech32())))
                            .collect(Collectors.toList());
                    List<TxIO> accountInputs = txInfo.getInputs().stream().filter(
                                    tx -> addresses.contains(tx.getPaymentAddr().getBech32()))
                            .collect(Collectors.toList());
                    List<TxAsset> allAssets = accountOutputs.stream().flatMap(tx -> tx.getAssetList().stream()).collect(Collectors.toList());

                    LOG.debug("All assets:\n{}", allAssets);
                    Double receivedOrSentFunds = accountOutputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;

                    // If it's a SET funds you need to subtract the value of receivedOrSentFunds to the sub of the input ones
                    if (!isReceiveTx && !accountInputs.isEmpty()) {
                        accountOutputs = txInfo.getOutputs().stream().filter(
                                        tx -> addresses.contains(tx.getPaymentAddr().getBech32()))
                                .collect(Collectors.toList());
                        Double inputFunds = accountInputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
                        Double outputFunds = accountOutputs.stream().mapToLong(tx -> Long.valueOf(tx.getValue())).sum() / LOVELACE;
                        receivedOrSentFunds = inputFunds - outputFunds;
                    }
                    if (!isReceiveTx)
                        receivedOrSentFunds *= -1.0d;

                    LOG.debug("fee={} ADA, {}}={} ADA", fee, (isReceiveTx ? "received" : "sent"), receivedOrSentFunds);
                    String fundsTokenText = String.format("Funds %s\n", allAssets.isEmpty() ? "" : " and Tokens");
                    if (isReceiveTx) {
                        messageBuilder.append(EmojiParser.parseToUnicode(":arrow_heading_down: Received "))
                                .append(fundsTokenText)
                                .append(EmojiParser.parseToUnicode(":small_blue_diamond:"))
                                .append("Fee ").append(String.format("%,.2f", fee)).append(ADA_SYMBOL)
                                .append(EmojiParser.parseToUnicode("\n:small_blue_diamond:"))
                                .append("Input ").append(String.format("%,.2f", receivedOrSentFunds))
                                .append(" ").append(ADA_SYMBOL);
                    } else {
                        messageBuilder.append(EmojiParser.parseToUnicode(":arrow_heading_up: Sent "))
                                .append(fundsTokenText)
                                .append(EmojiParser.parseToUnicode(":small_blue_diamond:"))
                                .append("Fee ").append(String.format("%,.2f", fee)).append(ADA_SYMBOL)
                                .append(EmojiParser.parseToUnicode("\n:small_blue_diamond:"))
                                .append("Output ").append(String.format("%,.2f", receivedOrSentFunds))
                                .append(" ").append(ADA_SYMBOL);
                    }

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

                                // Cache it
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
                this.userDao.updateUserBlockHeight(u.getId(), maxBlockHeight.get().getBlockHeight() + 1);
            }
        } catch (Throwable t) {
            LOG.error("Caught throwable while checking wallet transaction", t);
        }
    }

    private static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");

        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }

        return output.toString();
    }
}
