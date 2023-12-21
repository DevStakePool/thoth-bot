package com.devpool.thothBot.subscription;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.KoiosResponseException;
import com.devpool.thothBot.exceptions.SubscriptionException;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAsset;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for validating user subscription based on the amount of NFTs that the user owns.
 */
@Component
public class SubscriptionManager implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionManager.class);
    public static final String DEV_POOL_ID = "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv";
    @Value("${thoth.subscription.nft.stake-policy-id}")
    private String stakeNftPolicyId;

    @Value("${thoth.subscription.nft.free-for-all-policy-id}")
    private String freeForAllNftPolicyId;


    @Value("${thoth.subscription.info-batch-size:100}")
    private Integer infoBatchSize;

    @Value("${thoth.subscription.assets-batch-size:50}")
    private Integer assetsBatchSize;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    @Autowired
    private TelegramFacade telegramFacade;


    /**
     * Single shot call used when a user wants to register a new address
     *
     * @param address the new address
     * @param chatId  the chat ID of the user
     * @throws KoiosResponseException in case of issues with koios
     * @throws SubscriptionException  is case it's not possible to subscribe this address
     */
    public void verifyUserSubscription(String address, Long chatId) throws KoiosResponseException, SubscriptionException {

        if (!getDevStakers(Map.of(chatId, List.of(address))).getOrDefault(chatId, Collections.emptyList()).isEmpty())
            return;

        LOG.debug("The stake address {} is not delegated to DEV. Checking NFTs for subscriptions and stolen NFTs", address);

        List<User> allUsers = this.userDao.getUsers();

        List<String> allUserSubscriptions = allUsers.stream()
                .filter(u -> u.getChatId().equals(chatId))
                .map(User::getAddress).distinct().collect(Collectors.toList());

        // retrieve all assets belonging to this user
        List<String> userSubscribedAccounts = allUserSubscriptions.stream().filter(User::isStakingAddress).collect(Collectors.toList());
        List<String> userSubscribedAddresses = allUserSubscriptions.stream().filter(User::isNormalAddress).collect(Collectors.toList());
        if (User.isStakingAddress(address))
            userSubscribedAccounts.add(address);
        else
            userSubscribedAddresses.add(address);

        List<AccountAsset> accountAssets = userSubscribedAccounts.isEmpty() ? Collections.emptyList() : getAccountsAssets(Map.of(chatId, userSubscribedAccounts)).get(chatId);
        List<AddressAsset> addressAssets = userSubscribedAddresses.isEmpty() ? Collections.emptyList() : getAddressesAssets(Map.of(chatId, userSubscribedAddresses)).get(chatId);

        // Before proceeding, we need to verify that the address the user wasts to subscribe to,
        // does not have NFTs if it already belongs to someone.
        Optional<User> originalUser = allUsers.stream()
                .filter(u -> !Objects.equals(u.getChatId(), chatId) && u.getAddress().equals(address))
                .findAny();

        if (originalUser.isPresent()) {
            LOG.info("The user {} is trying to add the address {} but the user {} is already subscribed to it. Checking for Thoth NFTs..",
                    chatId, address, originalUser.get().getChatId());
            checkForNftStealing(address, chatId, accountAssets, addressAssets);
        }

        // We should not count the subscribed accounts staking with DEV
        long devStakers = getDevStakers(Map.of(chatId, userSubscribedAccounts)).getOrDefault(chatId, Collections.emptyList()).size();

        // All good so far, sum up all the already subscribed NFTs in various accounts/addresses including the new one
        long noUserSubscriptionNfts = 1;  // with start with 1 is because one subscription is free for everyone
        noUserSubscriptionNfts += accountAssets.stream()
                .filter(a -> a.getPolicyId().equals(this.freeForAllNftPolicyId) || a.getPolicyId().equals(this.stakeNftPolicyId)).count();
        noUserSubscriptionNfts += addressAssets.stream()
                .filter(a -> a.getPolicyId().equals(this.freeForAllNftPolicyId) || a.getPolicyId().equals(this.stakeNftPolicyId)).count();

        // Get current subscriptions
        long noCurrentSubscriptions = allUsers.stream()
                .filter(u -> u.getChatId().equals(chatId)).count() - devStakers;

        LOG.debug("The user {} holds {} subscription NFTs, and it is currently subscribed to {} accounts/addresses",
                chatId, noUserSubscriptionNfts, noCurrentSubscriptions);

        if (noUserSubscriptionNfts - noCurrentSubscriptions <= 0) {
            LOG.info("The user with chat-id {} has exceeded the subscription slots while trying to add the account {}. noUserSubscriptionNfts={}, noCurrentSubscriptions={}",
                    chatId, address, noUserSubscriptionNfts, noCurrentSubscriptions);
            SubscriptionException e = new SubscriptionException(SubscriptionException.ExceptionCause.FREE_SLOTS_EXCEEDED,
                    String.format("The user with chat-id %d has exceeded the subscription slots while trying to add the account %s. noUserSubscriptionNfts=%d, noCurrentSubscriptions=%d",
                            chatId, address, noUserSubscriptionNfts, noCurrentSubscriptions));
            e.setNumberOfOwnedNfts(noUserSubscriptionNfts);
            e.setNumberOfCurrentSubscriptions(noCurrentSubscriptions);
            e.setAddress(address);
            throw e;
        }
    }

    /**
     * Checks for each chat ID (map key) what are the account addresses that are staking with DEV pool and returns
     * the total list of addresses staking with DEV pool, organised by chat ID
     *
     * @param userSubscribedAccounts the map chat-id -> list of account addresses
     * @return the map of how staking addresses that are staking with DEV pool, per chat-id
     * @throws KoiosResponseException in case of API call error
     */
    private Map<Long, List<String>> getDevStakers(Map<Long, List<String>> userSubscribedAccounts) throws KoiosResponseException {
        List<String> allStakeAddresses = userSubscribedAccounts.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
        Iterator<List<String>> batchesIter = CollectionsUtil.batchesList(
                allStakeAddresses.stream().filter(User::isStakingAddress).collect(Collectors.toList()),
                this.infoBatchSize).iterator();

        Map<String, Boolean> accountsInDevPool = new HashMap<>();
        while (batchesIter.hasNext()) {
            try {
                List<String> batch = batchesIter.next();
                Result<List<AccountInfo>> resp = this.koiosFacade.getKoiosService().getAccountService()
                        .getAccountInformation(batch, null);

                if (!resp.isSuccessful()) {
                    LOG.warn("Koios call failed when retrieving the accounts information: {}/{}",
                            resp.getCode(), resp.getResponse());
                    throw new KoiosResponseException(String.format("Koios call failed when retrieving the account information %d/%s",
                            resp.getCode(), resp.getResponse()));
                }

                resp.getValue().forEach(a -> accountsInDevPool.put(a.getStakeAddress(), DEV_POOL_ID.equals(a.getDelegatedPool())));
            } catch (ApiException e) {
                LOG.error("API Exception while querying Koios", e);
                throw new KoiosResponseException("Koios API exception", e);
            }
        }

        // Construct output
        Map<Long, List<String>> output = new HashMap<>();
        for (Map.Entry<Long, List<String>> entry : userSubscribedAccounts.entrySet()) {
            Long chatId = entry.getKey();
            output.putIfAbsent(chatId, new ArrayList<>());
            output.get(chatId).addAll(entry.getValue().stream()
                    .filter(stakeAddr -> accountsInDevPool.getOrDefault(stakeAddr, false))
                    .collect(Collectors.toList()));
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Calculated the following output chat-id vs how many subscribed addresses are in DEV: {}", output);

        return output;
    }

    /**
     * We are checking  if the user is trying to steal someone else's NFTs by subscribing to an account/address with
     * lots of Thoth NFTs. This is possible only if this account/address is not yet subscribed by some other user
     *
     * @param address
     * @param chatId
     * @param accountAssets
     * @param addressAssets
     * @throws SubscriptionException
     */
    private void checkForNftStealing(String address, long chatId, List<AccountAsset> accountAssets, List<AddressAsset> addressAssets) throws SubscriptionException {
        long assetThothNfts;
        if (User.isStakingAddress(address)) {
            assetThothNfts = accountAssets.stream()
                    .filter(a -> a.getStakeAddress().equals(address))
                    .filter(a -> a.getPolicyId().equals(this.freeForAllNftPolicyId) || a.getPolicyId().equals(this.stakeNftPolicyId)).count();
        } else {
            assetThothNfts = addressAssets.stream()
                    .filter(a -> a.getAddress().equals(address))
                    .filter(a -> a.getPolicyId().equals(this.freeForAllNftPolicyId) || a.getPolicyId().equals(this.stakeNftPolicyId)).count();
        }

        if (assetThothNfts > 0) {
            LOG.warn("The user with chat-id {} is trying to add the address {} and it contains {} NFTs, but it already belongs to another user",
                    chatId, address, assetThothNfts);
            SubscriptionException e = new SubscriptionException(SubscriptionException.ExceptionCause.ADDRESS_ALREADY_OWNED_BY_OTHERS,
                    String.format("The user with chat-id %d is trying to add the address %s and it contains %d NFTs, but it already belongs to another user",
                            chatId, address, assetThothNfts));
            e.setAddress(address);
            throw e;
        }
        LOG.info("The address {} is already followed by another user, but it does not contain any thoth NFTs", address);
    }

    private Map<Long, List<AccountAsset>> getAccountsAssets(Map<Long, List<String>> chatAccountAddresses) throws KoiosResponseException {
        long offset = 0;
        long pagination = 1000;
        Result<List<AccountAsset>> assetsResp;

        List<String> accountAddresses = chatAccountAddresses.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
        Iterator<List<String>> batchesIterator = CollectionsUtil.batchesList(accountAddresses, this.assetsBatchSize).iterator();
        Map<String, List<AccountAsset>> assetsOfAccounts = new HashMap<>();

        while (batchesIterator.hasNext()) {

            try {
                List<String> batch = batchesIterator.next();
                do {
                    Options options = Options.builder()
                            .option(Limit.of(1000))
                            .option(Offset.of(offset)).build();
                    offset += pagination;
                    assetsResp = this.koiosFacade.getKoiosService().getAccountService()
                            .getAccountAssets(batch, null, options);

                    if (!assetsResp.isSuccessful()) {
                        LOG.error("Can't retrieve the asset list, due to code {} and response {}",
                                assetsResp.getCode(), assetsResp.getResponse());
                        throw new KoiosResponseException(String.format("Can't retrieve the asset list, due to code %d and response %s",
                                assetsResp.getCode(), assetsResp.getResponse()));
                    }

                    assetsResp.getValue().forEach(a -> {
                        assetsOfAccounts.putIfAbsent(a.getStakeAddress(), new ArrayList<>());
                        assetsOfAccounts.get(a.getStakeAddress()).add(a);
                    });
                } while (assetsResp.isSuccessful() && !assetsResp.getValue().isEmpty());
            } catch (ApiException e) {
                LOG.error("API Exception while querying Koios during batch processing", e);
                throw new KoiosResponseException("API Exception while querying Koios during batch processing", e);
            }
        }

        // create the output
        Map<Long, List<AccountAsset>> outcome = new HashMap<>();
        for (Map.Entry<Long, List<String>> entry : chatAccountAddresses.entrySet()) {
            Long chatId = entry.getKey();
            outcome.putIfAbsent(chatId, new ArrayList<>());
            for (String addr : entry.getValue()) {
                if (assetsOfAccounts.containsKey(addr)) {
                    outcome.get(chatId).addAll(assetsOfAccounts.get(addr));
                }
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("Retrieved account assets: {}", outcome);

        return outcome;
    }

    private Map<Long, List<AddressAsset>> getAddressesAssets(Map<Long, List<String>> chatAddresses) throws KoiosResponseException {
        long offset = 0;
        long pagination = 1000;
        Result<List<AddressAsset>> assetsResp;

        List<String> addresses = chatAddresses.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
        Iterator<List<String>> batchesIterator = CollectionsUtil.batchesList(addresses, this.assetsBatchSize).iterator();
        Map<String, List<AddressAsset>> assetsOfAddresses = new HashMap<>();

        while (batchesIterator.hasNext()) {
            try {

                List<String> batch = batchesIterator.next();
                do {
                    Options options = Options.builder()
                            .option(Limit.of(1000))
                            .option(Offset.of(offset)).build();
                    offset += pagination;
                    assetsResp = this.koiosFacade.getKoiosService().getAddressService()
                            .getAddressAssets(batch, options);

                    if (!assetsResp.isSuccessful()) {
                        LOG.error("Can't retrieve the asset list, due to code {} and response {}",
                                assetsResp.getCode(), assetsResp.getResponse());
                        throw new KoiosResponseException(String.format("Can't retrieve the asset list, due to code %d and response %s",
                                assetsResp.getCode(), assetsResp.getResponse()));
                    }

                    assetsResp.getValue().forEach(a -> {
                        assetsOfAddresses.putIfAbsent(a.getAddress(), new ArrayList<>());
                        assetsOfAddresses.get(a.getAddress()).add(a);
                    });
                } while (assetsResp.isSuccessful() && !assetsResp.getValue().isEmpty());
            } catch (ApiException e) {
                LOG.error("API Exception while querying Koios during the batch processing", e);
                throw new KoiosResponseException("API Exception while querying Koios during the batch processing", e);
            }
        }

        // create the output
        Map<Long, List<AddressAsset>> outcome = new HashMap<>();
        for (Map.Entry<Long, List<String>> entry : chatAddresses.entrySet()) {
            Long chatId = entry.getKey();
            outcome.putIfAbsent(chatId, new ArrayList<>());
            for (String addr : entry.getValue()) {
                if (assetsOfAddresses.containsKey(addr)) {
                    outcome.get(chatId).addAll(assetsOfAddresses.get(addr));
                }
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("Retrieved account assets: {}", outcome);

        return outcome;
    }

    @Override
    public void run() {
        List<User> allUsers = this.userDao.getUsers();
        Map<Long, List<String>> allSubscriptions = new HashMap<>();
        Map<Long, List<String>> addressSubscriptions = new HashMap<>();
        Map<Long, List<String>> accountSubscriptions = new HashMap<>();

        // Prepare the input
        for (User u : allUsers) {
            allSubscriptions.putIfAbsent(u.getChatId(), new ArrayList<>());
            addressSubscriptions.putIfAbsent(u.getChatId(), new ArrayList<>());
            accountSubscriptions.putIfAbsent(u.getChatId(), new ArrayList<>());
            allSubscriptions.get(u.getChatId()).add(u.getAddress());
            if (u.isStakeAddress())
                accountSubscriptions.get(u.getChatId()).add(u.getAddress());
            else
                addressSubscriptions.get(u.getChatId()).add(u.getAddress());
        }

        LOG.info("Checking subscriptions of {} users, of which {} address subscriptions and {} account subscriptions",
                allSubscriptions.size(),
                addressSubscriptions.values().stream().mapToLong(List::size).sum(),
                accountSubscriptions.values().stream().mapToLong(List::size).sum());

        try {
            // Get who's staking already with dev chatId -> #accountsInDev
            Map<Long, List<String>> stakingWithDev = getDevStakers(allSubscriptions);

            // Retrieve all assets of all accounts
            Map<Long, List<AccountAsset>> accountAssets = getAccountsAssets(accountSubscriptions);
            Map<Long, List<AddressAsset>> addressAssets = getAddressesAssets(addressSubscriptions);

            for (Map.Entry<Long, List<String>> e : allSubscriptions.entrySet()) {
                // TODO this part can be parallelized
                Long chatId = e.getKey();
                List<AccountAsset> userAccountAssets = accountAssets.getOrDefault(chatId, Collections.emptyList());
                List<AddressAsset> userAddressAssets = addressAssets.getOrDefault(chatId, Collections.emptyList());

                List<String> userSubscriptions = allSubscriptions.getOrDefault(chatId, Collections.emptyList());
                List<String> stakedSubscriptionsWithDev = stakingWithDev.getOrDefault(chatId, Collections.emptyList());
                if (LOG.isDebugEnabled())
                    LOG.debug("Checking subscription for user {}, who's subscribed to {} addresses/accounts ({} staking with DEV), " +
                                    "against {} account assets and {} address assets",
                            chatId, userSubscriptions.size(),
                            stakedSubscriptionsWithDev, userAccountAssets.size(), userAddressAssets.size());

                // with start with 1 is because one subscription is free for everyone. Also staking with DEV do not count
                long totalNFTs = 1;
                totalNFTs += stakedSubscriptionsWithDev.size();
                totalNFTs += userAccountAssets.stream()
                        .filter(a -> a.getPolicyId().equals(this.freeForAllNftPolicyId) ||
                                a.getPolicyId().equals(this.stakeNftPolicyId)).count();
                totalNFTs += userAddressAssets.stream()
                        .filter(a -> a.getPolicyId().equals(this.freeForAllNftPolicyId) ||
                                a.getPolicyId().equals(this.stakeNftPolicyId)).count();

                // This can be negative if you have a lot of thoth NFTs
                long invalidNoOfSubscriptions = userSubscriptions.size() - totalNFTs;
                LOG.debug("The user {} has a total of {} Thoth NFTs and has {} invalid subscriptions",
                        chatId, totalNFTs, invalidNoOfSubscriptions);

                if (invalidNoOfSubscriptions <= 0)
                    continue; // User without invalid subscriptions

                // We will need to remove subscriptions that are not staking with DEV and notify the user
                // First let's find out all the subscriptions that have THOTH NFTs. We need to exclude these
                List<String> accountsWithThothNfts = userAccountAssets.stream()
                        .filter(a -> a.getPolicyId().equals(this.freeForAllNftPolicyId) ||
                                a.getPolicyId().equals(this.stakeNftPolicyId))
                        .map(AccountAsset::getStakeAddress).collect(Collectors.toList());
                List<String> subscriptionsAllowedToBeRemoved = userSubscriptions.stream()
                        .filter(s -> !stakedSubscriptionsWithDev.contains(s))
                        .filter(s -> !accountsWithThothNfts.contains(s))
                        .collect(Collectors.toList());
                LOG.debug("User {} has currently {} subscriptions ({} not staking with DEV), but {} of them are invalid/saturated",
                        chatId, userSubscriptions.size(), subscriptionsAllowedToBeRemoved, invalidNoOfSubscriptions);

                List<String> subscriptionsToBeRemoved = new ArrayList<>();
                for (int i = 0; i < Math.max(0, invalidNoOfSubscriptions); i++) {
                    if (subscriptionsAllowedToBeRemoved.isEmpty()) {
                        // We got an issue here
                        LOG.error("Should never happen (chatId={}}. The invalidNoOfSubscriptions {} is bigger than the subscriptionsAllowedToBeRemoved {}",
                                chatId, invalidNoOfSubscriptions, subscriptionsAllowedToBeRemoved.size());
                        break;
                    }
                    String subscriptionToBeRemoved = subscriptionsAllowedToBeRemoved.remove(0);
                    subscriptionsToBeRemoved.add(subscriptionToBeRemoved);
                }
                if (LOG.isDebugEnabled())
                    LOG.debug("The following subscriptions will be removed for the user {}: {}",
                            chatId, subscriptionsToBeRemoved);
                removeSubscriptionAndNotifyUser(chatId, subscriptionsToBeRemoved);
            }
        } catch (Exception e) {
            LOG.error("Could not complete the subscription checks due to exception", e);
            if (Thread.interrupted())
                Thread.currentThread().interrupt();
        }
    }

    private void removeSubscriptionAndNotifyUser(Long chatId, List<String> subscriptionsToBeRemoved) {
        StringBuilder sb = new StringBuilder("Hello, you are missing Thoth NFTs and therefore ")
                .append("the following subscriptions have been removed:<br/>");
        for (String addr : subscriptionsToBeRemoved) {
            this.userDao.removeAddress(chatId, addr);
            sb.append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append(addr)
                    .append("<br/>");
        }

        //TODO text should be refined, also with the text above during the subscription
        sb.append("You can obtain additional Thoth NFTs in the following ways: TODO...");

        telegramFacade.sendMessageTo(chatId, sb.toString());
    }
}
