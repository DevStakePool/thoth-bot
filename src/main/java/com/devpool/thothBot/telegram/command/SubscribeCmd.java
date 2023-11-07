package com.devpool.thothBot.telegram.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ForceReply;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubscribeCmd implements IBotCommand {

    public static final String CMD_PREFIX = "/subscribe";

    @Autowired
    private AddressCmd addressCmd;

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
        return "Subscribes to receive transaction notifications for a specific account or single address";
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        String name = update.message().from().firstName() != null ? update.message().from().firstName() : update.message().from().username();
        this.addressCmd.getOperationsQueue().get(AddressCmd.CmdOperation.SUBSCRIBE).add(update.message().chat().id());

        bot.execute(new SendMessage(update.message().chat().id(), String.format("Hi %s, please send your address (stake1... or addr1...)", name))
                .replyMarkup(new ForceReply(false)));
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 3;
    }
}
