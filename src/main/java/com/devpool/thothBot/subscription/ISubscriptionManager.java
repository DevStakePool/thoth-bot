package com.devpool.thothBot.subscription;

import com.devpool.thothBot.exceptions.KoiosResponseException;
import com.devpool.thothBot.exceptions.SubscriptionException;

public interface ISubscriptionManager {
    void verifyUserSubscription(String address, Long chatId) throws KoiosResponseException, SubscriptionException;

    String getHelpText();
}
