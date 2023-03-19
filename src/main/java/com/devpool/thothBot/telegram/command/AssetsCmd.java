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

//FIXME 11
@Component
public class AssetsCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AssetsCmd.class);
    public static final String CMD_PREFIX = "/assets";

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
        return "Shows the assets of a specific Cardano account/wallet";
    }

    public AssetsCmd() {
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();

        Map<String, Long> stakeAddresses = this.userDao.getUsers().stream().filter(
                u -> u.getChatId().equals(chatId)).collect(Collectors.toMap(User::getAddress, User::getId));

        if (stakeAddresses.isEmpty()) {
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("You have not yet registered any Cardano account. Please try %s", SubscribeCmd.CMD_PREFIX)));
            return;
        }

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(createInlineKeyboardButtons(stakeAddresses));
        LOG.debug("Created {} inline markup rows", inlineKeyboard.inlineKeyboard().length);
        bot.execute(new SendMessage(chatId, "Please select an account").replyMarkup(inlineKeyboard));
    }

    private InlineKeyboardButton[][] createInlineKeyboardButtons(Map<String, Long> stakeAddresses) {
        Map<String, String> handles = getAdaHandleForAccount(stakeAddresses.keySet().toArray(new String[0]));
        int inputSize = stakeAddresses.size();
        int numArrays = (int) Math.ceil((double) inputSize / 2);
        InlineKeyboardButton[][] outputArray = new InlineKeyboardButton[numArrays][2];

        for (int i = 0; i < numArrays; i++) {
            int startIndex = i * 2;
            int endIndex = Math.min(startIndex + 2, inputSize);
            List<String> sublist = stakeAddresses.keySet().stream().collect(Collectors.toList()).subList(startIndex, endIndex);
            outputArray[i] = sublist.stream().map(e -> new InlineKeyboardButton(handles.get(e))
                            .callbackData(DetailsCmd.CMD_PREFIX + " " + stakeAddresses.get(e)))
                    .collect(Collectors.toList()).toArray(new InlineKeyboardButton[0]);
        }

        return outputArray;
    }

}
