package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.data.User;
import org.springframework.stereotype.Component;

@Component
public class RewardsCmd extends AbstractSubscriptionSelectionCmd {
    public static final String CMD_PREFIX = "/rewards";

    @Override
    public String getDescription() {
        return "Shows the staking rewards of a specific Cardano account";
    }

    public RewardsCmd() {
        super(CMD_PREFIX);
    }

    @Override
    protected boolean acceptAddressSelection(User user) {
        return user.isStakeAddress();
    }

    @Override
    protected String createCallbackData(long chatId) {
        return String.format("%s%d",
                AccountRewardsCmd.CMD_PREFIX,
                chatId);
    }
}
