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
public class LongCommandDouble implements IBotCommand {
    public static final String CMD_PREFIX = "/long";
    private static final Logger LOG = LoggerFactory.getLogger(LongCommandDouble.class);

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
        LOG.info("Executing long command.. it will take 10 secs to complete, but timeout is set to {} secs", getCommandExecutionTimeoutSeconds());

        try {
            Thread.sleep(1000 * 10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        bot.execute(new SendMessage(update.message().chat().id(), "Hello from Long")
                .disableWebPagePreview(true)
                .parseMode(ParseMode.HTML));
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 3;
    }
}
