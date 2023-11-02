package com.devpool.thothBot.telegram;

import com.devpool.thothBot.telegram.command.IBotCommand;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class TelegramMessageCallable implements Callable<Boolean> {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramMessageCallable.class);
    private final Update update;
    private final TelegramBot bot;
    private final IBotCommand command;

    public TelegramMessageCallable(IBotCommand command, Update update, TelegramBot bot) {
        this.command = command;
        this.update = update;
        this.bot = bot;
    }

    @Override
    public Boolean call() {
        try {
            this.command.execute(this.update, this.bot);
        } catch (Exception e) {
            LOG.error("Error while processing the command {}, with the update {}", this.command.getClass().getName(), this.update, e);
            if (Thread.interrupted())
                Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
