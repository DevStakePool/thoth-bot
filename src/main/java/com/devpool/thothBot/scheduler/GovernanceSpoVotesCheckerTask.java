package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.governance.model.Proposal;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;

import java.util.Set;

import static rest.koios.client.backend.factory.options.filters.FilterType.*;

@Component
public class GovernanceSpoVotesCheckerTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GovernanceSpoVotesCheckerTask.class);
    private static final String FIELD_EXPIRATION = "expiration";
    private static final String FIELD_PROPOSAL_TYPE = "proposal_type";
    private static final Set<String> SPO_ALLOWED_PROPOSAL_TYPES = Set.of(
            "ParameterChange", "HardForkInitiation", "NoConfidence", "NewCommittee", "InfoAction");
    private static final int POOL_BATCH_SIZE = 5;
    private static final String FIELD_VOTER_ROLE = "voter_role";
    private static final String VALUE_VOTER_ROLE = "SPO";
    private static final String FIELD_VOTER_ID = "voter_id";

    private final TelegramFacade telegramFacade;

    public GovernanceSpoVotesCheckerTask(TelegramFacade telegramFacade) {
        this.telegramFacade = telegramFacade;
    }

    @Override
    public void run() {
        LOG.info("Checking for new SPO governance votes");

        try {
            // First we get the current epoch
            var tip = koiosFacade.getKoiosService().getNetworkService().getChainTip();
            if (!tip.isSuccessful()) {
                LOG.warn("Can't get network tip to check SPO governance votes: (code {}), {}",
                        tip.getCode(), tip.getResponse());
                return;
            }

            // Now, we check for new active proposals
            var proposalOptions = Options.builder()
                    .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                    .option(Offset.of(0))
                    .option(Filter.of(FIELD_EXPIRATION, GTE, tip.getValue().getEpochNo().toString()))
                    .option(Filter.of(FIELD_PROPOSAL_TYPE, IN, constructInFilter(SPO_ALLOWED_PROPOSAL_TYPES)))
                    .build();
            var proposalsResp = koiosFacade.getKoiosService().getGovernanceService()
                    .getProposalList(proposalOptions);

            if (!proposalsResp.isSuccessful()) {
                LOG.warn("Can't get gov proposals in epoch {}, due to ({}) {}",
                        tip.getValue().getEpochNo(),
                        proposalsResp.getCode(), proposalsResp.getResponse());
                return;
            }

            proposalsResp.getValue().forEach(this::processAction);

        } catch (Exception e) {
            LOG.error("Caught throwable while checking governance votes", e);
        } finally {
            LOG.info("Completed checking for new governance votes");
        }
    }

    private void processAction(Proposal proposal) {
        try {
            LOG.debug("Processing proposal {} of type {}. Looking for new SPO votes",
                    proposal.getProposalId(), proposal.getProposalType());

            var proposalId = proposal.getProposalId();

            // get all pool addresses
            var stakingUsers = userDao.getUsers().stream().filter(User::isStakeAddress).toList();
            var allStakingAddresses = stakingUsers.stream().map(User::getAddress).distinct().toList();
            LOG.debug("Checking for retiring/retired pools among {} staking addresses", allStakingAddresses.size());

            // Staking Address -> Pool Address
            var stakingAddrAndPools = collectPoolAddressesAssociatedToStakingAddresses(allStakingAddresses);

            // Collect al the pools and check (batching max 5)
            var iter = CollectionsUtil.batchesList(stakingAddrAndPools.values().stream().distinct().toList(),
                    POOL_BATCH_SIZE).iterator();

            while (iter.hasNext()) {
                var batch = iter.next();
                var proposalVotesOptions = Options.builder()
                        .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                        .option(Offset.of(0))
                        .option(Filter.of(FIELD_VOTER_ROLE, EQ, VALUE_VOTER_ROLE))
                        .option(Filter.of(FIELD_VOTER_ID, IN, constructInFilter(batch)))
                        .build();

                var propVotesRes = koiosFacade.getKoiosService().getGovernanceService().getProposalVotes(proposalId, proposalVotesOptions);
                if (!propVotesRes.isSuccessful()) {
                    LOG.warn("Can't retrieve the proposal votes of {}, due to ({}) {}",
                            proposalId, propVotesRes.getCode(), propVotesRes.getResponse());
                    return;
                }

                // TODO here I got all the votes for the batch of pools.
                //  We need to check the vote block height, check the DB for the specific pool and gov action
                // Finally notify the user
            }
        } catch (ApiException e) {
            LOG.warn("API exception while processing gov action {}", e, e);
        } catch (Exception e) {
            LOG.error("Unknown exception while processing gov action {}", e, e);
        }
    }

}
