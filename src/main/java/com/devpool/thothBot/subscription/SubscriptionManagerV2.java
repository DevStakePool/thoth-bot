package com.devpool.thothBot.subscription;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.KoiosResponseException;
import com.devpool.thothBot.exceptions.SubscriptionException;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountAsset;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.asset.model.AssetAddress;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for validating user subscription based on the number of NFTs that the user owns.
 */
@Component
public class SubscriptionManagerV2 implements Runnable, ISubscriptionManager {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionManagerV2.class);
    private static final int BATCH_SIZE_ASSETS_RETRIEVAL = 10;

    public static final String DEV_POOL_ID = "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv";

    @Value("${thoth.subscription.nft.free-for-all-policy-id}")
    private String subscriptionNftPolicyId;

    @Value("${thoth.subscription.info-batch-size:10}")
    private Integer infoBatchSize;

    @Value("${thoth.subscription.assets-batch-size:10}")
    private Integer assetsBatchSize;

    @Value("classpath:subscription-help.html")
    private Resource subscriptionHelpResource;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    @Lazy
    @Autowired
    private TelegramFacade telegramFacade;
    private String helpText;

    @PostConstruct
    public void post() throws Exception {
        this.helpText = new BufferedReader(new InputStreamReader(
                this.subscriptionHelpResource.getInputStream())).lines().collect(Collectors.joining("\n"));
        this.helpText = this.helpText.replace("%white_small_square", EmojiParser.parseToUnicode(":white_small_square:"));
        this.helpText = this.helpText.replace("%speaking_head_in_silhouette", EmojiParser.parseToUnicode(":speaking_head_in_silhouette:"));
    }


    /**
     * Single shot call used when a user wants to register a new address
     *
     * @param address the new address
     * @param chatId  the chat ID of the user
     * @throws KoiosResponseException in case of issues with koios
     * @throws SubscriptionException  in case it's not possible to subscribe this address
     */
    @Override
    public void verifyUserSubscription(String address, Long chatId) throws KoiosResponseException, SubscriptionException {

        if (!getDevStakers(Map.of(chatId, List.of(address))).getOrDefault(chatId, Collections.emptyList()).isEmpty())
            return;

        LOG.debug("The stake address {} is not delegated to DEV. Checking NFTs for subscriptions and stolen NFTs", address);

        List<User> allUsers = this.userDao.getUsers();

        List<String> allUserSubscriptions = allUsers.stream()
                .filter(u -> u.getChatId().equals(chatId))
                .map(User::getAddress).distinct().toList();

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
        List<String> allStakeAddresses = userSubscribedAccounts.values().stream().flatMap(List::stream).distinct().toList();
        Iterator<List<String>> batchesIter = CollectionsUtil.batchesList(
                allStakeAddresses.stream().filter(User::isStakingAddress).toList(),
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
                    throw new KoiosResponseException("Koios call failed when retrieving the account information %d/%s"
                            .formatted(resp.getCode(), resp.getResponse()));
                }

                resp.getValue().forEach(a -> accountsInDevPool.put(a.getStakeAddress(), DEV_POOL_ID.equals(a.getDelegatedPool())));
            } catch (ApiException e) {
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

    @Override
    public void run() {
        List<User> allUsers = this.userDao.getUsers();
        Map<Long, List<String>> allSubscriptions = new HashMap<>();

        // Organize users
        for (User u : allUsers) {
            allSubscriptions.putIfAbsent(u.getChatId(), new ArrayList<>());
            allSubscriptions.get(u.getChatId()).add(u.getAddress());
        }

        try {
            // Get who's staking already with dev chatId -> #accountsInDev
            var stakingWithDevPool = getDevStakers(allSubscriptions);

            long offset = 0;
            long pagination = 100;
            Result<List<AssetAddress>> addrListResp;
            do {
                var options = Options.builder()
                        .option(Limit.of(pagination))
                        .option(Offset.of(offset)).build();
                offset += pagination;

                addrListResp = koiosFacade.getKoiosService().getAssetService().getPolicyAssetAddressList(subscriptionNftPolicyId, options);

                if (!addrListResp.isSuccessful()) {
                    LOG.error("Can't retrieve the address list of the NFT subscription, due to code {} and response {}",
                            addrListResp.getCode(), addrListResp.getResponse());
                    throw new KoiosResponseException("Can't retrieve the address list of the NFT subscription, due to code  %s and response %s"
                            .formatted(addrListResp.getCode(), addrListResp.getResponse()));
                }

                for (var u : allSubscriptions.entrySet()) {
                    // Check how many NFTs this user has
                    var nftsOwnedByUser = addrListResp.getValue().stream()
                            .filter(a -> u.getValue().contains(a.getStakeAddress()) ||
                                    u.getValue().contains(a.getPaymentAddress())).count();
                    var stakedSubscriptionsWithDev = stakingWithDevPool.getOrDefault(u.getKey(), Collections.emptyList());

                    LOG.debug("The account (chat-id) {} has {} NFT(s), it is subscribed {} time(s), and {} with DEV pool",
                            u.getKey(), nftsOwnedByUser, u.getValue().size(), stakedSubscriptionsWithDev.size());

                    // Starts with 1 is because one subscription is free for everyone.
                    // Also staking with DEV does not count
                    var totalNFTs = 1L;
                    totalNFTs += stakedSubscriptionsWithDev.size();
                    totalNFTs += nftsOwnedByUser;

                    // This can be negative if you have a lot of thoth NFTs
                    var invalidNoOfSubscriptions = allSubscriptions.getOrDefault(u.getKey(), Collections.emptyList()).size() - totalNFTs;
                    LOG.debug("The user {} has a total of {} Thoth NFTs and has {} invalid subscriptions",
                            u.getKey(), totalNFTs, invalidNoOfSubscriptions);

                    if (invalidNoOfSubscriptions <= 0) {
                        continue; // User without invalid subscriptions
                    }


                }

            } while (addrListResp.isSuccessful() && !addrListResp.getValue().isEmpty());

        } catch (Exception e) {
            LOG.error("Could not complete the subscription checks due to exception", e);
            if (Thread.interrupted())
                Thread.currentThread().interrupt();
        }
    }

    private void removeSubscriptionAndNotifyUser(Long chatId, List<String> subscriptionsToBeRemoved) {
        StringBuilder sb = new StringBuilder("Hello, you are missing Thoth NFTs and therefore ")
                .append("the following subscriptions have been removed:\n");
        for (String addr : subscriptionsToBeRemoved) {
            this.userDao.removeAddress(chatId, addr);
            sb.append(EmojiParser.parseToUnicode(":small_blue_diamond:"))
                    .append(AbstractCheckerTask.shortenAddr(addr))
                    .append("\n");
        }

        sb.append("\n").append(this.helpText);

        telegramFacade.sendMessageTo(chatId, sb.toString());
    }

    @Override
    public String getHelpText() {
        return helpText;
    }
}
