package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final TransactionCheckerTaskV2 transactionCheckerTask;
    private final StakingRewardsCheckerTask stakingRewardsCheckerTask;
    private final SubscriptionManager subscriptionManager;
    private final GovernanceDrepVotesCheckerTask governanceDrepVotesCheckerTask;
    private final GovernanceSpoVotesCheckerTask governanceSpoVotesCheckerTask;
    private final GovernanceNewProposalsTask governanceNewProposalsTask;
    private final RetiredPoolCheckerTask retiredPoolCheckerTask;

    @Value("${thoth.disable-scheduler:false}")
    private Boolean disableScheduler;

    @Value("${thoth.disable-subscription-manager:false}")
    private Boolean disableSubscriptionManager;

    @Value("${thoth.scheduler.initial-delay-secs:10}")
    private long scheduledJobsInitialDelaySecs;

    @Value("${thoth.scheduler.gov-spo-votes-enabled:true}")
    private Boolean govSpoVotesEnabled;

    @Value("${thoth.scheduler.gov-new-prop-enabled:true}")
    private Boolean govNewPropEnabled;

    public SchedulerController(TransactionCheckerTaskV2 transactionCheckerTask, StakingRewardsCheckerTask stakingRewardsCheckerTask,
                               SubscriptionManager subscriptionManager, GovernanceDrepVotesCheckerTask governanceDrepVotesCheckerTask,
                               RetiredPoolCheckerTask retiredPoolCheckerTask,
                               GovernanceSpoVotesCheckerTask governanceSpoVotesCheckerTask,
                               GovernanceNewProposalsTask governanceNewProposalsTask) {
        this.transactionCheckerTask = transactionCheckerTask;
        this.stakingRewardsCheckerTask = stakingRewardsCheckerTask;
        this.subscriptionManager = subscriptionManager;
        this.governanceDrepVotesCheckerTask = governanceDrepVotesCheckerTask;
        this.retiredPoolCheckerTask = retiredPoolCheckerTask;
        this.governanceSpoVotesCheckerTask = governanceSpoVotesCheckerTask;
        this.governanceNewProposalsTask = governanceNewProposalsTask;
    }

    @PostConstruct
    public void post() {
        LOG.info("Creating Scheduling Controller");
        this.executorService = Executors.newScheduledThreadPool(4,
                new CustomizableThreadFactory("WalletCheckerThread"));

        if (Boolean.TRUE.equals(this.disableScheduler)) {
            LOG.warn("Running with TX and Staking scheduler disabled!");
        } else {
            this.executorService.scheduleWithFixedDelay(this.transactionCheckerTask, scheduledJobsInitialDelaySecs, 120, TimeUnit.SECONDS);
            this.executorService.scheduleWithFixedDelay(this.stakingRewardsCheckerTask, scheduledJobsInitialDelaySecs, 15 * 60, TimeUnit.SECONDS);
            this.executorService.scheduleWithFixedDelay(this.governanceDrepVotesCheckerTask, scheduledJobsInitialDelaySecs, 60 * 60 * 6, TimeUnit.SECONDS);
            this.executorService.scheduleWithFixedDelay(this.retiredPoolCheckerTask, scheduledJobsInitialDelaySecs, 60 * 60 * 24, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(this.govSpoVotesEnabled)) {
                this.executorService.scheduleWithFixedDelay(this.governanceSpoVotesCheckerTask, scheduledJobsInitialDelaySecs, 60 * 60 * 25, TimeUnit.SECONDS);
            }
            if (Boolean.TRUE.equals(this.govNewPropEnabled)) {
                this.executorService.scheduleWithFixedDelay(this.governanceNewProposalsTask, scheduledJobsInitialDelaySecs, 60 * 60 * 22, TimeUnit.SECONDS);
            }
        }

        if (Boolean.TRUE.equals(this.disableSubscriptionManager)) {
            LOG.warn("Running with subscription manager scheduler disabled!");
        } else {
            this.executorService.scheduleWithFixedDelay(this.subscriptionManager, scheduledJobsInitialDelaySecs * 2, 60 * 60 * 12, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down scheduler");
        this.executorService.shutdown();
    }
}
