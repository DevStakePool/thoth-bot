package com.devpool.thothBot.telegram.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ForceReply;
import com.pengrad.telegrambot.request.SendMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UnsubscribeCmd extends AbstractSubscriptionSelectionCmd {

    public static final String CMD_PREFIX = "/unsubscribe";

    protected UnsubscribeCmd() {
        super(CMD_PREFIX);
    }

    @Override
    protected String createCallbackData(long userId) {
        return String.format("%s%d", UnsubscribeSelectionCmd.CMD_PREFIX, userId);
    }

    @Override
    public String getDescription() {
        return "Unsubscribes to stop receiving transaction notifications for a specific account or single address";
    }
}
