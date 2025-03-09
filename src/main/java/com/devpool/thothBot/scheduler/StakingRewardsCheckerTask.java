package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.vdurmont.emoji.EmojiParser;
import jakarta.annotation.PostConstruct;
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

@Component
public class StakingRewardsCheckerTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(StakingRewardsCheckerTask.class);

    @Autowired
    private TelegramFacade telegramFacade;

    @Autowired
    private MetricsHelper metricsHelper;

    @PostConstruct
    public void post() {
        execTimer = metricsHelper.registerNewTimer(io.micrometer.core.instrument.Timer
                .builder("thoth.scheduler.staking.time")
                .description("Time spent getting new staking rewards")
                .publishPercentiles(0.9, 0.95, 0.99));
    }

    @Override
    public void run() {
        execTimer.record(() -> {
            LOG.debug("Starting thread to check staking rewards");

            try {
                Result<Tip> chainTipRes = this.koiosFacade.getKoiosService().getNetworkService().getChainTip();
                if (!chainTipRes.isSuccessful()) {
                    LOG.warn("Cannot get the chain tip from main-net: {}", chainTipRes.getResponse());
                    return;
                }

                Integer currentEpochNumber = chainTipRes.getValue().getEpochNo();

                LOG.info("Checking staking rewards for {} wallets", this.userDao.getUsers().size());
                // Filter out non-staking users
                Iterator<List<User>> batchIterator = CollectionsUtil.batchesList(
                        userDao.getUsers().stream().filter(User::isStakeAddress).collect(Collectors.toList()),
                        this.usersBatchSize).iterator();

                while (batchIterator.hasNext()) {
                    List<User> usersBatch = batchIterator.next();
                    LOG.debug("Processing users batch size {}", usersBatch.size());

                    processUserBatch(usersBatch, currentEpochNumber);
                }
            } catch (Exception e) {
                LOG.error("Caught throwable while checking wallet staking rewards", e);
            }
        });
    }

    private void processUserBatch(List<User> usersBatch, Integer currentEpochNumber) {
        Map<String, User> accountsToProcess = new HashMap<>();
        for (User u : usersBatch) {
            if (Objects.equals(u.getLastEpochNumber(), currentEpochNumber))
                continue;

            if (u.getLastEpochNumber() > currentEpochNumber) {
                LOG.error("User last epoch number {} greater than the current one from the Tip {}!",
                        u.getLastEpochNumber(), currentEpochNumber);
                continue;
            }
            accountsToProcess.put(u.getAddress(), u);
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
                LOG.warn("Cannot get the rewards for accounts. Code={}, Response={}",
                        rewardsRes.getCode(), rewardsRes.getResponse());
                return;
            }

            // Get ADA Handles
            Map<String, String> handles = getAdaHandleForAccount(accountsToProcess.keySet().toArray(new String[0]));

            Set<String> allPoolIds = rewardsRes.getValue().stream()
                    .flatMap(ar -> ar.getRewards().stream()).map(AccountReward::getPoolId)
                    .collect(Collectors.toSet());
            allPoolIds.remove(null); // The rest API can return nulls on pool IDs
            List<PoolInfo> poolInfoList = Collections.emptyList();

            if (!allPoolIds.isEmpty()) {
                try {
                    Result<List<PoolInfo>> poolInfoRes = this.koiosFacade.getKoiosService().getPoolService().getPoolInformation(List.copyOf(allPoolIds), options);
                    if (poolInfoRes.isSuccessful())
                        poolInfoList = poolInfoRes.getValue();
                    else
                        LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());
                } catch (ApiException e) {
                    LOG.warn("Cannot retrieve pool information: {}", e, e);
                }
            }

            Double latestCardanoPriceUsd = this.oracle.getPriceUsd();

            for (AccountRewards accountRewards : rewardsRes.getValue()) {
                if (accountRewards.getRewards().isEmpty()) continue;

                StringBuilder sb = new StringBuilder();
                sb.append(EmojiParser.parseToUnicode(":key: <a href=\""))
                        .append(CARDANO_SCAN_STAKE_KEY)
                        .append(accountRewards.getStakeAddress())
                        .append("\">")
                        .append(handles.getOrDefault(accountRewards.getStakeAddress(), shortenAddr(accountRewards.getStakeAddress())))
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
                    double adaValue = Long.parseLong(reward.getAmount()) / LOVELACE;
                    sb.append(String.format("%,.2f", adaValue));
                    sb.append(ADA_SYMBOL);
                    if (latestCardanoPriceUsd != null) {
                        sb.append(" (");
                        sb.append(String.format("%,.2f $", adaValue * latestCardanoPriceUsd));
                        sb.append(")");
                    }
                    sb.append("\n\n");

                    this.userDao.updateUserEpochNumber(accountsToProcess.get(accountRewards.getStakeAddress()).getId(), currentEpochNumber);
                    this.telegramFacade.sendMessageTo(accountsToProcess.get(accountRewards.getStakeAddress()).getChatId(), sb.toString());
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Sending telegram message for staking rewards: {}", sb);
                    }
                }
            }
        } catch (ApiException e) {
            LOG.error("Cannot process batch of {} users due to an API exception: {}", usersBatch.size(), e, e);
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
}
