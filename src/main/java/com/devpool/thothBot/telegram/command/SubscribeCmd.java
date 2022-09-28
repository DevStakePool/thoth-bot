package com.devpool.thothBot.telegram.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubscribeCmd extends AbstractCommand {

    public static final String CMD_PREFIX = "/subscribe";

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
        this.stakeAddressCmd.getOperationsQueue().get(StakeAddressCmd.StakeOperation.SUBSCRIBE).add(update.message().chat().id());

        bot.execute(new SendMessage(update.message().chat().id(), String.format("Hi %s, please send your stake address (stake1u8yxtug...)", name)));
    }
}
