package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    }

    private void notifyUser(User user, List<Proposal> proposals) throws URISyntaxException {
        var sb = new StringBuilder();
        for (Proposal proposal : proposals) {
            if (proposal.getBlockTime() <= user.getLastGovActionBlockTime()) {
                LOG.debug("Skipping proposal {} notification, for user with chat-id {}",
                        proposal.getProposalId(), user.getChatId());
                continue;
            }

            // build notification
            var proposalContent = getProposalContent(proposal.getMetaJson(), proposal.getMetaUrl(), proposal.getProposalId());
            sb.append(EmojiParser.parseToUnicode(":page_with_curl: New proposal "))
                    .append("<a href=\"")
                    .append(String.format(GOV_TOOLS_PROPOSAL, proposal.getProposalId()))
                    .append("\">")
                    .append(proposalContent.title())
                    .append("</a>\n")
                    .append("Type: ")
                    .append(proposal.getProposalType()).append("\n")
                    .append("Valid until epoch: ")
                    .append(proposal.getExpiration() == null ? "unknown" : proposal.getExpiration())
                    .append("\nAuthors: ")
                    .append(String.join(",", proposalContent.authors()))
                    .append("\n<strong>Abstract</strong>\n<i>").append(proposalContent.abstractText()).append("</i>");

            telegramFacade.sendMessageTo(user.getChatId(), sb.toString());
        }

        // Update latest block time to avoid spamming the user
        var latestBlockTime = proposals.stream().map(Proposal::getBlockTime).max(Comparator.naturalOrder());
        latestBlockTime.ifPresent(bt -> userDao.updateUserGovActionBlockTime(user.getId(), bt));
    }
}
