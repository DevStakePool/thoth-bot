package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.exceptions.UserNotFoundException;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UnsubscribeSelectionCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(UnsubscribeSelectionCmd.class);
    public static final String CMD_PREFIX = "/us";

    @Autowired
    private AddressCmd addressCmd;

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
        LOG.debug("unsubscribe selected callback data (messageId={}): {} -> userID={}",
                messageId, msgText, userId);

        try {
            LOG.debug("Getting staking rewards for user {}", userId);
            var user = userDao.getUser(Long.parseLong(userId));

            boolean outcome = this.userDao.removeAddress(update.message().chat().id(), user.getAddress());

            if (outcome) {
                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("You have successfully unsubscribed the address %s", user.getAddress())));
            } else {
                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("Sorry! I could not find the address %s associated to this chat", user.getAddress())));
            }
        } catch (UserNotFoundException e) {
            bot.execute(new SendMessage(chatId, String.format("The user with ID %s cannot be found.", userId)));
        } catch (Exception e) {
            LOG.error("Unknown error when unsubscribing wallet for user-id {}", userId, e);
            bot.execute(new SendMessage(chatId,
                    String.format("Unknown error when unsubscribing address for user-id %s. %s", userId, e)));
        }
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 30L;
    }
}
