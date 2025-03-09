package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.vdurmont.emoji.EmojiParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.governance.model.Proposal;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class GovernanceNewProposalsTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GovernanceNewProposalsTask.class);
    private static final String FIELD_BLOCK_TIME = "block_time";
    private final TelegramFacade telegramFacade;
    private final MetricsHelper metricsHelper;

    @PostConstruct
    public void post() {
        execTimer = metricsHelper.registerNewTimer(io.micrometer.core.instrument.Timer
                .builder("thoth.scheduler.gov.proposals.time")
                .description("Time spent getting new governance proposals")
                .publishPercentiles(0.9, 0.95, 0.99));
    }

    public GovernanceNewProposalsTask(TelegramFacade telegramFacade, MetricsHelper metricsHelper) {
        this.telegramFacade = telegramFacade;
        this.metricsHelper = metricsHelper;
    }

    @Override
    public void run() {
        execTimer.record(() -> {
            try {
                LOG.info("Checking governance new proposals for {} wallets", this.userDao.getUsers().size());
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

                if (!proposalsResp.isSuccessful()) {
                    LOG.warn("Cannot retrieve GOV proposals due to {} (code {})",
                            proposalsResp.getResponse(), proposalsResp.getValue());
                    return;
                }

                for (User user : uniqueUsers) {
                    try {
                        notifyUser(user, proposalsResp.getValue());
                    } catch (Exception e) {
                        LOG.warn("Cannot notify the user {} of new proposals", user.getChatId(), e);
                    }
                }
            } catch (Exception e) {
                LOG.error("Caught throwable while checking governance votes", e);
            } finally {
                LOG.info("Completed checking for new governance votes");
            }
        });
    }

    private void notifyUser(User user, List<Proposal> proposals) throws URISyntaxException {
        for (Proposal proposal : proposals) {
            var sb = new StringBuilder();
            if (proposal.getBlockTime() <= user.getLastGovActionBlockTime()) {
                LOG.debug("Skipping proposal {} notification, for user with chat-id {}",
                        proposal.getProposalId(), user.getChatId());
                continue;
            } else {
                LOG.debug("Notifying user {} about new proposal {}", user.getChatId(), proposal.getProposalId());
            }

            // build notification
            var proposalContent = getProposalContent(proposal.getMetaJson(), proposal.getMetaUrl(), proposal.getProposalId());
            sb.append(EmojiParser.parseToUnicode(":page_with_curl: New proposal "))
                    .append("<a href=\"")
                    .append(String.format(GOV_TOOLS_PROPOSAL, proposal.getProposalId()))
                    .append("\">")
                    .append(proposalContent.title())
                    .append("</a>\n")
                    .append(EmojiParser.parseToUnicode(":label: "))
                    .append(proposal.getProposalType()).append("\n")
                    .append(EmojiParser.parseToUnicode(":hourglass_flowing_sand: Expiring epoch "))
                    .append(proposal.getExpiration() == null ? "unknown" : proposal.getExpiration());

            if (!Optional.ofNullable(proposalContent.authors()).orElse(List.of()).isEmpty()) {
                sb.append(EmojiParser.parseToUnicode("\n:black_nib: Authors "))
                        .append(String.join(",", proposalContent.authors()));
            }
            sb.append(EmojiParser.parseToUnicode("\n:memo: <strong>Abstract</strong>\n<i>"))
                    .append(Optional.ofNullable(proposalContent.abstractText()).orElse("Abstract not found")).append("</i>");

            telegramFacade.sendMessageTo(user.getChatId(), sb.toString());
        }

        // Update the latest block time to avoid spamming the user
        var latestBlockTime = proposals.stream().map(Proposal::getBlockTime).max(Comparator.naturalOrder());
        latestBlockTime.ifPresent(bt -> userDao.updateUserGovActionBlockTime(user.getChatId(), bt));
    }
}
