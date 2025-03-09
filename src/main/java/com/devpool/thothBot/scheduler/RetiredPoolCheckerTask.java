package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.subscription.SubscriptionManager;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.vdurmont.emoji.EmojiParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RetiredPoolCheckerTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(RetiredPoolCheckerTask.class);

    @Autowired
    private TelegramFacade telegramFacade;

    @Autowired
    private MetricsHelper metricsHelper;

    @PostConstruct
    public void post() {
        execTimer = metricsHelper.registerNewTimer(io.micrometer.core.instrument.Timer
                .builder("thoth.scheduler.retired.pools.time")
                .description("Time spent getting retired pools")
                .publishPercentiles(0.9, 0.95, 0.99));
    }

    @Override
    public void run() {
        execTimer.record(() -> {
            LOG.debug("Starting thread to check for retired/retiring pools");

            try {
                LOG.info("Checking retired/retiring pools for {} wallets", this.userDao.getUsers().size());

                // Filter out non-staking users
                var stakingUsers = userDao.getUsers().stream().filter(User::isStakeAddress).toList();

                // get all pool addresses
                var allStakingAddresses = stakingUsers.stream().map(User::getAddress).distinct().toList();
                LOG.debug("Checking for retiring/retired pools among {} staking addresses", allStakingAddresses.size());

                var stakingAddrAndPools = collectPoolAddressesAssociatedToStakingAddresses(allStakingAddresses);

                var allRetiringRetiredPools = collectAllRetiringOrRetiredPools(stakingAddrAndPools.values().stream().distinct().collect(Collectors.toList()));

                notifyUsers(stakingAddrAndPools, allRetiringRetiredPools, stakingUsers);
            } catch (Exception e) {
                LOG.error("Caught throwable while checking wallet retired/retiring pools", e);
            }
        });
    }

    // PoolID -> PoolInfo
    private Map<String, PoolInfo> collectAllRetiringOrRetiredPools(List<String> allPools) throws ApiException {
        Map<String, PoolInfo> allRetiredRetiringPools = new HashMap<>();
        // get only the pools that have status retiring or retired
        Options options = Options.builder()
                .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                .option(Filter.of("pool_status", FilterType.NEQ, "registered"))
                .option(Offset.of(0))
                .build();

        var iter = CollectionsUtil.batchesList(allPools, usersBatchSize).iterator();

        while (iter.hasNext()) {
            var batch = iter.next();
            LOG.debug("Grabbing pool info for batch of size {}", batch.size());
            var resp = koiosFacade.getKoiosService().getPoolService().getPoolInformation(batch, options);
            if (!resp.isSuccessful()) {
                throw new ApiException(String.format("Invalid API response while checking for retiring/retired pools: %d - %s",
                        resp.getCode(), resp.getResponse()));
            }
            allRetiredRetiringPools.putAll(
                    resp.getValue().stream().collect(
                            Collectors.toMap(PoolInfo::getPoolIdBech32, Function.identity())));
        }

        return allRetiredRetiringPools;
    }

    private void notifyUsers(Map<String, String> stakingAddrAndPool, Map<String, PoolInfo> allRetiringRetiredPools, List<User> stakingUsers) {

        // Aggregate users
        Map<Long, List<PoolInfo>> usersToNotify = new HashMap<>();

        for (User user : stakingUsers) {
            var userPool = stakingAddrAndPool.get(user.getAddress());
            if (userPool == null) {
                LOG.debug("The user with ID {} and address {} is not staking with any pool", user.getId(), user.getAddress());
                continue;
            }

            if (allRetiringRetiredPools.containsKey(userPool)) {
                var userPools = usersToNotify.getOrDefault(user.getChatId(), new ArrayList<>());
                userPools.add(allRetiringRetiredPools.get(userPool));
                usersToNotify.put(user.getChatId(), userPools);
            }
        }

        for (Map.Entry<Long, List<PoolInfo>> userEntry : usersToNotify.entrySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append(EmojiParser.parseToUnicode(":alarm_clock:"))
                    .append("Your wallet is staking with a retired or retiring pool!\n")
                    .append("You will receive a maximum of ")
                    .append(UserDao.DEFAULT_RETIRING_POOL_NOTIFICATIONS).append(" reminders.\n");
            boolean notificationEmpty = true;
            for (PoolInfo poolInfo : userEntry.getValue()) {
                int remainingNotifications = this.userDao.getRemainingUserNotificationForRetiringPool(userEntry.getKey(), poolInfo.getPoolIdBech32());
                if (remainingNotifications <= 0) {
                    LOG.debug("The user with chat-id {} has a pool {} that is retiring/retired but he has no left reminders",
                            userEntry.getKey(), poolInfo.getPoolIdBech32());
                    continue;
                }
                notificationEmpty = false;

                var poolName = getPoolName(poolInfo);
                sb.append(EmojiParser.parseToUnicode(":skull: "))
                        .append("<a href=\"")
                        .append(CARDANO_SCAN_STAKE_POOL).append(poolInfo.getPoolIdBech32()).append("\">")
                        .append(poolName).append("</a> is ")
                        .append(poolInfo.getPoolStatus());
                var conjunction = poolInfo.getPoolStatus().equals("retired") ? "since" : "in";
                sb.append(" ").append(conjunction)
                        .append(" epoch ")
                        .append(poolInfo.getRetiringEpoch());

                if (remainingNotifications == 1) {
                    sb.append(" This is the last WARNING!");
                    sb.append(EmojiParser.parseToUnicode(":scream:"));
                }
                sb.append("\n");

                this.userDao.setRemainingUserNotificationForRetiringPool(userEntry.getKey(), poolInfo.getPoolIdBech32(), remainingNotifications - 1);
            }

            sb.append("\nPlease consider staking with ")
                    .append("<a href=\"")
                    .append(CARDANO_SCAN_STAKE_POOL).append(SubscriptionManager.DEV_POOL_ID).append("\">")
                    .append("DEV pool!</a>")
                    .append(EmojiParser.parseToUnicode(":pray:"));

            if (notificationEmpty) {
                LOG.debug("The wallet {} has some retiring/retired pools, but already received all the notifications.",
                        userEntry.getKey());
                return;
            }

            this.telegramFacade.sendMessageTo(userEntry.getKey(), sb.toString());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Sending telegram message for staking rewards: {}", sb);
            }
        }
    }
}
