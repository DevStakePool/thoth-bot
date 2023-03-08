package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.UserNotFoundException;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAssets;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.common.Asset;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class DetailsCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(DetailsCmd.class);
    public static final String CMD_PREFIX = "/d";

    @Autowired
    private AssetFacade assetFacade;

    @Override
    public boolean canTrigger(String message) {
        return message.trim().startsWith(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp() {
        return false;
    }

    @Override
    public String getDescription() {
        return "";
    }

    public DetailsCmd() {
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (update.callbackQuery() == null) {
            LOG.error("Callback query is null: {}", update);
            return;
        }

        Long chatId = update.callbackQuery().message().chat().id();

        String msgText = update.callbackQuery().data().trim();
        String[] msgParts = msgText.split(" ");
        if (msgParts.length != 2) {
            LOG.warn("Invalid message {}. Expected prefix + user_id", msgText);
            bot.execute(new SendMessage(chatId,
                    String.format("Invalid {} command", CMD_PREFIX)));
            return;
        }

        bot.execute(new SendMessage(chatId, "Processing..."));

        String userId = msgParts[1];
        User user;
        try {
            LOG.debug("Getting assets for account {}", userId);
            user = userDao.getUser(Long.parseLong(userId));
            Result<List<AccountAssets>> result = this.koiosFacade.getKoiosService()
                    .getAccountService().getAccountAssets(List.of(user.getStakeAddr()), null, null);
            if (!result.isSuccessful()) {
                bot.execute(new SendMessage(chatId, String.format("Could not get account assets for staking address {}. {} ({})",
                        user.getStakeAddr(), result.getResponse(), result.getCode())));
                return;
            }

            List<AccountAssets> assetsList = result.getValue();
            Optional<AccountAssets> assetForAccount = assetsList.stream().findFirst();
            if (assetForAccount.isEmpty()) {
                //TODO
                return;
            }

            StringBuilder messageBuilder = new StringBuilder("Assets:");
            for (Asset asset : assetForAccount.get().getAssetList()) {
                Object genericQuantity = this.assetFacade.getAssetQuantity(
                        asset.getPolicyId(), asset.getAssetName(), Long.parseLong(asset.getQuantity()));

                messageBuilder.append(EmojiParser.parseToUnicode("\n:small_orange_diamond:"))
                        .append(hexToAscii(asset.getAssetName()))
                        .append(" ")
                        .append(this.assetFacade.formatAssetQuantity(genericQuantity));
            }


            bot.execute(new SendMessage(chatId, messageBuilder.toString()));
        } catch (UserNotFoundException e) {
            bot.execute(new SendMessage(chatId, String.format("The user with ID {} cannot be found.", userId)));
        } catch (Exception e) {
            bot.execute(new SendMessage(chatId, String.format("Unknown error when getting account assets for user-id {}. {}", userId, e)));
        }
    }
}
