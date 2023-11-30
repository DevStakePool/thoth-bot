package com.devpool.thothBot.subscription;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.exceptions.KoiosResponseException;
import com.devpool.thothBot.exceptions.SubscriptionException;
import com.devpool.thothBot.koios.KoiosFacade;
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
public class SubscriptionManager {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionManager.class);
    public static final String DEV_POOL_ID = "pool1e2tl2w0x4puw0f7c04mznq4qz6kxjkwhvuvusgf2fgu7q4d6ghv";
    @Value("${thoth.subscription.nft.stake-policy-id}")
    private String stakeNftPolicyId;

    @Value("${thoth.subscription.nft.free-for-all-policy-id}")
    private String freeForAllNftPolicyId;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private UserDao userDao;

    public void verifyUserSubscription(String address, Long chatId) throws KoiosResponseException, SubscriptionException {
        if (User.isStakingAddress(address) && isAccountStakingWithDev(address, chatId))
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

        List<AccountAsset> accountAssets = userSubscribedAccounts.isEmpty() ? Collections.emptyList() : getAccountsAssets(userSubscribedAccounts, chatId);
        List<AddressAsset> addressAssets = userSubscribedAddresses.isEmpty() ? Collections.emptyList() : getAddressesAssets(userSubscribedAddresses, chatId);

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
        long devStakers = userSubscribedAccounts.isEmpty() ? 0 : countDevStakers(userSubscribedAccounts, chatId);

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

    private long countDevStakers(List<String> userSubscribedAccounts, long chatId) throws KoiosResponseException {
        try {
            Result<List<AccountInfo>> resp = this.koiosFacade.getKoiosService().getAccountService().getAccountInformation(userSubscribedAccounts, null);

            if (!resp.isSuccessful()) {
                LOG.warn("Koios call failed when retrieving the accounts information subscribed by chat-id {}: {}/{}",
                        chatId, resp.getCode(), resp.getResponse());
                throw new KoiosResponseException(String.format("Koios call failed when retrieving the account information for chat-id %d: %d/%s",
                        chatId, resp.getCode(), resp.getResponse()));
            }
            long delegatedToDev = resp.getValue().stream().filter(i -> DEV_POOL_ID.equals(i.getDelegatedPool())).count();
            LOG.debug("The chat-id {} is subscribed to {} account(s) delegated to DEV)", chatId, delegatedToDev);
            return delegatedToDev;
        } catch (ApiException e) {
            LOG.error("API Exception while querying Koios", e);
            throw new KoiosResponseException("Koios API exception", e);
        }
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

    private boolean isAccountStakingWithDev(String address, long chatId) throws KoiosResponseException {
        try {
            // We need to first check if this account is staking to DEV pool. In this case it's fine to be subscribed
            Result<List<AccountInfo>> accountInfoRes = this.koiosFacade.getKoiosService().getAccountService().getAccountInformation(
                    List.of(address), null);
            if (!accountInfoRes.isSuccessful()) {
                LOG.warn("Koios call failed when retrieving the account information for chat-id {}: {}/{}",
                        chatId, accountInfoRes.getCode(), accountInfoRes.getResponse());
                throw new KoiosResponseException(String.format("Koios call failed when retrieving the account information for chat-id %d: %d/%s",
                        chatId, accountInfoRes.getCode(), accountInfoRes.getResponse()));
            }

            if (accountInfoRes.getValue().isEmpty()) {
                LOG.error("No account info found for the stake address {}", address);
                throw new KoiosResponseException(String.format("No account info found for the stake address %s", address));
            }

            String delegatedToPool = accountInfoRes.getValue().get(0).getDelegatedPool();
            LOG.debug("Is the account {} delegated to DEV pool? {})", address, DEV_POOL_ID.equals(delegatedToPool));

            return DEV_POOL_ID.equals(delegatedToPool);
        } catch (ApiException e) {
            LOG.error("API Exception while querying Koios", e);
            throw new KoiosResponseException("Koios API exception", e);
        }
    }

    private List<AccountAsset> getAccountsAssets(List<String> accountAddresses, long chatId) throws KoiosResponseException {
        List<AccountAsset> outcome = new ArrayList<>();
        long offset = 0;
        long pagination = 1000;
        Result<List<AccountAsset>> assetsResp;
        try {
            do {
                Options options = Options.builder()
                        .option(Limit.of(1000))
                        .option(Offset.of(offset)).build();
                offset += pagination;
                assetsResp = this.koiosFacade.getKoiosService().getAccountService()
                        .getAccountAssets(accountAddresses, null, options);

                if (!assetsResp.isSuccessful()) {
                    LOG.error("Can't retrieve the asset list for user chat-id {}, due to code {} and response {}",
                            chatId, assetsResp.getCode(), assetsResp.getResponse());
                    throw new KoiosResponseException(String.format("Can't retrieve the asset list for user chat-id %d, due to code %d and response %s",
                            chatId, assetsResp.getCode(), assetsResp.getResponse()));
                }

                outcome.addAll(assetsResp.getValue());

            } while (assetsResp.isSuccessful() && !assetsResp.getValue().isEmpty());
        } catch (ApiException e) {
            LOG.error("API Exception while querying Koios", e);
            throw new KoiosResponseException("Koios API exception", e);
        }

        return outcome;
    }

    private List<AddressAsset> getAddressesAssets(List<String> addresses, long chatId) throws KoiosResponseException {
        List<AddressAsset> outcome = new ArrayList<>();
        long offset = 0;
        long pagination = 1000;
        Result<List<AddressAsset>> assetsResp;
        try {
            do {
                Options options = Options.builder()
                        .option(Limit.of(1000))
                        .option(Offset.of(offset)).build();
                offset += pagination;
                assetsResp = this.koiosFacade.getKoiosService().getAddressService()
                        .getAddressAssets(addresses, options);

                if (!assetsResp.isSuccessful()) {
                    LOG.error("Can't retrieve the asset list for user chat-id {}, due to code {} and response {}",
                            chatId, assetsResp.getCode(), assetsResp.getResponse());
                    throw new KoiosResponseException(String.format("Can't retrieve the asset list for user chat-id %d, due to code %d and response %s",
                            chatId, assetsResp.getCode(), assetsResp.getResponse()));
                }

                outcome.addAll(assetsResp.getValue());

            } while (assetsResp.isSuccessful() && !assetsResp.getValue().isEmpty());
        } catch (ApiException e) {
            LOG.error("API Exception while querying Koios", e);
            throw new KoiosResponseException("Koios API exception", e);
        }

        return outcome;
    }
}
