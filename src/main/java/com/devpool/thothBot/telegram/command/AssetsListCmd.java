package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.UserNotFoundException;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAssets;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.common.Asset;

import java.util.List;
import java.util.Optional;

@Component
public class AssetsListCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AssetsListCmd.class);
    public static final String CMD_PREFIX = "/al";

    @Autowired
    private AssetFacade assetFacade;

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

    public AssetsListCmd() {
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
            LOG.debug("Getting assets for user {}", userId);
            user = userDao.getUser(Long.parseLong(userId));

            List<Asset> assets;
            if (user.isStakeAddress()) {
                Result<List<AccountAssets>> result = this.koiosFacade.getKoiosService()
                        .getAccountService().getAccountAssets(List.of(user.getAddress()), null, null);
                if (!result.isSuccessful()) {
                    bot.execute(new SendMessage(chatId, String.format("Could not get account assets for staking address %s. %s (%d)",
                            user.getAddress(), result.getResponse(), result.getCode())));
                    return;
                }

                List<AccountAssets> assetsList = result.getValue();
                Optional<AccountAssets> assetForAccount = assetsList.stream().findFirst();
                if (assetForAccount.isEmpty()) {
                    bot.execute(new SendMessage(chatId, "No assets found for this account"));
                    return;
                }
                assets = assetForAccount.get().getAssetList();
            } else {
                Result<List<AddressAsset>> result = this.koiosFacade.getKoiosService()
                        .getAddressService().getAddressAssets(List.of(user.getAddress()), null);
                if (!result.isSuccessful()) {
                    bot.execute(new SendMessage(chatId, String.format("Could not get account assets for address %s. %s (%d)",
                            user.getAddress(), result.getResponse(), result.getCode())));
                    return;
                }

                List<AddressAsset> assetsList = result.getValue();
                Optional<AddressAsset> assetsForAddr = assetsList.stream().findFirst();
                if (assetsForAddr.isEmpty()) {
                    bot.execute(new SendMessage(chatId, "No assets found for this address"));
                    return;
                }
                assets = assetsForAddr.get().getAssetList();
            }

            InlineKeyboardButton[][] assetsButtons = new InlineKeyboardButton[Math.min(assets.size(), MAX_BUTTON_ROWS)][1];

            int processed = 0;
            StringBuilder messageBuilder = new StringBuilder("Assets")
                    .append(" ").append("for address ").append(this.getAdaHandleForAccount(user.getAddress()).get(user.getAddress()));

            for (Asset asset : assets) {
                Object genericQuantity = this.assetFacade.getAssetQuantity(
                        asset.getPolicyId(), asset.getAssetName(), Long.parseLong(asset.getQuantity()));

                // construct the inline button
                StringBuilder buttonText = new StringBuilder()
                        .append(EmojiParser.parseToUnicode("\n:small_orange_diamond:"))
                        .append(hexToAscii(asset.getAssetName()))
                        .append(" ")
                        .append(this.assetFacade.formatAssetQuantity(genericQuantity));

                Optional<Long> assetCacheId = this.assetFacade.getCacheIdFor(asset);

                assetsButtons[processed][0] = new InlineKeyboardButton(buttonText.toString())
                        .callbackData("/d " + (assetCacheId.isEmpty() ? AssetFacade.UNKNOWN : assetCacheId.get()));

                processed++;

                if (processed >= MAX_BUTTON_ROWS) {
                    messageBuilder.append(". Showing ").append(processed).append("/").append(assets.size());
                    break;
                }
            }

            // Notify the user

            bot.execute(new SendMessage(chatId, messageBuilder.toString())
                    .replyMarkup(new InlineKeyboardMarkup(assetsButtons)));
        } catch (UserNotFoundException e) {
            bot.execute(new SendMessage(chatId, String.format("The user with ID %s cannot be found.", userId)));
        } catch (Exception e) {
            LOG.error("Unknown error when getting assets for user-id " + userId, e);
            bot.execute(new SendMessage(chatId, String.format("Unknown error when getting account for user-id %s. %s", userId, e)));
        }
    }
}
