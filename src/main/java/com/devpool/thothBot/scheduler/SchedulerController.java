package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SchedulerController {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerController.class);
    private ScheduledExecutorService executorService;

    @Autowired
    private TransactionCheckerTaskV2 transactionCheckerTask;

    @Autowired
    private StakingRewardsCheckerTask stakingRewardsCheckerTask;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Value("${thoth.disable-scheduler:false}")
    private Boolean disableScheduler;

    @Value("${thoth.disable-subscription-manager:false}")
    private Boolean disableSubscriptionManager;

    @PostConstruct
    public void post() {
        LOG.info("Creating Scheduling Controller");
        this.executorService = Executors.newScheduledThreadPool(4,
                new CustomizableThreadFactory("WalletCheckerThread"));

        if (Boolean.TRUE.equals(this.disableScheduler)) {
            LOG.warn("Running with TX and Staking scheduler disabled!");
        } else {
            this.executorService.scheduleWithFixedDelay(this.transactionCheckerTask, 10, 60, TimeUnit.SECONDS);
            this.executorService.scheduleWithFixedDelay(this.stakingRewardsCheckerTask, 10, 10 * 60, TimeUnit.SECONDS);
        }

        if (Boolean.TRUE.equals(this.disableSubscriptionManager)) {
            LOG.warn("Running with subscription manager scheduler disabled!");
        } else {
            this.executorService.scheduleWithFixedDelay(this.subscriptionManager, 20, 60 * 60 * 6, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down scheduler");
        this.executorService.shutdown();
    }
}
