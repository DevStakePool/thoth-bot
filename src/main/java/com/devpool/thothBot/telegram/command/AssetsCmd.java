package com.devpool.thothBot.telegram.command;

import org.springframework.stereotype.Component;

@Component
public class AssetsCmd extends AbstractSubscriptionSelectionCmd {
    public static final String CMD_PREFIX = "/assets";

    @Override
    public String getDescription() {
        return "Shows the assets of a specific Cardano account/address";
    }

    public AssetsCmd() {
        super(CMD_PREFIX);
    }

    @Override
    protected String createCallbackData(long chatId) {
        return String.format("%s%s%d%s0",
                AssetsListCmd.CMD_PREFIX,
                AssetsListCmd.CMD_DATA_SEPARATOR,
                chatId,
                AssetsListCmd.CMD_DATA_SEPARATOR);
    }
}
