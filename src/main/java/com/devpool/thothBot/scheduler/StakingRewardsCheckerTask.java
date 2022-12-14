package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.AssetsDao;
import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountReward;
import rest.koios.client.backend.api.account.model.AccountRewards;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.network.model.Tip;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Component
public class StakingRewardsCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(StakingRewardsCheckerTask.class);
    private static final long DEFAULT_PAGINATION_SIZE = 1000;
    public static final double LOVELACE = 1000000.0;
    public static final String ADA_SYMBOL = " " + '\u20B3';
    public static final String CARDANO_SCAN_STAKE_KEY = "https://cardanoscan.io/stakekey/";
    public static final String CARDANO_SCAN_STAKE_POOL = "https://cardanoscan.io/pool/";
    private static final int USERS_BATCH_SIZE = 50;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AssetsDao assetsDao;

    @Autowired
    private KoiosFacade koiosFacade;

    @Autowired
    private TelegramFacade telegramFacade;

    @Override
    public void run() {
        LOG.debug("Starting thread to check staking rewards");

        try {
            Result<Tip> chainTipRes = this.koiosFacade.getKoiosService().getNetworkService().getChainTip();
            if (!chainTipRes.isSuccessful()) {
                LOG.warn("Cannot get the chain tip from main-net: {}", chainTipRes.getResponse());
                return;
            }

            Integer currentEpochNumber = chainTipRes.getValue().getEpochNo();

            LOG.info("Checking staking rewards for {} wallets", this.userDao.getUsers().size());
            Iterator<List<User>> batchIterator = batches(userDao.getUsers(), USERS_BATCH_SIZE).iterator();

            while (batchIterator.hasNext()) {
                List<User> usersBatch = batchIterator.next();
                LOG.debug("Processing users batch size {}", usersBatch.size());

                processUserBatch(usersBatch, currentEpochNumber);
            }
        } catch (Throwable t) {
            LOG.error("Caught throwable while checking wallet staking rewards", t);
        }
    }

    private void processUserBatch(List<User> usersBatch, Integer currentEpochNumber) {
        Map<String, User> accountsToProcess = new HashMap<>();
        for (User u : usersBatch) {
            if (Objects.equals(u.getLastEpochNumber(), currentEpochNumber))
                continue;

            if (u.getLastEpochNumber() > currentEpochNumber) {
                LOG.error("User last epoch number {} greater than the current one from the Tip {}!", u.getLastEpochNumber(), currentEpochNumber);
                continue;
            }
            accountsToProcess.put(u.getStakeAddr(), u);
        }

        LOG.debug("Asking staking rewards for {} accounts", accountsToProcess.size());
        Options options = Options.builder()
                .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                .option(Offset.of(0))
                .build();

        try {
            // Let's get the staking rewards for the user in the epoch currentEpochNumber - 2
            Result<List<AccountRewards>> rewardsRes = this.koiosFacade.getKoiosService().getAccountService().getAccountRewards(
                    List.copyOf(accountsToProcess.keySet()), currentEpochNumber - 2, options);
            if (!rewardsRes.isSuccessful()) {
                LOG.warn("Cannot get the rewards for accounts. Response={}", rewardsRes.getResponse());
                return;
            }

            Set<String> allPoolIds = rewardsRes.getValue().stream().flatMap(ar -> ar.getRewards().stream()).map(r -> r.getPoolId()).collect(Collectors.toSet());
            allPoolIds.remove(null); // The rest API can return nulls on pool IDs
            List<PoolInfo> poolInfoList = null;

            try {
                Result<List<PoolInfo>> poolInfoRes = this.koiosFacade.getKoiosService().getPoolService().getPoolInformation(List.copyOf(allPoolIds), options);
                if (poolInfoRes.isSuccessful())
                    poolInfoList = poolInfoRes.getValue();
                else
                    LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());
            } catch (ApiException e) {
                LOG.warn("Cannot retrieve pool information: {}", e);
            }
            for (AccountRewards accountRewards : rewardsRes.getValue()) {
                if (accountRewards.getRewards().isEmpty()) continue;

                StringBuilder sb = new StringBuilder();
                sb.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                        .append(CARDANO_SCAN_STAKE_KEY)
                        .append(accountRewards.getStakeAddress())
                        .append("\">")
                        .append(shortenStakeAddr(accountRewards.getStakeAddress()))
                        .append("</a>\n")
                        .append((EmojiParser.parseToUnicode(":envelope: ")))
                        .append(accountRewards.getRewards().size())
                        .append(" reward(s)\n\n");
                for (AccountReward reward : accountRewards.getRewards()) {
                    sb.append(EmojiParser.parseToUnicode(":arrow_heading_down: "));
                    String poolName = getPoolName(poolInfoList, reward.getPoolId());
                    if (poolName != null) {
                        sb.append("<a href=\"");
                        sb.append(CARDANO_SCAN_STAKE_POOL).append(reward.getPoolId()).append("\">");
                        sb.append(poolName).append("</a> - ");
                    }
                    sb.append("Epoch ").append(reward.getEarnedEpoch());
                    sb.append("\n").append(translateRewardsType(reward.getType()));
                    sb.append(" ");
                    sb.append(String.format("%,.2f", Long.valueOf(reward.getAmount()) / LOVELACE));
                    sb.append(ADA_SYMBOL).append("\n\n");

                    this.userDao.updateUserEpochNumber(accountsToProcess.get(accountRewards.getStakeAddress()).getId(), currentEpochNumber);
                    this.telegramFacade.sendMessageTo(accountsToProcess.get(accountRewards.getStakeAddress()).getChatId(), sb.toString());
                }
            }
        } catch (ApiException e) {
            LOG.error("Cannot process batch of {} users due to an API exception: {}", e, e);
        } catch (DataAccessException e) {
            LOG.error("Caught SQL exception while processing the staking rewards: {}", e, e);
        }
    }

    private String translateRewardsType(String type) {
        if ("member".equals(type))
            return "Staking Rewards";
        if ("leader".equals(type))
            return "Pool Operator Rewards";
        if ("treasury".equals(type))
            return "Catalyst Voting";
        return type;
    }

    private String getPoolName(List<PoolInfo> poolIds, String poolAddress) {
        if (poolAddress == null) return null;

        if (poolIds != null) {
            Optional<PoolInfo> poolInfo = poolIds.stream().filter(pi -> pi.getPoolIdBech32().equals(poolAddress)).findFirst();
            if (poolInfo.isPresent() && poolInfo.get().getMetaJson() != null && poolInfo.get().getMetaJson().getTicker() != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                sb.append(poolInfo.get().getMetaJson().getTicker());
                sb.append("]");
                if (poolInfo.get().getMetaJson().getName() != null)
                    sb.append(" ").append(poolInfo.get().getMetaJson().getName());

                return sb.toString();
            }
        }

        return "pool1..." + poolAddress.substring(poolAddress.length() - 8);
    }

    private String shortenStakeAddr(String stakeAddr) {
        return "stake1u..." + stakeAddr.substring(stakeAddr.length() - 8);
    }

    public <T> Stream<List<T>> batches(List<T> source, int length) {
        if (length <= 0)
            throw new IllegalArgumentException("length cannot be negative, length=" + length);
        int size = source.size();
        if (size <= 0)
            return Stream.empty();

        int fullChunks = (size - 1) / length;
        return IntStream.range(0, fullChunks + 1).mapToObj(
                n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length));
    }
}
