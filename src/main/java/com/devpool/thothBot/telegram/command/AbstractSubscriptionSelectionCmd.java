package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractSubscriptionSelectionCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSubscriptionSelectionCmd.class);

    protected final String commandPrefix;

    @Override
    public boolean canTrigger(String username, String message) {
        return message.trim().equals(commandPrefix);
    }

    @Override
    public String getCommandPrefix() {
        return commandPrefix;
    }

    @Override
    public boolean showHelp(String username) {
        return true;
    }

    @Override
    public String getDescription() {
        return "replace me";
    }

    protected AbstractSubscriptionSelectionCmd(String commandPrefix) {
        this.commandPrefix = commandPrefix;
    }

    /**
     * Replace this to filter the user. Default implementation does not filter anything
     *
     * @param user the user to filter
     * @return true if not filtered, false otherwise
     */
    protected boolean acceptAddressSelection(User user) {
        return true;
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();

        Map<String, Long> addresses = this.userDao.getUsers().stream().filter(
                        u -> u.getChatId().equals(chatId))
                .filter(this::acceptAddressSelection)
                .collect(Collectors.toMap(User::getAddress, User::getId));

        if (addresses.isEmpty()) {
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("You have not yet subscribed any Cardano wallet.%nPlease try %s", SubscribeCmd.CMD_PREFIX)));
            return;
        }

        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(createInlineKeyboardButtons(addresses));
        LOG.debug("Created {} inline markup rows", inlineKeyboard.inlineKeyboard().length);
        bot.execute(new SendMessage(chatId, "Please select an account").replyMarkup(inlineKeyboard));
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 4;
    }

    protected InlineKeyboardButton[][] createInlineKeyboardButtons(Map<String, Long> addresses) {
        Map<String, String> handles = getAdaHandleForAccount(addresses.keySet().toArray(new String[0]));
        int inputSize = addresses.size();
        int numArrays = (int) Math.ceil((double) inputSize / 2);
        InlineKeyboardButton[][] outputArray = new InlineKeyboardButton[numArrays][2];

        for (int i = 0; i < numArrays; i++) {
            int startIndex = i * 2;
            int endIndex = Math.min(startIndex + 2, inputSize);
            List<String> sublist = new ArrayList<>(addresses.keySet()).subList(startIndex, endIndex);
            outputArray[i] = sublist
                    .stream()
                    .map(e -> new InlineKeyboardButton(handles.get(e))
                            .callbackData(createCallbackData(addresses.get(e))))
                    .toArray(InlineKeyboardButton[]::new);
        }

        return outputArray;
    }

    protected abstract String createCallbackData(long userId);
}
