package com.devpool.thothBot.doubles.koios;

import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import rest.koios.client.backend.api.account.AccountService;
import rest.koios.client.backend.api.account.model.*;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.options.Option;
import rest.koios.client.backend.factory.options.OptionType;
import rest.koios.client.backend.factory.options.Options;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AccountServiceDouble implements AccountService {
    @Override
    public Result<List<StakeAddress>> getAccountList(Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AccountInfo>> getAccountInformation(List<String> addressList, Options options) throws ApiException {
        try {
            List<AccountInfo> data = KoiosDataBuilder.getAccountInformationTestData();
            List<AccountInfo> filteredList = data.stream().filter(r -> addressList.contains(r.getStakeAddress())).collect(Collectors.toList());
            return Result.<List<AccountInfo>>builder().code(200).response("").successful(true).value(filteredList).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AccountInfo>> getCachedAccountInformation(List<String> stakeAddresses, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AccountRewards>> getAccountRewards(List<String> addressList, Integer epochNo, Options options) throws ApiException {
        try {
            List<AccountRewards> data = KoiosDataBuilder.getAccountRewardsTestData(epochNo);
            List<AccountRewards> filteredList = data.stream().filter(r -> addressList.contains(r.getStakeAddress())).collect(Collectors.toList());
            return Result.<List<AccountRewards>>builder().code(200).response("").successful(true).value(filteredList).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AccountUpdates>> getAccountUpdates(List<String> addressList, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(List<String> addressList, Options options) throws ApiException {
        if (options != null) {
            // Check the offset
            Optional<Option> optionOffset = options.getOptions().stream().filter(o -> o.getOptionType().equals(OptionType.OFFSET)).findFirst();
        if (optionOffset.isPresent() && Integer.parseInt(optionOffset.get().getValue()) > 0)
            return Result.<List<AccountAddress>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
        }

        try {
            List<AccountAddress> data = KoiosDataBuilder.getAccountAddressesTestData();
            List<AccountAddress> filteredList = data.stream().filter(a -> addressList.contains(a.getStakeAddress())).collect(Collectors.toList());
            return Result.<List<AccountAddress>>builder().code(200).response("").successful(true).value(filteredList).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AccountAssets>> getAccountAssets(List<String> addressList, Integer epochNo, Options options) throws ApiException {
        try {
            List<AccountAssets> data = KoiosDataBuilder.getAccountAssets();
            // For testing purposes, we make sure the account "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy" does not have any handle
            for (AccountAssets aa : data) {
                if (aa.getStakeAddress().equals("stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy")) {
                    aa.getAssetList().removeIf(a -> a.getPolicyId().equals(AbstractCheckerTask.ADA_HANDLE_POLICY_ID));
                }
            }
            return Result.<List<AccountAssets>>builder().code(200).response("").successful(true).value(data).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(List<String> addressList, Integer epochNo, Options options) throws ApiException {
        return null;
    }
}
