package com.devpool.thothBot.doubles.commands;

import com.devpool.thothBot.telegram.command.IBotCommand;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ErrorCommandDouble implements IBotCommand {
    public static final String CMD_PREFIX = "/error";
    private static final Logger LOG = LoggerFactory.getLogger(ErrorCommandDouble.class);

    @Override
    public boolean canTrigger(String username, String message) {
        return message.equals(CMD_PREFIX);
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
        return getClass().getName();
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        // Nothing to do for now
        LOG.info("Executing error command, and throwing an exception");
        throw new RuntimeException("Dummy error exception");
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 1;
    }
}
