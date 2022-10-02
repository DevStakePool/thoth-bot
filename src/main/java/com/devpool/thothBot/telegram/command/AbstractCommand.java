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
     * If this command has to be shown in the help description
     *
     * @return true if the command is included in the help, false otherwise
     */
    public abstract boolean showHelp();

    /**
     * The description of this command
     * @return the description to show in the help
     */
    public abstract String getDescription();

    /**
     * Execute the command
     *
     * @param update the Telegram update received
     * @param bot    The Telegram bot
     */
    public abstract void execute(Update update, TelegramBot bot);
}
