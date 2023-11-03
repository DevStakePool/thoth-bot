package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.devpool.thothBot.scheduler.StakingRewardsCheckerTask;
import com.devpool.thothBot.scheduler.TransactionCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.factory.options.SortType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    public AccountInfoCmd() {
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();
        boolean failure = false;

        try {
            List<String> addresses = this.userDao.getUsers().stream().filter(
                    u -> u.getChatId().equals(chatId)).map(
                    u -> u.getAddress()).collect(Collectors.toList());

            List<String> stakingAddr = addresses.stream().filter(a -> User.isStakingAddress(a)).collect(Collectors.toList());
            List<String> normalAddr = addresses.stream().filter(a -> !User.isStakingAddress(a)).collect(Collectors.toList());

            if (addresses.isEmpty()) {
                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("You have not yet registered any Cardano account or address. Please try %s", SubscribeCmd.CMD_PREFIX)));
                return;
            }

            StringBuilder messageBuilder = new StringBuilder();

            if (!stakingAddr.isEmpty()) {
                Result<List<AccountInfo>> accountInfoRes = this.koiosFacade.getKoiosService().getAccountService().getAccountInformation(
                        stakingAddr, null);
                if (!accountInfoRes.isSuccessful()) {
                    LOG.warn("Koios call failed when retrieving the account information for chat-id {}: {}", chatId, accountInfoRes.getResponse());
                    failure = true;
                } else {
                    // Get accounts ADA Handles
                    Map<String, String> handles = getAdaHandleForAccount(stakingAddr.toArray(new String[0]));

                    for (AccountInfo accountInfo : accountInfoRes.getValue()) {
                        messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                                .append(TransactionCheckerTask.CARDANO_SCAN_STAKE_KEY)
                                .append(accountInfo.getStakeAddress())
                                .append("\">")
                                .append(handles.get(accountInfo.getStakeAddress()))
                                .append("</a>\n");

                        if (accountInfo.getDelegatedPool() != null) {
                            try {
                                Result<List<PoolInfo>> poolInfoRes = this.koiosFacade.getKoiosService().getPoolService().getPoolInformation(
                                        Arrays.asList(accountInfo.getDelegatedPool()), null);
                                PoolInfo pool;
                                if (poolInfoRes.isSuccessful()) {
                                    pool = poolInfoRes.getValue().get(0);
                                    String poolName = getPoolName(pool, accountInfo.getDelegatedPool());
                                    messageBuilder.append(EmojiParser.parseToUnicode(":white_small_square: "))
                                            .append("<a href=\"")
                                            .append(CARDANO_SCAN_STAKE_POOL)
                                            .append(accountInfo.getDelegatedPool())
                                            .append("\">")
                                            .append(poolName).append("</a>\n");
                                } else
                                    LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());
                            } catch (ApiException e) {
                                LOG.warn("Cannot retrieve pool information: {}", e, e);
                            }
                        }

                        double cardanoBalance = Long.parseLong(accountInfo.getTotalBalance()) / StakingRewardsCheckerTask.LOVELACE;
                        Double latestCardanoPriceUsd = this.oracle.getPriceUsd();
                        double cardanoBalanceUsd = -1;
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

                }
            }

            Map<String, String> handlesForAddresses = getAdaHandleForAccount(normalAddr.toArray(new String[0]));
            for (String addr : normalAddr) {
                Result<AddressInfo> addressInfoResult = this.koiosFacade.getKoiosService().getAddressService().getAddressInformation(
                        List.of(addr), SortType.DESC, null);
                if (!addressInfoResult.isSuccessful()) {
                    LOG.warn("Koios call failed when retrieving the address information for chat-id {}: {}", chatId, addressInfoResult.getResponse());
                    failure = true;
                } else {
                    // Get accounts ADA Handles
                    AddressInfo addrInfo = addressInfoResult.getValue();
                    messageBuilder.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                            .append(TransactionCheckerTask.CARDANO_SCAN_ADDR_KEY)
                            .append(addrInfo.getAddress())
                            .append("\">")
                            .append(handlesForAddresses.get(addrInfo.getAddress()))
                            .append("</a>\n");

                    double cardanoBalance = Long.parseLong(addrInfo.getBalance()) / StakingRewardsCheckerTask.LOVELACE;
                    Double latestCardanoPriceUsd = this.oracle.getPriceUsd();
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
                            .append(addrInfo.getScriptAddress() ? "YES" : "NO").append("\n");

                    messageBuilder
                            .append(EmojiParser.parseToUnicode(":white_small_square: "))
                            .append("UTXOs: ")
                            .append(addrInfo.getUtxoSet().size())
                            .append("\n\n");
                }
            }

            bot.execute(new SendMessage(update.message().chat().id(), messageBuilder.toString())
                    .disableWebPagePreview(true)
                    .parseMode(ParseMode.HTML));
        } catch (Exception e) {
            LOG.warn("Could not get the wallet information due to {}", e, e);
            failure = true;
        }


        if (failure) {
            bot.execute(new SendMessage(update.message().chat().id(),
                    "There was an problem retrieving the account information. Please try again later"));
        }
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 10L;
    }

    private String getPoolName(PoolInfo poolInfo, String poolAddress) {
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
}
