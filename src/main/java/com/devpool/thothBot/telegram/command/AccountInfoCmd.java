package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.oracle.CoinGeckoCardanoOracle;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.pool.model.PoolInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AccountInfoCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AccountInfoCmd.class);
    public static final String CMD_PREFIX = "/info";

    @Autowired
    private CoinGeckoCardanoOracle oracle;

    @Override
    public boolean canTrigger(String message) {
        return message.trim().equals(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Shows the details of the registered accounts/wallets";
    }

    public AccountInfoCmd() {
    }

    //FIXME 11 - tests missing
    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();
        boolean failure = false;

        try {
            List<String> stakeAddresses = this.userDao.getUsers().stream().filter(
                    u -> u.getChatId().equals(chatId)).map(
                    u -> u.getAddress()).collect(Collectors.toList());

            if (stakeAddresses.isEmpty()) {
                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("You have not yet registered any Cardano account. Please try %s", SubscribeCmd.CMD_PREFIX)));
                return;
            }

            Result<List<AccountInfo>> accountInfoRes = this.koiosFacade.getKoiosService().getAccountService().getAccountInformation(
                    stakeAddresses, null);

            if (!accountInfoRes.isSuccessful()) {
                LOG.warn("Koios call failed when retrieving the account information for chat-id {}: {}", chatId, accountInfoRes.getResponse());
                failure = true;
            } else {
                // Get accounts ADA Handles
                Map<String, String> handles = getAdaHandleForAccount(stakeAddresses.toArray(new String[0]));

                StringBuilder messageBuilder = new StringBuilder();
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
                            LOG.warn("Cannot retrieve pool information: {}", e);
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

                bot.execute(new SendMessage(update.message().chat().id(), messageBuilder.toString())
                        .disableWebPagePreview(true)
                        .parseMode(ParseMode.HTML));
            }
        } catch (Exception e) {
            LOG.warn("Could not get the wallet information due to {}", e, e);
            failure = true;
        }

        if (failure) {
            bot.execute(new SendMessage(update.message().chat().id(),
                    "There was an problem retrieving the account information. Please try again later"));
        }
    }

    private String getPoolName(PoolInfo poolInfo, String poolAddress) {

        if (poolInfo != null) {
            if (poolInfo.getMetaJson() != null && poolInfo.getMetaJson().getTicker() != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                sb.append(poolInfo.getMetaJson().getTicker());
                sb.append("]");
                if (poolInfo.getMetaJson().getName() != null)
                    sb.append(" ").append(poolInfo.getMetaJson().getName());

                return sb.toString();
            }
        }

        return "pool1..." + poolAddress.substring(poolAddress.length() - 8);
    }
}
