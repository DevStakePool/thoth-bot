package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.KoiosResponseException;
import com.devpool.thothBot.exceptions.SubscriptionException;
import com.devpool.thothBot.koios.AssetFacade;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.subscription.ISubscriptionManager;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.network.model.Tip;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AddressCmd implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AddressCmd.class);
    public static final String CMD_PREFIX_STAKE = "stake1";
    public static final String CMD_PREFIX_ADDR = "addr1";


    public enum CmdOperation {
        SUBSCRIBE,
        UNSUBSCRIBE
    }

    private Map<CmdOperation, List<Long>> operationsQueue;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AssetFacade assetFacade;

    @Autowired
    private ISubscriptionManager subscriptionManager;

    @Override
    public boolean canTrigger(String username, String message) {
        return message.startsWith(CMD_PREFIX_STAKE) || message.startsWith(CMD_PREFIX_ADDR);
    }

    @Override
    public String getCommandPrefix() {
        return null;
    }

    @Override
    public boolean showHelp(String username) {
        return false;
    }

    @Override
    public String getDescription() {
        // Not in the help
        return null;
    }

    public AddressCmd() {
        this.operationsQueue = new ConcurrentHashMap<>();
        this.operationsQueue.put(CmdOperation.SUBSCRIBE, new CopyOnWriteArrayList<>());
        this.operationsQueue.put(CmdOperation.UNSUBSCRIBE, new CopyOnWriteArrayList<>());
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();

        if (this.operationsQueue.get(CmdOperation.UNSUBSCRIBE).contains(chatId)) {
            unsubscribeNewAddress(update, bot);
            this.operationsQueue.get(CmdOperation.UNSUBSCRIBE).remove(chatId);
        } else if (this.operationsQueue.get(CmdOperation.SUBSCRIBE).contains(chatId)) {
            subscribeNewAddress(update, bot);
            this.operationsQueue.get(CmdOperation.SUBSCRIBE).remove(chatId);
        } else {
            LOG.debug("Called Address command but the chat id was not found in both SUBSCRIBE and UNSUBSCRIBE queues");
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Please specify the operation first: %s or %s", SubscribeCmd.CMD_PREFIX, UnsubscribeCmd.CMD_PREFIX)));
        }
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 10;
    }

    private void unsubscribeNewAddress(Update update, TelegramBot bot) {
        String name = update.message().from().firstName() != null ? update.message().from().firstName() : update.message().from().username();
        String addr = update.message().text().trim();

        boolean outcome = this.userDao.removeAddress(update.message().chat().id(), addr);

        if (outcome) {
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("You have successfully unsubscribed the address %s", addr)));
        } else {
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Sorry %s! I could not find the address %s associated to this chat", name, addr)));
        }
    }

    public void subscribeNewAddress(Update update, TelegramBot bot) {

        String name = update.message().from().firstName() != null ? update.message().from().firstName() : update.message().from().username();
        String addr = update.message().text().trim();

        try {
            if (!isValidAddress(addr)) {
                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("The provided address \"%s\" does not exist on-chain or it is not valid", addr)));

                return;
            }

            this.subscriptionManager.verifyUserSubscription(addr, update.message().chat().id());

            // Get block height
            Result<Tip> tipResult = this.koiosFacade.getKoiosService().getNetworkService().getChainTip();
            if (!tipResult.isSuccessful()) {
                LOG.warn("Unsuccessful KOIOS call during the subscribe of the address {}. {} {}",
                        addr, tipResult.getCode(), tipResult.getResponse());

                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("Sorry %s! Something went wrong when collecting the mainnet tip", name)));

                return;
            }


            userDao.addNewUser(
                    new User(update.message().chat().id(),
                            addr, tipResult.getValue().getBlockNo(), tipResult.getValue().getEpochNo(),
                            System.currentTimeMillis() / 1000));

            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Thank you %s! From now on you will receive updates every time a transaction appears or when you receive funds", name)));

            // Submit the asset task to quickly cache the user assets
            this.assetFacade.refreshAssetsForUserNow(addr);

        } catch (ApiException e) {
            LOG.warn("Error in command address: {}", e, e);
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("The address seems to be invalid: %s", e.getMessage())));
        } catch (DuplicateKeyException e) {
            LOG.info("Duplicated key when registering a new wallet {}", e.toString());
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("It looks like the address %s has been already registered in this chat.", addr)));
        } catch (KoiosResponseException e) {
            LOG.warn("Something went wrong with the Koios call", e);
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Could not get on-chain information. Please try later again: %s", e.getMessage())));

        } catch (SubscriptionException e) {
            switch (e.getExceptionCause()) {
                case FREE_SLOTS_EXCEEDED: {
                    LOG.warn("Max number of subscriptions exceeded for user {}: {}", update.message().chat().id(), e.getMessage());
                    bot.execute(new SendMessage(update.message().chat().id(),
                            String.format("Max number of subscriptions exceeded. You are currently subscribed to %d/%d addresses%n%n%s",
                                    e.getNumberOfOwnedNfts(),
                                    e.getNumberOfCurrentSubscriptions(),
                                    this.subscriptionManager.getHelpText()))
                            .parseMode(ParseMode.HTML).disableWebPagePreview(true));
                    break;
                }
                case ADDRESS_ALREADY_OWNED_BY_OTHERS: {
                    LOG.warn("Address already used by other users and contains Thoth NFTs user: {}: {}",
                            update.message().chat().id(), e.getMessage());
                    bot.execute(new SendMessage(update.message().chat().id(),
                            String.format("The address %s that you are trying to subscribe to, contains Thoth NFTs but " +
                                            "it is currently used by another Telegram user. If you believe this address " +
                                            "is yours, please seek out support in the <a href=\"https://t.me/cardano_thoth_bot_discussions\">Cardano Thoth Bot Discussions</a> channel",
                                    e.getAddress())).parseMode(ParseMode.HTML).disableWebPagePreview(true));
                    break;

                }
            }
        }
    }

    /**
     * Validates if addr (stake or base) is valid and existing one
     *
     * @param addr stake or base address
     * @return true if the addr is valid, false otherwise
     */
    private boolean isValidAddress(String addr) {
        try {
            if (User.isStakingAddress(addr)) {
                // Stake address
                Result<List<AccountInfo>> accountInfo = this.koiosFacade.getKoiosService().getAccountService().getAccountInformation(List.of(addr), null);
                if (!accountInfo.isSuccessful() || accountInfo.getValue().isEmpty())
                    return false;
            } else {
                // Base address
                Result<AddressInfo> addressInfo = this.koiosFacade.getKoiosService().getAddressService().getAddressInformation(addr);
                if (!addressInfo.isSuccessful() || addressInfo.getValue() == null)
                    return false;
            }
        } catch (ApiException e) {
            LOG.warn("Invalid address {}. Error: {}", addr, e.toString());
            return false;
        }
        return true;
    }


    public Map<CmdOperation, List<Long>> getOperationsQueue() {
        return operationsQueue;
    }
}
