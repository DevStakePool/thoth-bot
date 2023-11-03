package com.devpool.thothBot.telegram;

import com.devpool.thothBot.telegram.command.IBotCommand;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class TelegramMessageCallable implements Callable<Boolean> {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramMessageCallable.class);
    private final Update update;
    private final TelegramBot bot;
    private final IBotCommand command;
    private final String from;
    private final String payload;
    private final Long id;

    public TelegramMessageCallable(Long id, IBotCommand command, Update update, TelegramBot bot, String from, String payload) {
        this.id = id;
        this.command = command;
        this.update = update;
        this.bot = bot;
        this.from = from;
        this.payload = payload;
    }

    @Override
    public Boolean call() {
        LOG.debug("Command {} has been started by {}", payload, from);
        try {
            this.command.execute(this.update, this.bot);
        } catch (Exception e) {
            LOG.error("Error while processing the command {}, with payload {}, from user {}: {}",
                    this.command.getClass().getName(), this.payload, this.from, e, e);
            if (Thread.interrupted())
                Thread.currentThread().interrupt();
            bot.execute(new SendMessage(id,
                    "Command " + this.payload + " execution failed with an unexpected error: " + e));
            return false;
        }
        return true;
    }
}
