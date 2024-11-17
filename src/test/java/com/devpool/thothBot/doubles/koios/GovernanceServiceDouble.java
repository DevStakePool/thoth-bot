package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.governance.GovernanceService;
import rest.koios.client.backend.api.governance.model.*;
import rest.koios.client.backend.factory.options.Options;

import java.io.IOException;
import java.util.List;
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
            var filteredDreps =  allDreps.stream().filter(drep -> drepIds.contains(drep.getDrepId())).collect(Collectors.toList());
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
        return null;
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
        return null;
    }

    @Override
    public Result<List<PoolVote>> getPoolVotes(String poolBech32, Options options) throws ApiException {
        return null;
    }
}
