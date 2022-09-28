package com.devpool.thothBot.telegram.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;

public abstract class AbstractCommand {

    /**
     * Method will check if the message can trigger this command
     *
     * @param message
     * @return true if the message triggers the command, false otherwise
     */
    public abstract boolean canTrigger(String message);

    /**
     * Get the command prefix
     *
     * @return the command prefix or null if none
     */
    public abstract String getCommandPrefix();

    /**
     * Execute the command
     *
     * @param update the Telegram update received
     * @param bot    The Telegram bot
     */
    public abstract void execute(Update update, TelegramBot bot);
}
