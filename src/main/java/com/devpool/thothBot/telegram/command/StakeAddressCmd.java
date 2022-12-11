package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.MaxRegistrationsExceededException;
import com.devpool.thothBot.koios.KoiosFacade;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAddress;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.network.model.Tip;
import rest.koios.client.backend.factory.options.Options;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class StakeAddressCmd extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(StakeAddressCmd.class);
    public static final String CMD_PREFIX = "stake1";

    public enum StakeOperation {
        SUBSCRIBE,
        UNSUBSCRIBE
    }

    private Map<StakeOperation, List<Long>> operationsQueue;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    @Override
    public boolean canTrigger(String message) {
        return message.startsWith(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp() {
        return false;
    }

    @Override
    public String getDescription() {
        // Not in the help
        return null;
    }

    public StakeAddressCmd() {
        this.operationsQueue = new ConcurrentHashMap<>();
        this.operationsQueue.put(StakeOperation.SUBSCRIBE, new CopyOnWriteArrayList<>());
        this.operationsQueue.put(StakeOperation.UNSUBSCRIBE, new CopyOnWriteArrayList<>());
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();
        if (this.operationsQueue.get(StakeOperation.UNSUBSCRIBE).contains(chatId))
            unsubscribeNewStakeAddress(update, bot);
        else if (this.operationsQueue.get(StakeOperation.SUBSCRIBE).contains(chatId))
            subscribeNewStakeAddress(update, bot);
        else {
            LOG.debug("Called Stake Address command but the chat id was not found in both SUBSCRIBE and UNSUBSCRIBE queues");
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Please specify the operation first: %s or %s", SubscribeCmd.CMD_PREFIX, UnsubscribeCmd.CMD_PREFIX)));
        }
    }

    private void unsubscribeNewStakeAddress(Update update, TelegramBot bot) {
        String name = update.message().from().firstName() != null ? update.message().from().firstName() : update.message().from().username();
        String stakeAddr = update.message().text().trim();

        boolean outcome = this.userDao.removeStakeAddress(update.message().chat().id(), stakeAddr);

        if (outcome) {
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("You have successfully unsubscribed the stake address %s", stakeAddr)));
            this.operationsQueue.get(StakeOperation.UNSUBSCRIBE).remove(update.message().chat().id());
        } else {
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Sorry %s! I could not find the stake address %s associated to this chat", name, stakeAddr)));
        }
    }

    public void subscribeNewStakeAddress(Update update, TelegramBot bot) {

        String name = update.message().from().firstName() != null ? update.message().from().firstName() : update.message().from().username();
        String stakeAddr = update.message().text().trim();

        try {
            Result<List<AccountAddress>> addresses = this.koiosFacade.getKoiosService().getAccountService().getAccountAddresses(
                    Arrays.asList(stakeAddr),null);

            if (!addresses.isSuccessful()) {
                LOG.warn("Unsuccessful KOIOS call during the subscribe of the stake address {}. {} {}",
                        stakeAddr, addresses.getCode(), addresses.getResponse());

                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("Sorry %s! Something went wrong when collecting the information about the stake address %s", name, stakeAddr)));

                return;
            }

            LOG.info(String.valueOf(addresses.getValue()));
            if (addresses.getValue().isEmpty()) throw new ApiException("Cannot find stake address");

            // Get block height
            Result<Tip> tipResult = this.koiosFacade.getKoiosService().getNetworkService().getChainTip();

            if (!tipResult.isSuccessful()) {
                LOG.warn("Unsuccessful KOIOS call during the subscribe of the stake address {}. {} {}",
                        stakeAddr, tipResult.getCode(), tipResult.getResponse());

                bot.execute(new SendMessage(update.message().chat().id(),
                        String.format("Sorry %s! Something went wrong when collecting the mainnet tip", name)));

                return;
            }
            userDao.addNewUser(
                    new User(update.message().chat().id(),
                            stakeAddr, tipResult.getValue().getBlockNo(), tipResult.getValue().getEpochNo()));

            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Thank you %s! From now on you will receive updates every time a transaction appears or when you receive funds", name)));

            this.operationsQueue.get(StakeOperation.SUBSCRIBE).remove(update.message().chat().id());
        } catch (ApiException e) {
            LOG.warn("Error in command stake address: " + e);
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("The stake address seems to be invalid: %s", e.getMessage())));
            return;
        } catch (MaxRegistrationsExceededException e) {
            LOG.warn("Max number of registrations exceeded for user " + update.message().chat().id() + ": " + e.getMessage());
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("Max number of registrations exceeded. You can only register a maximum of %d wallets. Try to de-register some.",
                            e.getMaxRegistrationsAllowed())));
            return;
        } catch (DuplicateKeyException e) {
            LOG.info("Duplicated key when registering a new wallet {}", e.toString());
            bot.execute(new SendMessage(update.message().chat().id(),
                    String.format("It looks like the stake address %s has been already registered in this chat.", stakeAddr)));
        }
    }


    public Map<StakeOperation, List<Long>> getOperationsQueue() {
        return operationsQueue;
    }
}
