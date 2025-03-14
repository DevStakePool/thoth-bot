package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.governance.GovernanceService;
import rest.koios.client.backend.api.governance.model.*;
import rest.koios.client.backend.factory.options.Option;
import rest.koios.client.backend.factory.options.OptionType;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GovernanceServiceDouble implements GovernanceService {
    @Override
    public Result<List<DRepEpochSummary>> getDRepsEpochSummary(Integer epochNo, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<DRep>> getDRepsList(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<DRepInfo>> getDRepsInfo(List<String> drepIds, Options options) throws ApiException {
        try {
            List<DRepInfo> allDreps = KoiosDataBuilder.getAllDrepInfo();
            var filteredDreps = allDreps.stream().filter(drep -> drepIds.contains(drep.getDrepId())).collect(Collectors.toList());
            return Result.<List<DRepInfo>>builder().code(200).response("").successful(true).value(filteredDreps).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<DRepMetadata>> getDRepsMetadata(List<String> drepIds, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<DRepUpdate>> getDRepsUpdates(String drepId, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<DRepVote>> getDRepsVotes(String drepId, Options options) throws ApiException {
        try {
            List<DRepVote> allDrepVotes = KoiosDataBuilder.getDrepVotes(drepId);
            return Result.<List<DRepVote>>builder().code(200).response("").successful(true).value(allDrepVotes).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<DRepDelegator>> getDRepsDelegators(String drepId, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<CommitteeInfo>> getCommitteeInformation(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<CommitteeVote>> getCommitteeVotes(String ccHotId, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<Proposal>> getProposalList(Options options) throws ApiException {
        try {
            List<Proposal> proposals = null;
            if (options.getOptionList().stream()
                    .filter(Filter.class::isInstance)
                    .map(Filter.class::cast)
                    .anyMatch(f -> List.of("block_time", "expiration")
                            .contains(f.getField()))) {
                // We'll get all the proposals
                proposals = KoiosDataBuilder.getAllGovernanceActions();

                // Filter out the expired ones
                var expirationFilterValue = options.getOptionList().stream().filter(Filter.class::isInstance)
                        .map(Filter.class::cast).filter(f -> f.getField().equals("expiration"))
                        .map(Filter::getValue).findFirst();
                if (expirationFilterValue.isPresent()) {
                    var epochNo = Integer.parseInt(expirationFilterValue.get().replace("gte.", ""));
                    proposals = proposals.stream()
                            .filter(p -> p.getExpiration() >= epochNo).toList();
                }
            } else {
                proposals = KoiosDataBuilder.getSpoOnlyGovernanceActions();
            }
            return Result.<List<Proposal>>builder().code(200).response("").successful(true).value(proposals).build();
        } catch (
                IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<Proposal>> getVoterProposals(String voterId, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<ProposalVotingSummary>> getProposalVotingSummary(String proposalId, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<ProposalVote>> getProposalVotes(String proposalId, Options options) throws ApiException {
        if (options != null) {
            // Check the offset
            Optional<Option> optionOffset = options.getOptionList().stream().filter(o -> o.getOptionType().equals(OptionType.OFFSET)).findFirst();
            if (optionOffset.isPresent() && Integer.parseInt(optionOffset.get().getValue()) > 0)
                return Result.<List<ProposalVote>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
        }
        try {
            List<ProposalVote> proposals = KoiosDataBuilder.getSpoOnlyGovernanceActionVotes(proposalId);
            return Result.<List<ProposalVote>>builder().code(200).response("").successful(true).value(proposals).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<PoolVote>> getPoolVotes(String poolBech32, Options options) throws ApiException {
        return null;
    }
}
