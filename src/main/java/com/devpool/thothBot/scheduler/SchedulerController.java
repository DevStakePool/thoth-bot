package com.devpool.thothBot.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostConstruct
    public void post() {
        LOG.info("Creating Scheduling Controller");
        this.executorService = Executors.newScheduledThreadPool(4,
                new CustomizableThreadFactory("WalletActivityChecker"));

        this.executorService.scheduleWithFixedDelay(transactionCheckerTask, 30, 60, TimeUnit.SECONDS);

    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down scheduler");
        this.executorService.shutdown();
    }
}
