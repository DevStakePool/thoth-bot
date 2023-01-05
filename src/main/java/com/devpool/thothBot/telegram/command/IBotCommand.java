package com.devpool.thothBot.telegram.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;

public interface IBotCommand {

    /**
     * Method will check if the message can trigger this command
     *
     * @param message
     * @return true if the message triggers the command, false otherwise
     */
    boolean canTrigger(String message);

    /**
     * Get the command prefix
     *
     * @return the command prefix or null if none
     */
    String getCommandPrefix();

    /**
     * If this command has to be shown in the help description
     *
     * @return true if the command is included in the help, false otherwise
     */
    boolean showHelp();

    /**
     * The description of this command
     * @return the description to show in the help
     */
    String getDescription();

    /**
     * Execute the command
     *
     * @param update the Telegram update received
     * @param bot    The Telegram bot
     */
    void execute(Update update, TelegramBot bot);
}
