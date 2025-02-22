package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.PoolVotesDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.governance.model.Proposal;
import rest.koios.client.backend.api.governance.model.ProposalVote;
import rest.koios.client.backend.api.pool.model.PoolInfo;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

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
    private final PoolVotesDao poolVotesDao;

    public GovernanceSpoVotesCheckerTask(TelegramFacade telegramFacade, PoolVotesDao poolVotesDao) {
        this.telegramFacade = telegramFacade;
        this.poolVotesDao = poolVotesDao;
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

            // get all pool addresses
            var stakingUsers = userDao.getUsers().stream().filter(User::isStakeAddress).toList();
            var allStakingAddresses = stakingUsers.stream().map(User::getAddress).distinct().toList();

            LOG.debug("Checking for retiring/retired pools among {} staking addresses", allStakingAddresses.size());

            // Staking Address -> Pool Address
            var stakingAddrAndPools = collectPoolAddressesAssociatedToStakingAddresses(allStakingAddresses);

            // Get ADA Handles
            Map<String, String> handles = getAdaHandleForAccount(allStakingAddresses.toArray(new String[0]));

            Map<String, String> poolNamesCache = new HashMap<>();

            for (Proposal proposal : proposalsResp.getValue()) {
                processAction(proposal, stakingUsers, stakingAddrAndPools, handles, poolNamesCache);
            }

        } catch (Exception e) {
            LOG.error("Caught throwable while checking governance votes", e);
        } finally {
            LOG.info("Completed checking for new governance votes");
        }
    }

    private void processAction(Proposal proposal,
                               List<User> stakingUsers,
                               Map<String, String> stakingAddrAndPools,
                               Map<String, String> handles, Map<String, String> poolNamesCache) {
        try {
            LOG.debug("Processing proposal {} of type {}. Looking for new SPO votes",
                    proposal.getProposalId(), proposal.getProposalType());

            var proposalId = proposal.getProposalId();

            // Collect al the pools and check (batching max 5)
            var iter = CollectionsUtil.batchesList(stakingAddrAndPools.values().stream().distinct().toList(),
                    POOL_BATCH_SIZE).iterator();

            while (iter.hasNext()) {
                var batch = iter.next();
                var inFilterValue = constructInFilter(batch);
                long offset = 0;
                Result<List<ProposalVote>> propVotesRes;
                do {
                    LOG.debug("Getting proposal votes for {}, offset {} with page size {}",
                            proposalId, offset, DEFAULT_PAGINATION_SIZE);
                    var proposalVotesOptions = Options.builder()
                            .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                            .option(Offset.of(offset))
                            .option(Filter.of(FIELD_VOTER_ROLE, EQ, VALUE_VOTER_ROLE))
                            .option(Filter.of(FIELD_VOTER_ID, IN, inFilterValue))
                            .build();

                    propVotesRes = koiosFacade.getKoiosService()
                            .getGovernanceService()
                            .getProposalVotes(proposalId, proposalVotesOptions);
                    offset += DEFAULT_PAGINATION_SIZE;

                    if (!propVotesRes.isSuccessful()) {
                        LOG.warn("Can't retrieve all the proposal votes of {}, due to ({}) {}",
                                proposalId, propVotesRes.getCode(), propVotesRes.getResponse());
                        return;
                    }

                    // Get all the pool names for a given proposal, keeping the cache.
                    getPoolNames(poolNamesCache, propVotesRes.getValue());

                    for (ProposalVote proposalVote : propVotesRes.getValue()) {
                        var blockTime = proposalVote.getBlockTime();
                        var poolId = proposalVote.getVoterId();
                        // if the return is not empty, this means4
                        var poolVotes = poolVotesDao.getVotesForGovAction(proposalId, poolId, blockTime);
                        var lastPoolVoteBlockTime = poolVotes.stream()
                                .max(Comparator.naturalOrder())
                                .orElse(0L);

                        // We got a new vote from the pool?
                        if (blockTime > lastPoolVoteBlockTime) {
                            // The list of stake addresses (from subscribed users) who stake to the pool
                            var userStakeAddresses = stakingAddrAndPools.entrySet().stream()
                                    .filter(e -> poolId.equals(e.getValue()))
                                    .map(Map.Entry::getKey).toList();
                            var usersToNotify = stakingUsers.stream()
                                    .filter(u -> userStakeAddresses.contains(u.getAddress()))
                                    .toList();

                            notifyUsers(proposal, proposalVote, usersToNotify, poolNamesCache, handles);
                            poolVotesDao.addPoolVote(proposalId, poolId, blockTime);
                        }
                    }
                } while (propVotesRes.isSuccessful() && !propVotesRes.getValue().isEmpty());
            }
        } catch (ApiException e) {
            LOG.warn("API exception while processing gov action {}", e, e);
        } catch (Exception e) {
            LOG.error("Unknown exception while processing gov action {}", e, e);
        }
    }

    private void getPoolNames(Map<String, String> poolNamesCache, List<ProposalVote> votes) {
        var uniquePools = votes.stream()
                .map(ProposalVote::getVoterId)
                .filter(poolId -> !poolNamesCache.containsKey(poolId)) // exclude it if we already have it
                .collect(Collectors.toSet());
        if (uniquePools.isEmpty()) return;

        List<PoolInfo> poolInfoList = new ArrayList<>();
        var options = Options.builder()
                .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                .option(Offset.of(0))
                .build();
        try {
            Result<List<PoolInfo>> poolInfoRes = this.koiosFacade.getKoiosService()
                    .getPoolService().getPoolInformation(uniquePools.stream().toList(), options);
            if (poolInfoRes.isSuccessful())
                poolInfoList.addAll(poolInfoRes.getValue());
            else
                LOG.warn("Cannot retrieve pool information due to {}", poolInfoRes.getResponse());
        } catch (ApiException e) {
            LOG.warn("Cannot retrieve pool information: {}", e, e);
        }

        for (PoolInfo poolInfo : poolInfoList) {
            var poolName = getPoolName(poolInfo);
            poolNamesCache.putIfAbsent(poolInfo.getPoolIdBech32(), poolName);
        }
    }

    private void notifyUsers(Proposal proposal, ProposalVote proposalVote, List<User> usersToNotify, Map<String, String> poolNamesCache, Map<String, String> handles) {
        for (User user : usersToNotify) {
            StringBuilder sb = new StringBuilder();
            var poolName = poolNamesCache.get(proposalVote.getVoterId());
            sb.append(EmojiParser.parseToUnicode(":memo: The SPO <a href=\""))
                    .append(CARDANO_SCAN_STAKE_POOL)
                    .append(proposalVote.getVoterId())
                    .append("\">")
                    .append(poolName)
                    .append("</a> followed by ")
                    .append("<a href=\"")
                    .append(CARDANO_SCAN_STAKE_KEY)
                    .append(user.getAddress())
                    .append("\">")
                    .append(handles.get(user.getAddress()))
                    .append("</a>, has voted:\n");

            sb.append(EmojiParser.parseToUnicode(":small_blue_diamond: "))
                    .append("Action <a href=\"")
                    .append(String.format(GOV_TOOLS_PROPOSAL, proposal.getProposalId()))
                    .append("\">")
                    .append(proposal.getProposalId().substring(proposal.getProposalId().length() - 8))
                    .append("</a>")
                    .append(EmojiParser.parseToUnicode(" :arrow_right: "))
                    .append(proposalVote.getVote())
                    .append(" (<i>")
                    .append(TX_DATETIME_FORMATTER.format(LocalDateTime.ofEpochSecond(proposalVote.getBlockTime(), 0, ZoneOffset.UTC)))
                    .append("</i>)\n");

            this.telegramFacade.sendMessageTo(user.getChatId(), sb.toString());
            if (LOG.isTraceEnabled()) {
                LOG.trace("Sending telegram message for SPO governance votes: {}", sb);
            }
        }
    }
}
