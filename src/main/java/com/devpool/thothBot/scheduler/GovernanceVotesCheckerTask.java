package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.devpool.thothBot.util.CollectionsUtil;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.account.model.AccountInfo;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.governance.model.DRepVote;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GovernanceVotesCheckerTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GovernanceVotesCheckerTask.class);
    private static final String FIELD_BLOCK_TIME = "block_time";
    private final TelegramFacade telegramFacade;

    public GovernanceVotesCheckerTask(TelegramFacade telegramFacade) {
        this.telegramFacade = telegramFacade;
    }

    @Override
    public void run() {
        LOG.info("Checking for new governance votes");
        Map<String, List<DRepVote>> votesCache = new HashMap<>();

        try {

            LOG.info("Checking governance votes for {} wallets", this.userDao.getUsers().size());
            // Filter out non-staking users
            Iterator<List<User>> batchIterator = CollectionsUtil.batchesList(
                    userDao.getUsers().stream().filter(User::isStakeAddress).collect(Collectors.toList()),
                    this.usersBatchSize).iterator();

            while (batchIterator.hasNext()) {
                List<User> usersBatch = batchIterator.next();
                LOG.debug("Processing users batch size {}", usersBatch.size());

                processUserBatch(usersBatch, votesCache);
            }
        } catch (Exception e) {
            LOG.error("Caught throwable while checking governance votes", e);
        } finally {
            LOG.info("Completed checking for new governance votes");
        }
    }

    private void processUserBatch(List<User> usersBatch, Map<String, List<DRepVote>> votesCache) {
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
                    usersBatch.stream().map(User::getAddress).collect(Collectors.toList()), defaultOpts);

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

        var drepNames = super.getDrepNames(batchDreps.values().stream().distinct().collect(Collectors.toList()));

        for (var user : batchDreps.entrySet()) {
            var drepName = drepNames.get(user.getValue());
            LOG.trace("Processing votes for user {} with drep {} (name {})",
                    user.getKey(), user.getValue(), drepName);

            var userEntity = usersBatch.stream()
                    .filter(u -> u.getAddress().equals(user.getKey())).findAny().orElseThrow();
            try {
                List<DRepVote> drepVotes = votesCache.get(user.getValue());
                long currentTs = System.currentTimeMillis() / 1000;

                // Not in cache?
                if (drepVotes == null) {
                    // We get the new votes only
                    var filteredOptions = Options.builder()
                            .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                            .option(Offset.of(0))
                            .option(Filter.of(FIELD_BLOCK_TIME, FilterType.GT, userEntity.getLastGovVotesBlockTime().toString()))
                            .build();
                    var response = koiosFacade.getKoiosService().getGovernanceService().getDRepsVotes(user.getValue(), filteredOptions);
                    if (!response.isSuccessful())
                        throw new ApiException("response was not successful.");

                    drepVotes = response.getValue();
                    votesCache.put(user.getValue(), drepVotes);
                }

                LOG.debug("The user {} follows the drep {} (name {}) and got {} new vote(s)",
                        user.getKey(), user.getValue(), drepName, drepVotes.size());

                userDao.updateUserGovVotesBlockTime(userEntity.getId(), currentTs);
                renderUserNotification(userEntity, user.getValue(), drepName, drepVotes);
            } catch (ApiException e) {
                LOG.warn("Can't check governance votes for user {} and drep {} due to {}",
                        user.getKey(), user.getValue(), e, e);
            }
        }
    }

    private void renderUserNotification(User user, String drepId, String drepName, List<DRepVote> drepVotes) {
        StringBuilder sb = new StringBuilder();
        sb.append(EmojiParser.parseToUnicode(":memo: The DRep <a href=\""))
                .append(GOV_TOOLS_DREP)
                .append(drepId)
                .append("\">")
                .append(drepName)
                .append("</a> has voted:\n");

        for (var vote : drepVotes) {
            sb.append(EmojiParser.parseToUnicode(":small_blue_diamond: "))
                    .append("Gov Action <a hrep=\"")
                    .append(String.format(GOV_TOOLS_PROPOSAL, vote.getProposalTxHash(), vote.getProposalIndex()))
                    .append("\">")
                    .append(vote.getProposalTxHash().substring(vote.getProposalTxHash().length() - 8))
                    .append("#")
                    .append(vote.getProposalIndex())
                    .append("</a>")
                    .append(EmojiParser.parseToUnicode(" :arrow_right: "))
                    .append(vote.getVote())
                    .append("\n");
        }

        this.telegramFacade.sendMessageTo(user.getChatId(), sb.toString());
        if (LOG.isTraceEnabled()) {
            LOG.trace("Sending telegram message for governance votes: {}", sb);
        }
    }
}
