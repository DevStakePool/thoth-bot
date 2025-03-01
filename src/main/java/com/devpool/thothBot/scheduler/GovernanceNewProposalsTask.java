package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.telegram.TelegramFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.util.Comparator;
import java.util.stream.Collectors;

@Component
public class GovernanceNewProposalsTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GovernanceNewProposalsTask.class);
    private static final String FIELD_BLOCK_TIME = "block_time";
    private final TelegramFacade telegramFacade;

    public GovernanceNewProposalsTask(TelegramFacade telegramFacade) {
        this.telegramFacade = telegramFacade;
    }

    @Override
    public void run() {
        LOG.info("Checking for new governance actions");

        try {

            LOG.info("Checking governance votes for {} wallets", this.userDao.getUsers().size());
            // Filter out unique users (unique chat-ids)
            var uniqueUsers = userDao.getUsers().stream()
                    .collect(Collectors.toMap(User::getChatId, u -> u, (existing, replacement) -> existing))
                    .values().stream().toList();

            // Grab last actions
            Long maxBlockTimeUsers = uniqueUsers.stream()
                    .map(User::getLastGovActionBlockTime)
                    .max(Comparator.naturalOrder())
                    .orElse(Long.MAX_VALUE);

            var options = Options.builder()
                    .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                    .option(Offset.of(0))
                    .option(Filter.of(FIELD_BLOCK_TIME, FilterType.GT, maxBlockTimeUsers.toString()))
                    .build();
            var proposalsResp = koiosFacade.getKoiosService().getGovernanceService().getProposalList(options);
            //TODO continue here
            //  1. iterate users that have gov action block lower than the proposal
            //  2. notify
            //  3. update user block time

        } catch (Exception e) {
            LOG.error("Caught throwable while checking governance votes", e);
        } finally {
            LOG.info("Completed checking for new governance votes");
        }
    }
}
