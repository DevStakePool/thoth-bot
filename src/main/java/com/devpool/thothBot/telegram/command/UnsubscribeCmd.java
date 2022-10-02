package com.devpool.thothBot.telegram.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ForceReply;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UnsubscribeCmd extends AbstractCommand {

    public static final String CMD_PREFIX = "/unsubscribe";

    @Autowired
    private StakeAddressCmd stakeAddressCmd;

    @Override
    public boolean canTrigger(String message) {
        return message.equals(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        String name = update.message().from().firstName() != null ? update.message().from().firstName() : update.message().from().username();
        this.stakeAddressCmd.getOperationsQueue().get(StakeAddressCmd.StakeOperation.UNSUBSCRIBE).add(update.message().chat().id());

        bot.execute(new SendMessage(update.message().chat().id(), String.format("Hi %s, please specify your stake address (stake1u8yxtug...)", name))
                .replyMarkup(new ForceReply(true)));
    }

    @Override
    public boolean showHelp() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Unsubscribe to stop receiving transaction notifications for a specific wallet";
    }
}
