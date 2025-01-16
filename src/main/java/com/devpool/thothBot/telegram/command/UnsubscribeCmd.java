package com.devpool.thothBot.telegram.command;

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
