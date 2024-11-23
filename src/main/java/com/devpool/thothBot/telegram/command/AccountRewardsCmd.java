package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.exceptions.UserNotFoundException;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountReward;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.factory.options.Options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
public class AccountRewardsCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AccountRewardsCmd.class);
    public static final String CMD_PREFIX = "/ar";
    private static final int MAX_LAST_REWARDS = 5;

    @Override
    public boolean canTrigger(String username, String message) {
        return message.trim().startsWith(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp(String username) {
        return false;
    }

    @Override
    public String getDescription() {
        return "";
    }


    @Override
    public void execute(Update update, TelegramBot bot) {
        if (update.callbackQuery() == null) {
            LOG.error("Callback query is null: {}", update);
            return;
        }

        Long chatId = update.callbackQuery().maybeInaccessibleMessage().chat().id();
        Integer messageId = update.callbackQuery().maybeInaccessibleMessage().messageId();

        String msgText = update.callbackQuery().data().trim();
        String userId = msgText.substring(CMD_PREFIX.length());
        LOG.debug("account rewards callback data (messageId={}): {} -> userID={}",
                messageId, msgText, userId);

        try {
            LOG.debug("Getting staking rewards for user {}", userId);
            var user = userDao.getUser(Long.parseLong(userId));

            var rewardsResp = koiosFacade.getKoiosService()
                    .getAccountService()
                    .getAccountRewards(List.of(user.getAddress()), null, null);

            if (!rewardsResp.isSuccessful()) {
                bot.execute(new SendMessage(chatId, "Can't get the account rewards now. Please try again later"));
                return;
            }

            var accountRewards = rewardsResp.getValue().stream().findFirst().orElseThrow(() -> new NoSuchElementException("No data returned from Koios"));
            var lastRewards = accountRewards.getRewards()
                    .stream()
                    .skip(Math.max(0, accountRewards.getRewards().size() - MAX_LAST_REWARDS))
                    .collect(Collectors.toList());
            Collections.reverse(lastRewards);

            // Grab pools names
            var allPools = lastRewards.stream().map(AccountReward::getPoolId).distinct().collect(Collectors.toList());

            var poolInfoRes = this.koiosFacade.getKoiosService().getPoolService().getPoolInformation(allPools, null);
            List<PoolInfo> poolInfoList = null;
            if (poolInfoRes.isSuccessful())
                poolInfoList = poolInfoRes.getValue();
            else
                LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());

            // Grab ada handle
            var handles = getAdaHandleForAccount(user.getAddress());

            // Get cardano price
            Double latestCardanoPriceUsd = this.oracle.getPriceUsd();

            // Render the response
            StringBuilder sb = new StringBuilder();
            sb.append(EmojiParser.parseToUnicode(":bar_chart: Latest rewards for <a href=\""))
                    .append(CARDANO_SCAN_STAKE_KEY)
                    .append(user.getAddress()).append("\">")
                    .append(handles.getOrDefault(user.getAddress(), shortenAddr(user.getAddress())))
                    .append("</a>\n\n");

            for (AccountReward reward : lastRewards) {
                var poolId = reward.getPoolId();
                var amount = Double.parseDouble(reward.getAmount()) / LOVELACE;
                var epoch = reward.getEarnedEpoch();
                sb.append(EmojiParser.parseToUnicode(":hourglass_flowing_sand: Epoch "))
                        .append(epoch).append("\n");
                sb.append(EmojiParser.parseToUnicode(":classical_building: "))
                        .append("<a href=\"")
                        .append(CARDANO_SCAN_STAKE_POOL)
                        .append(poolId)
                        .append("\">")
                        .append(getPoolName(poolInfoList, poolId)).append("</a>\n");
                sb.append(EmojiParser.parseToUnicode(":arrow_heading_down: "))
                        .append(String.format("%,.2f", amount)).append(ADA_SYMBOL);
                // USD value
                if (latestCardanoPriceUsd != null) {
                    sb.append(" (").append(String.format("%,.2f $", amount * latestCardanoPriceUsd)).append(")");
                }
                sb.append("\n\n");
            }

            bot.execute(new SendMessage(chatId, sb.toString())
                    .disableWebPagePreview(true)
                    .parseMode(ParseMode.HTML));
        } catch (UserNotFoundException e) {
            bot.execute(new SendMessage(chatId, String.format("The user with ID %s cannot be found.", userId)));
        } catch (Exception e) {
            LOG.error("Unknown error when getting assets for user-id {}", userId, e);
            bot.execute(new SendMessage(chatId, String.format("Unknown error when getting account for user-id %s. %s", userId, e)));
        }
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 10;
    }
}
