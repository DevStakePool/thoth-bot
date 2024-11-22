package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AssetsCmd extends AbstractSubscriptionSelectionCmd {
    private static final Logger LOG = LoggerFactory.getLogger(AssetsCmd.class);
    public static final String CMD_PREFIX = "/assets";

    @Override
    public String getDescription() {
        return "Shows the assets of a specific Cardano account/address";
    }

    public AssetsCmd() {
        super(CMD_PREFIX);
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();

        Map<String, Long> addresses = this.userDao.getUsers().stream().filter(
                u -> u.getChatId().equals(chatId)).collect(Collectors.toMap(User::getAddress, User::getId));

        if (addresses.isEmpty()) {
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("You have not yet registered any Cardano account or address. Please try %s", SubscribeCmd.CMD_PREFIX)));
            return;
        }

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(createInlineKeyboardButtons(addresses));
        LOG.debug("Created {} inline markup rows", inlineKeyboard.inlineKeyboard().length);
        bot.execute(new SendMessage(chatId, "Please select an account").replyMarkup(inlineKeyboard));
    }

    @Override
    protected String createCallbackData(long chatId) {
        return String.format("%s%s%d%s0",
                AssetsListCmd.CMD_PREFIX,
                AssetsListCmd.CMD_DATA_SEPARATOR,
                chatId,
                AssetsListCmd.CMD_DATA_SEPARATOR);
    }
}
