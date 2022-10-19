package com.devpool.thothBot.scheduler;

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
    private TransactionCheckerTask transactionCheckerTask;

    @Autowired
    private StakingRewardsCheckerTask stakingRewardsCheckerTask;

    @Value("${thoth.test-mode:false}")
    private Boolean testMode;

    @PostConstruct
    public void post() {
        LOG.info("Creating Scheduling Controller");
        this.executorService = Executors.newScheduledThreadPool(4,
                new CustomizableThreadFactory("WalletActivityChecker"));

        if (this.testMode) {
            LOG.warn("Scheduling in TEST mode");
            this.executorService.scheduleWithFixedDelay(this.transactionCheckerTask, 10, 20, TimeUnit.SECONDS);
            this.executorService.scheduleWithFixedDelay(this.stakingRewardsCheckerTask, 10, 30, TimeUnit.SECONDS);
        } else {
            this.executorService.scheduleWithFixedDelay(this.transactionCheckerTask, 30, 60, TimeUnit.SECONDS);
            this.executorService.scheduleWithFixedDelay(this.stakingRewardsCheckerTask, 2, 10, TimeUnit.MINUTES);
        }
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down scheduler");
        this.executorService.shutdown();
    }
}
