package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.UserNotFoundException;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DetailsCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(DetailsCmd.class);
    public static final String CMD_PREFIX = "/d";

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

        String msgText = update.callbackQuery().data().trim();
        String[] msgParts = msgText.split(" ");
        if (msgParts.length != 2) {
            LOG.warn("Invalid message {}. Expected prefix + user_id", msgText);
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Invalid {} command", CMD_PREFIX)));
            return;
        }

        String userId = msgParts[1];
        try {
            User user = userDao.getUser(Long.parseLong(userId));
            LOG.info("{}", user);
        } catch (UserNotFoundException e) {
            throw new RuntimeException(e);
        }
        LOG.warn("Getting assets for account {}", userId);
        //TODO implement me
    }

}
