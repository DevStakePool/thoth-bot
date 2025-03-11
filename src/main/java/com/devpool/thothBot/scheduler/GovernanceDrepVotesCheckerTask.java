package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.model.model.proposal.ProposalContent;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.vdurmont.emoji.EmojiParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.governance.model.DRepVote;
import rest.koios.client.backend.api.governance.model.Proposal;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GovernanceDrepVotesCheckerTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GovernanceDrepVotesCheckerTask.class);
    private static final String FIELD_BLOCK_TIME = "block_time";
    private final TelegramFacade telegramFacade;
    private final MetricsHelper metricsHelper;

    @PostConstruct
    public void post() {
        execTimer = metricsHelper.registerNewTimer(io.micrometer.core.instrument.Timer
                .builder("thoth.scheduler.gov.drep.votes.time")
                .description("Time spent getting new DREP votes in governance action")
                .publishPercentiles(0.9, 0.95, 0.99));
    }

    public GovernanceDrepVotesCheckerTask(TelegramFacade telegramFacade, MetricsHelper metricsHelper) {
        this.telegramFacade = telegramFacade;
        this.metricsHelper = metricsHelper;
    }

    @Override
    public void run() {
        execTimer.record(() -> {
            LOG.info("Checking for new DRep governance votes");
            Map<String, ProposalContent> proposalsContent = new HashMap<>();
            Optional<List<Proposal>> allProposals = Optional.empty();
            try {
                var tip = koiosFacade.getKoiosService().getNetworkService().getChainTip();
                if (!tip.isSuccessful()) {
                    LOG.warn("Cannot get TIP: {}, {}", tip.getCode(), tip.getResponse());
                } else {
                    // Only active proposals
                    var options = Options.builder()
                            .option(
                                    Filter.of("expiration", FilterType.GTE, tip.getValue().getEpochNo().toString()))
                            .build();

                    var proposals = this.koiosFacade.getKoiosService().getGovernanceService().getProposalList(options);
                    if (!proposals.isSuccessful()) {
                        LOG.warn("Cannot get proposals: {}, {}", proposals.getCode(), proposals.getResponse());
                    } else {
                        allProposals = Optional.of(proposals.getValue());
                    }
                }

                LOG.info("Checking governance votes for {} wallets", this.userDao.getUsers().size());
                // Filter out non-staking users
                Iterator<List<User>> batchIterator = CollectionsUtil.batchesList(
                        userDao.getUsers().stream().filter(User::isStakeAddress).toList(),
                        this.usersBatchSize).iterator();

                while (batchIterator.hasNext()) {
                    List<User> usersBatch = batchIterator.next();
                    LOG.debug("Processing users batch size {}", usersBatch.size());

                    processUserBatch(usersBatch, proposalsContent, allProposals);
                }
            } catch (Exception e) {
                LOG.error("Caught throwable while checking governance votes", e);
            } finally {
                LOG.info("Completed checking for new governance votes");
            }
        });
    }

    private void processUserBatch(List<User> usersBatch, Map<String, ProposalContent> proposalsContent, Optional<List<Proposal>> proposals) {
        // 1. for the batch, grab the cached info and get the drep they delegate (if any)
        // 2. for each user, check the drep votes (cache it locally in case more users are delegating to the same drep)
        // 2.1 check if there are new votes since last time we checked (last gov votes block time)
        // 2.2 if so, notify the user

        Options defaultOpts = Options.builder()
                .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                .option(Offset.of(0))
                .build();

        // StakeAddr -> DrepAddr
        Map<String, String> batchDreps;
        try {
            var response = koiosFacade.getKoiosService().getAccountService().getCachedAccountInformation(
                    usersBatch.stream().map(User::getAddress).toList(), defaultOpts);

            if (!response.isSuccessful())
                throw new ApiException("Response was not successful.");

            batchDreps = response.getValue().stream()
                    .filter(drep -> drep.getDelegatedDrep() != null)
                    .filter(drep -> drep.getDelegatedDrep().startsWith(DREP_HASH_PREFIX))
                    .collect(Collectors.toMap(AccountInfo::getStakeAddress, AccountInfo::getDelegatedDrep));

            LOG.debug("Found {} dreps for user batch of {}",
                    batchDreps.values().stream().distinct().count(),
                    usersBatch.size());

        } catch (ApiException e) {
            LOG.warn("Cannot retrieve the account information for batch of size {}, due to {}",
                    usersBatch.size(), e, e);
            return; // We'll try again later
        }

        var drepNames = super.getDrepNames(batchDreps.values().stream().distinct().toList());
        var handles = super.getAdaHandleForAccount(batchDreps.keySet().toArray(new String[0]));
        for (var user : batchDreps.entrySet()) {
            var drepName = drepNames.get(user.getValue());
            LOG.trace("Processing votes for user {} with drep {} (name {})",
                    user.getKey(), user.getValue(), drepName);

            var userEntity = usersBatch.stream()
                    .filter(u -> u.getAddress().equals(user.getKey())).findAny().orElseThrow();
            try {
                long currentTs = System.currentTimeMillis() / 1000;

                // We get the new votes only
                var filteredOptions = Options.builder()
                        .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                        .option(Offset.of(0))
                        .option(Filter.of(FIELD_BLOCK_TIME, FilterType.GT, userEntity.getLastGovVotesBlockTime().toString()))
                        .build();
                var response = koiosFacade.getKoiosService().getGovernanceService().getDRepsVotes(user.getValue(), filteredOptions);
                if (!response.isSuccessful())
                    throw new ApiException("response was not successful.");

                var drepVotes = response.getValue();

                if (!drepVotes.isEmpty()) {
                    LOG.debug("The user {} follows the drep {} (name {}) and got {} new vote(s)",
                            user.getKey(), user.getValue(), drepName, drepVotes.size());

                    // Get the proposal content (cached)
                    for (String proposalId : drepVotes.stream().map(DRepVote::getProposalId).toList()) {
                        if (!proposalsContent.containsKey(proposalId)) {
                            var prop = proposals
                                    .orElse(List.of())
                                    .stream()
                                    .filter(p -> p.getProposalId().equals(proposalId)).findAny();

                            if (prop.isEmpty()) continue;

                            try {
                                var content = getProposalContent(prop.get().getMetaJson(), prop.get().getMetaUrl(), proposalId);
                                proposalsContent.put(proposalId, content);
                            } catch (URISyntaxException e) {
                                LOG.warn("URI syntax error for URL {} and proposalID {}: {}",
                                        prop.get().getMetaUrl(), proposalId, e.toString());
                            }
                        }
                    }
                    userDao.updateUserGovVotesBlockTime(userEntity.getId(), currentTs);
                    renderUserNotification(userEntity, user.getValue(), drepName, drepVotes, handles, proposalsContent);
                }
            } catch (ApiException e) {
                LOG.warn("Can't check governance votes for user {} and drep {} due to {}",
                        user.getKey(), user.getValue(), e, e);
            }
        }
    }

    private void renderUserNotification(User user, String drepId, String drepName,
                                        List<DRepVote> drepVotes, Map<String, String> handles,
                                        Map<String, ProposalContent> proposalsContent) {
        StringBuilder sb = new StringBuilder();
        sb.append(EmojiParser.parseToUnicode(":memo: The DRep <a href=\""))
                .append(GOV_TOOLS_DREP)
                .append(drepId)
                .append("\">")
                .append(drepName)
                .append("</a> followed by ")
                .append("<a href=\"")
                .append(CARDANO_SCAN_STAKE_KEY)
                .append(user.getAddress())
                .append("\">")
                .append(handles.get(user.getAddress()))
                .append("</a>, has voted:\n\n");

        for (var vote : drepVotes) {
            var content = Optional.ofNullable(proposalsContent.get(vote.getProposalId()))
                    .orElse(new ProposalContent(vote.getProposalId().substring(vote.getProposalId().length() - 8),
                            null, null));

            sb.append(EmojiParser.parseToUnicode(":page_with_curl: "))
                    .append("Action <a href=\"")
                    .append(String.format(GOV_TOOLS_PROPOSAL, vote.getProposalId()))
                    .append("\">")
                    .append(content.title())
                    .append("</a>\n")
                    .append(EmojiParser.parseToUnicode(" :black_nib: "))
                    .append(vote.getVote())
                    .append(" (<i>")
                    .append(TX_DATETIME_FORMATTER.format(LocalDateTime.ofEpochSecond(vote.getBlockTime(), 0, ZoneOffset.UTC)))
                    .append("</i>)\n\n");
        }

        this.telegramFacade.sendMessageTo(user.getChatId(), sb.toString());
        if (LOG.isTraceEnabled()) {
            LOG.trace("Sending telegram message for DREP governance votes: {}", sb);
        }
    }
}
