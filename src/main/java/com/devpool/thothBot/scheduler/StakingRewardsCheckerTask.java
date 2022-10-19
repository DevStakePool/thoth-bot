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
import org.springframework.beans.factory.annotation.Value;
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
    private static final double LOVELACE = 1000000.0;
    private static final String ADA_SYMBOL = " " + '\u20B3';
    private static final String CARDANO_SCAN_STAKE_KEY = "https://cardanoscan.io/stakekey/";
    private static final String CARDANO_SCAN_STAKE_POOL = "https://cardanoscan.io/pool/";
    private static final int USERS_BATCH_SIZE = 50;

    @Value("${thoth.test-mode:false}")
    private Boolean testMode;

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
        LOG.debug("Starting thread in {} mode", this.testMode ? "TEST" : "PRODUCTION");

        try {
            Integer currentEpochNumber = 346;

            if (!testMode) {
                Result<Tip> chainTipRes = this.koiosFacade.getKoiosService().getNetworkService().getChainTip();
                if (!chainTipRes.isSuccessful()) {
                    LOG.warn("Cannot get the chain tip from main-net: {}", chainTipRes.getResponse());
                    return;
                }

                currentEpochNumber = chainTipRes.getValue().getEpochNo();
            }

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
            if (testMode) u.setLastEpochNumber(currentEpochNumber - 1);

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
                sb.append(EmojiParser.parseToUnicode(":briefcase: <a href=\""))
                        .append(CARDANO_SCAN_STAKE_KEY)
                        .append(accountRewards.getStakeAddress())
                        .append("\">")
                        .append(shortenStakeAddr(accountRewards.getStakeAddress()))
                        .append("</a>\n")
                        .append((EmojiParser.parseToUnicode(":page_facing_up: ")))
                        .append(accountRewards.getRewards().size())
                        .append(" reward(s)\n\n");
                for (AccountReward reward : accountRewards.getRewards()) {
                    sb.append(EmojiParser.parseToUnicode(":arrow_heading_down: <a href=\""));
                    sb.append(CARDANO_SCAN_STAKE_POOL).append(reward.getPoolId()).append("\">");
                    sb.append(getPoolName(poolInfoList, reward.getPoolId())).append("</a>");
                    sb.append("\nEpoch ").append(reward.getEarnedEpoch());
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
        if (poolAddress == null) return "unknown pool ID";

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

    private static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");

        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }

        return output.toString();
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
