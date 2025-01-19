package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.KoiosResponseException;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.devpool.thothBot.scheduler.StakingRewardsCheckerTask;
import com.devpool.thothBot.scheduler.TransactionCheckerTaskV2;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.factory.options.SortType;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class AccountInfoCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AccountInfoCmd.class);
    public static final String CMD_PREFIX = "/info";

    @Override
    public boolean canTrigger(String username, String message) {
        return message.trim().equals(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp(String username) {
        return true;
    }

    @Override
    public String getDescription() {
        return "Shows the details of the registered accounts/wallets";
    }

    private ExecutorService executorService = Executors.newFixedThreadPool(6,
            new CustomizableThreadFactory("InfoCommandWorker"));

    @PreDestroy
    public void shutdown() {
        this.executorService.shutdown();
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();

        try {
            List<String> addresses = this.userDao.getUsers().stream().filter(
                    u -> u.getChatId().equals(chatId)).map(
                    User::getAddress).collect(Collectors.toList());

            List<String> stakingAddr = addresses.stream().filter(User::isStakingAddress).collect(Collectors.toList());
            List<String> normalAddr = addresses.stream().filter(User::isNormalAddress).collect(Collectors.toList());

            if (addresses.isEmpty()) {
                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("You have not yet registered any Cardano account or address. Please try %s", SubscribeCmd.CMD_PREFIX)));
                return;
            }

            AccountInfoCaller accountInfoCaller = new AccountInfoCaller(stakingAddr, this.koiosFacade, chatId);
            AddressInfoCaller addressInfoCaller = new AddressInfoCaller(normalAddr, this.koiosFacade, chatId);
            Future<List<AccountInfo>> accountInfoFuture = this.executorService.submit(accountInfoCaller);
            Future<List<AddressInfo>> addressInfoFuture = this.executorService.submit(addressInfoCaller);

            // Get accounts ADA Handles while the other two threads work...
            Map<String, String> handles = getAdaHandleForAccount(addresses.toArray(new String[0]));

            List<AccountInfo> accountInfoList = accountInfoFuture.get();
            List<AddressInfo> addressInfoList = addressInfoFuture.get();
            Double latestCardanoPriceUsd = this.oracle.getPriceUsd();

            // Retrieval pool names
            List<String> allStakePools = accountInfoList.stream().map(AccountInfo::getDelegatedPool)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());

            Map<String, String> poolNames = new HashMap<>();
            allStakePools.forEach(p -> poolNames.put(p, extractPoolName(null, p))); // Let's first get defaults
            try {
                Result<List<PoolInfo>> poolInfoRes = this.koiosFacade.getKoiosService().getPoolService().getPoolInformation(allStakePools, null);
                if (poolInfoRes.isSuccessful()) {
                    for (PoolInfo poolInfo : poolInfoRes.getValue()) {
                        String poolName = extractPoolName(poolInfo, poolInfo.getPoolIdBech32());
                        poolNames.put(poolInfo.getPoolIdBech32(), poolName);
                    }
                } else
                    LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());
            } catch (ApiException e) {
                LOG.warn("Cannot retrieve pool information: {}", e, e);
            }

            // Retrieve Dreps info
            List<String> allDreps = accountInfoList.stream().map(AccountInfo::getDelegatedDrep)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            var drepNames = getDrepNames(allDreps);

            StringBuilder sb = new StringBuilder();
            renderAccountInformation(sb, accountInfoList, poolNames, handles, latestCardanoPriceUsd, stakingAddr, drepNames);
            renderAddressInformation(sb, addressInfoList, handles, latestCardanoPriceUsd, normalAddr);

            bot.execute(new SendMessage(update.message().chat().id(), sb.toString())
                    .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true))
                    .parseMode(ParseMode.HTML));

        } catch (InterruptedException e) {
            if (Thread.interrupted())
                Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("Could not get the wallet/address information due to {}", e, e);
            bot.execute(new SendMessage(update.message().chat().id(),
                    "There was a problem retrieving the requested information. Please try again later"));

        }
    }

    private void renderAddressInformation(StringBuilder messageBuilder, List<AddressInfo> addressInfoList,
                                          Map<String, String> handles, Double latestCardanoPriceUsd,
                                          List<String> addresses) {

        // Koios could send empty results if data is not cached (new address)
        List<String> unresolvedAddresses = addresses.stream()
                .filter(a -> addressInfoList.stream().map(AddressInfo::getAddress).noneMatch(s -> s.equals(a)))
                .collect(Collectors.toList());

        for (AddressInfo addrInfo : addressInfoList) {
            messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                    .append(TransactionCheckerTaskV2.CARDANO_SCAN_ADDR_KEY)
                    .append(addrInfo.getAddress())
                    .append("\">")
                    .append(handles.get(addrInfo.getAddress()))
                    .append("</a>\n");

            double cardanoBalance = Long.parseLong(addrInfo.getBalance()) / StakingRewardsCheckerTask.LOVELACE;
            double cardanoBalanceUsd = -1;
            if (latestCardanoPriceUsd != null)
                cardanoBalanceUsd = cardanoBalance * latestCardanoPriceUsd;

            messageBuilder.append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append("Balance: ")
                    .append(String.format("%,.2f", cardanoBalance))
                    .append(StakingRewardsCheckerTask.ADA_SYMBOL)
                    .append("\n");

            // USD value
            if (latestCardanoPriceUsd != null) {
                messageBuilder.append(EmojiParser.parseToUnicode(":white_small_square: "))
                        .append("USD Value: ")
                        .append(String.format("%,.2f $", cardanoBalanceUsd))
                        .append("\n");
            }

            messageBuilder
                    .append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append("Stake Address: ")
                    .append(addrInfo.getStakeAddress() != null ? "YES" : "NO").append("\n");

            messageBuilder
                    .append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append("Script Address: ")
                    .append(Boolean.TRUE.equals(addrInfo.getScriptAddress()) ? "YES" : "NO").append("\n");

            messageBuilder
                    .append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append("UTXOs: ")
                    .append(addrInfo.getUtxoSet().size())
                    .append("\n\n");
        }

        for (String unresolvedAddress : unresolvedAddresses) {
            messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                    .append(TransactionCheckerTaskV2.CARDANO_SCAN_ADDR_KEY)
                    .append(unresolvedAddress)
                    .append("\">")
                    .append(handles.get(unresolvedAddress))
                    .append("</a>\n")
                    .append(EmojiParser.parseToUnicode(":white_small_square: Data will be available soon\n\n"));
        }
    }

    private void renderAccountInformation(StringBuilder messageBuilder, List<AccountInfo> accountInfoList,
                                          Map<String, String> poolNames, Map<String, String> handles, Double latestCardanoPriceUsd,
                                          List<String> addresses, Map<String, String> drepNames) {

        // Koios could send empty results if data is not cached (new address)
        List<String> unresolvedAddresses = addresses.stream()
                .filter(a -> accountInfoList.stream()
                        .map(AccountInfo::getStakeAddress)
                        .noneMatch(s -> s.equals(a)))
                .toList();

        for (AccountInfo accountInfo : accountInfoList) {

            messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                    .append(TransactionCheckerTaskV2.CARDANO_SCAN_STAKE_KEY)
                    .append(accountInfo.getStakeAddress())
                    .append("\">")
                    .append(handles.get(accountInfo.getStakeAddress()))
                    .append("</a>\n");

            if (accountInfo.getDelegatedPool() != null) {
                String poolName = poolNames.get(accountInfo.getDelegatedPool());
                messageBuilder.append(EmojiParser.parseToUnicode(":classical_building: "))
                        .append("<a href=\"")
                        .append(CARDANO_SCAN_STAKE_POOL)
                        .append(accountInfo.getDelegatedPool())
                        .append("\">")
                        .append(poolName).append("</a>\n");
            }

            if (accountInfo.getDelegatedDrep() != null) {
                String drepFullHash = accountInfo.getDelegatedDrep();
                messageBuilder.append(EmojiParser.parseToUnicode(":scales: "));
                if (drepFullHash.startsWith(DREP_HASH_PREFIX)) {
                    messageBuilder.append("DRep <a href=\"")
                            .append(GOV_TOOLS_DREP)
                            .append(drepFullHash)
                            .append("\">");
                }
                messageBuilder.append(drepNames.get(drepFullHash));
                if (drepFullHash.startsWith(DREP_HASH_PREFIX)) {
                    messageBuilder.append("</a>");
                }
                messageBuilder.append("\n");
            }

            double cardanoBalanceUsd = -1;
            double cardanoBalance = Long.parseLong(accountInfo.getTotalBalance()) / StakingRewardsCheckerTask.LOVELACE;
            if (latestCardanoPriceUsd != null)
                cardanoBalanceUsd = cardanoBalance * latestCardanoPriceUsd;

            messageBuilder.append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append("Total Balance: ")
                    .append(String.format("%,.2f", cardanoBalance))
                    .append(StakingRewardsCheckerTask.ADA_SYMBOL)
                    .append("\n");

            // USD value
            if (latestCardanoPriceUsd != null) {
                messageBuilder.append(EmojiParser.parseToUnicode(":white_small_square: "))
                        .append("USD Value: ")
                        .append(String.format("%,.2f $", cardanoBalanceUsd))
                        .append("\n");
            }

            messageBuilder
                    .append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append("Rewards: ")
                    .append(String.format("%,.2f", Long.parseLong(accountInfo.getRewards()) / StakingRewardsCheckerTask.LOVELACE))
                    .append(StakingRewardsCheckerTask.ADA_SYMBOL)
                    .append("\n")
                    .append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append("Status: ").append(accountInfo.getStatus())
                    .append("\n\n");
        }

        for (String unresolvedAddress : unresolvedAddresses) {
            messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                    .append(TransactionCheckerTaskV2.CARDANO_SCAN_STAKE_KEY)
                    .append(unresolvedAddress)
                    .append("\">")
                    .append(handles.get(unresolvedAddress))
                    .append("</a>\n")
                    .append(EmojiParser.parseToUnicode(":white_small_square: Data will be available soon\n\n"));
        }
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 30L;
    }

    private String extractPoolName(PoolInfo poolInfo, String poolAddress) {
        if (poolInfo != null && poolInfo.getMetaJson() != null && poolInfo.getMetaJson().getTicker() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(poolInfo.getMetaJson().getTicker());
            sb.append("]");
            if (poolInfo.getMetaJson().getName() != null)
                sb.append(" ").append(poolInfo.getMetaJson().getName());

            return sb.toString();
        }

        return "pool1..." + poolAddress.substring(poolAddress.length() - 8);
    }

    public class AccountInfoCaller implements Callable<List<AccountInfo>> {
        private final Logger LOG_INTERNAL = LoggerFactory.getLogger(AccountInfoCaller.class);
        private final KoiosFacade koiosFacade;
        private final List<String> addresses;
        private final long chatId;

        public AccountInfoCaller(List<String> addresses, KoiosFacade koiosFacade, long chatId) {
            this.addresses = addresses;
            this.koiosFacade = koiosFacade;
            this.chatId = chatId;
        }

        @Override
        public List<AccountInfo> call() throws Exception {
            Result<List<AccountInfo>> accountInfoRes = this.koiosFacade.getKoiosService().getAccountService().getCachedAccountInformation(
                    this.addresses, null);
            if (!accountInfoRes.isSuccessful()) {
                LOG_INTERNAL.warn("Koios call failed when retrieving the account information for chat-id {}: {}/{}",
                        chatId, accountInfoRes.getCode(), accountInfoRes.getResponse());
                throw new KoiosResponseException(String.format("Koios call failed when retrieving the account information for chat-id %d: %d/%s",
                        chatId, accountInfoRes.getCode(), accountInfoRes.getResponse()));
            }

            return accountInfoRes.getValue();
        }
    }

    public class AddressInfoCaller implements Callable<List<AddressInfo>> {
        private final Logger LOG_INTERNAL = LoggerFactory.getLogger(AddressInfoCaller.class);
        private final KoiosFacade koiosFacade;
        private final List<String> addresses;
        private final long chatId;

        public AddressInfoCaller(List<String> addresses, KoiosFacade koiosFacade, long chatId) {
            this.addresses = addresses;
            this.koiosFacade = koiosFacade;
            this.chatId = chatId;
        }

        @Override
        public List<AddressInfo> call() throws Exception {
            Result<List<AddressInfo>> addressInfoRes = this.koiosFacade.getKoiosService().getAddressService().getAddressInformation(
                    this.addresses, SortType.DESC, null);
            if (!addressInfoRes.isSuccessful()) {
                LOG_INTERNAL.warn("Koios call failed when retrieving the address information for chat-id {}: {}/{}",
                        chatId, addressInfoRes.getCode(), addressInfoRes.getResponse());
                throw new KoiosResponseException(String.format("Koios call failed when retrieving the address information for chat-id %d: %d/%s",
                        chatId, addressInfoRes.getCode(), addressInfoRes.getResponse()));

            }
            return addressInfoRes.getValue();
        }
    }


}
