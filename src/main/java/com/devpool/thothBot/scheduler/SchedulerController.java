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
    private TransactionCheckerTaskV2 transactionCheckerTask;

    @Autowired
    private StakingRewardsCheckerTask stakingRewardsCheckerTask;


    @Value("${thoth.disable-scheduler:false}")
    private Boolean disableScheduler;

    @PostConstruct
    public void post() {
        if (this.disableScheduler) {
            LOG.warn("Running with scheduler disabled!");
            return;
        }

        LOG.info("Creating Scheduling Controller");
        this.executorService = Executors.newScheduledThreadPool(4,
                new CustomizableThreadFactory("WalletActivityCheckerThread"));

        this.executorService.scheduleWithFixedDelay(this.transactionCheckerTask, 10, 2 * 60, TimeUnit.SECONDS);
        this.executorService.scheduleWithFixedDelay(this.stakingRewardsCheckerTask, 10, 10 * 60, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (!disableScheduler) {
            LOG.info("Shutting down scheduler");
            this.executorService.shutdown();
        }
    }
}
