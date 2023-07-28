package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.UserNotFoundException;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAssets;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.common.Asset;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class AssetsListCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AssetsListCmd.class);
    public static final String CMD_PREFIX = "/al";
    public static final String CMD_DATA_SEPARATOR = ":";
    private static final int ASSET_LIST_PAGE_SIZE = 10;

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

    private Set<Integer> liveMessages;

    public AssetsListCmd() {
        this.liveMessages = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (update.callbackQuery() == null) {
            LOG.error("Callback query is null: {}", update);
            return;
        }

        Message incomingMessage = update.callbackQuery().message();
        Long chatId = update.callbackQuery().message().chat().id();
        Integer messageId = update.callbackQuery().message().messageId();

        String msgText = update.callbackQuery().data().trim();
        LOG.debug("assets list callback data (messageId={}): {}", messageId, msgText);

        String[] msgParts = msgText.split(CMD_DATA_SEPARATOR);
        if (msgParts.length != 3) {
            LOG.warn("Invalid message {}. Expected prefix + user_id + offset", msgText);
            bot.execute(new SendMessage(chatId,
                    String.format("Invalid {} command", CMD_PREFIX)));
            return;
        }

        String userId = msgParts[1];
        String offset = msgParts[2];

        try {
            Integer offsetNumber = Integer.parseInt(offset);    //TODO validate me
            User user;

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

            // Sort the assets first
            assets.sort(Comparator.comparing(Asset::getAssetName));

            int endOffset = Math.min(assets.size(), offsetNumber + ASSET_LIST_PAGE_SIZE);
            // Page header data
            StringBuilder assetsPage = new StringBuilder();
            assetsPage.append("Assets")
                    .append(" ")
                    .append("for address ")
                    .append(this.getAdaHandleForAccount(user.getAddress())
                            .get(user.getAddress()))
                    .append("\n\n");
            int shown = 0;
            if (offsetNumber < assets.size())
                for (int i = offsetNumber; i < endOffset; i++) {
                    Asset asset = assets.get(i);
                    Object genericQuantity = this.assetFacade.getAssetQuantity(
                            asset.getPolicyId(), asset.getAssetName(), Long.parseLong(asset.getQuantity()));

                    // construct the inline button
                    assetsPage.append(EmojiParser.parseToUnicode("\n:small_orange_diamond:"))
                            .append("<a href=\"https://pool.pm/").append(asset.getFingerprint())
                            .append("\">")
                            .append(hexToAscii(asset.getAssetName()))
                            .append("</a> ")
                            .append(this.assetFacade.formatAssetQuantity(genericQuantity));
                    shown++;
                }

            int pageNumber = (endOffset / ASSET_LIST_PAGE_SIZE) + (endOffset % ASSET_LIST_PAGE_SIZE > 0 ? 1 : 0);
            assetsPage
                    .append("\n\n").append("Shown ").append(shown + offsetNumber).append("/").append(assets.size())
                    .append("\nPage ")
                    .append(pageNumber)
                    .append("/")
                    .append(assets.size() / ASSET_LIST_PAGE_SIZE + (assets.size() % ASSET_LIST_PAGE_SIZE > 0 ? 1 : 0));

            LOG.trace("startOffset={}, endOffset={}, total={}", offsetNumber, endOffset, assets.size());

            // PREV/NEXT inline buttons
            InlineKeyboardButton[][] navigationButtons = new InlineKeyboardButton[1][2];

            // Prev
            navigationButtons[0][0] = new InlineKeyboardButton(EmojiParser.parseToUnicode(":arrow_backward: PREV"))
                    .callbackData(CMD_PREFIX + CMD_DATA_SEPARATOR + userId +
                            CMD_DATA_SEPARATOR + Math.max(0, offsetNumber - ASSET_LIST_PAGE_SIZE));

            // Next
            navigationButtons[0][1] = new InlineKeyboardButton(EmojiParser.parseToUnicode("NEXT :arrow_forward:"))
                    .callbackData(CMD_PREFIX + CMD_DATA_SEPARATOR + userId +
                            CMD_DATA_SEPARATOR +
                            Math.min((assets.size() - ASSET_LIST_PAGE_SIZE + 1 > 0 ?
                                            (assets.size() == ASSET_LIST_PAGE_SIZE ? assets.size() - ASSET_LIST_PAGE_SIZE : assets.size() - ASSET_LIST_PAGE_SIZE + 1)
                                            : offsetNumber),
                                    offsetNumber + ASSET_LIST_PAGE_SIZE));

            // Notify the user
            if (this.liveMessages.contains(incomingMessage.messageId())) {
                // It's an edit action
                BaseResponse editResp = bot.execute(new EditMessageText(chatId, incomingMessage.messageId(), assetsPage.toString()).parseMode(ParseMode.HTML).disableWebPagePreview(true)
                        .replyMarkup(new InlineKeyboardMarkup(navigationButtons)));
                LOG.debug("Edit response is ok? {}, error code {}, {}", editResp.isOk(), editResp.errorCode(), editResp.description());
            } else {
                // It's the first page and the message has to be created
                SendResponse resp = bot.execute(new SendMessage(chatId, assetsPage.toString()).parseMode(ParseMode.HTML).disableWebPagePreview(true)
                        .replyMarkup(new InlineKeyboardMarkup(navigationButtons)));
                this.liveMessages.add(resp.message().messageId());
            }
            LOG.debug("Current set of messages: {}. total assets {} offset-start {} offset-end {}",
                    this.liveMessages,
                    assets.size(),
                    offset, endOffset);

        } catch (UserNotFoundException e) {
            bot.execute(new SendMessage(chatId, String.format("The user with ID %s cannot be found.", userId)));
        } catch (Exception e) {
            LOG.error("Unknown error when getting assets for user-id " + userId, e);
            bot.execute(new SendMessage(chatId, String.format("Unknown error when getting account for user-id %s. %s", userId, e)));
        }
    }
}
