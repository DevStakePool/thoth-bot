package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.devpool.thothBot.scheduler.StakingRewardsCheckerTask;
import com.devpool.thothBot.scheduler.TransactionCheckerTaskV2;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.epoch.model.EpochInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EpochCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(EpochCmd.class);
    public static final String CMD_PREFIX = "/epoch";

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
        return "Shows the current epoch information";
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();

        try {

            Double latestCardanoPriceUsd = this.oracle.getPriceUsd();
            var tip = koiosFacade.getKoiosService().getNetworkService().getChainTip();
            if (!tip.isSuccessful()) {
                sendBackIssueMessage(bot, chatId, "Cannot get block-chain data right now. Please try later");
                return;
            }

            var epochInfo = this.koiosFacade.getKoiosService().getEpochService().getEpochInformationByEpoch(tip.getValue().getEpochNo());
            if (!epochInfo.isSuccessful()) {
                sendBackIssueMessage(bot, chatId, "Cannot get block-chain data right now. Please try later");
                return;
            }

            var toRender = renderEpochInformation(latestCardanoPriceUsd, epochInfo.getValue());
            bot.execute(new SendMessage(update.message().chat().id(), toRender)
                    .disableWebPagePreview(true)
                    .parseMode(ParseMode.HTML));

        } catch (Exception e) {
            LOG.warn("Could not get the epoch information due to {}", e, e);
            sendBackIssueMessage(bot, chatId,
                    "There was a problem retrieving the requested information. Please try again later");

        }
    }

    private String renderEpochInformation(Double latestCardanoPriceUsd, EpochInfo epoch) {
        StringBuilder sb = new StringBuilder();
        var epochLeftoverDuration = Duration.ofSeconds(Math.abs(epoch.getEndTime() - (System.currentTimeMillis() / 1000)));
        var d = epochLeftoverDuration.toDays();
        var h = epochLeftoverDuration.toHoursPart();
        var m = epochLeftoverDuration.toMinutesPart();
        sb
                .append(EmojiParser.parseToUnicode(":dollar: ADA price "))
                .append(String.format("%,.2f $%n", latestCardanoPriceUsd))
                .append(EmojiParser.parseToUnicode(":hourglass_flowing_sand: Epoch "))
                .append(epoch.getEpochNo()).append("\n")
                .append(EmojiParser.parseToUnicode(":stopwatch: Ends in "));
        if (d > 0)
            sb.append(String.format("%dd %dh %dm %n", d, h, m));
        else
            sb.append(String.format("%dh %dm %n", h, m));

        BigDecimal activeStake = new BigDecimal(epoch.getActiveStake());
        activeStake = activeStake.divide(BigDecimal.valueOf(AbstractCheckerTask.LOVELACE), RoundingMode.FLOOR);
        sb.append(EmojiParser.parseToUnicode("\uD83E\uDD69 Total stake: "))
                .append(humanReadableValue(activeStake.longValue()))
                .append(AbstractCheckerTask.ADA_SYMBOL)
                .append("\n");

        sb.append(EmojiParser.parseToUnicode(":twisted_rightwards_arrows: TXs count: ")).append(epoch.getTxCount());

        return sb.toString();
    }

    private String humanReadableValue(long amount) {
        if (amount >= 1_000_000_000) {
            return format(amount, 1_000_000_000, "B");
        } else if (amount >= 1_000_000) {
            return format(amount, 1_000_000, "M");
        } else if (amount >= 1_000) {
            return format(amount, 1_000, "K");
        } else {
            return String.format("$%d", amount);
        }
    }

    private static String format(long amount, long divisor, String suffix) {
        double value = (double) amount / divisor;
        DecimalFormat df = new DecimalFormat("#,##0.#"); // Format to 2 decimal places if needed
        return df.format(value) + suffix;
    }

    private void sendBackIssueMessage(TelegramBot bot, long chatId, String message) {
        bot.execute(new SendMessage(chatId,
                EmojiParser.parseToUnicode(":warning: ") + message));
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 30L;
    }
}
