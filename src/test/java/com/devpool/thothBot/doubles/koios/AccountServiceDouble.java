package com.devpool.thothBot.doubles.koios;

import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import rest.koios.client.backend.api.account.AccountService;
import rest.koios.client.backend.api.account.model.*;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.UTxO;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Option;
import rest.koios.client.backend.factory.options.OptionType;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.FilterType;

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
    public Result<List<UTxO>> getAccountUTxOs(List<String> addresses, boolean extended, Options options) throws ApiException {
        Integer offset = Integer.parseInt(options.toMap().getOrDefault(OptionType.OFFSET.name().toLowerCase(), "0"));
        if (offset > 0) {
            return Result.<List<UTxO>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
        }

        try {
            List<UTxO> utxos = KoiosDataBuilder.getUTxOsForAccount();
            List<UTxO> filtered = utxos.stream().filter(u -> addresses.contains(u.getStakeAddress())).collect(Collectors.toList());
            if (filtered.isEmpty()) {
                return Result.<List<UTxO>>builder().code(200).response("").successful(true).value(null).build();
            }
            return Result.<List<UTxO>>builder().code(200).response("").successful(true).value(filtered).build();

        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AccountTx>> getAccountTxs(String s, Integer integer, Options options) throws ApiException {
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
    public Result<List<AccountAddress>> getAccountAddresses(List<String> addressList, boolean firstOnly, boolean empty, Options options) throws ApiException {
        if (options != null) {
            // Check the offset
            Optional<Option> optionOffset = options.getOptionList().stream().filter(o -> o.getOptionType().equals(OptionType.OFFSET)).findFirst();
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
    public Result<List<AccountAsset>> getAccountAssets(List<String> addressList, Integer epochNo, Options options) throws ApiException {
        // only single iteration
        Optional<Option> optionOffset = Optional.empty();
        if (options != null) {
            optionOffset = options.getOptionList().stream().filter(o -> o.getOptionType() == OptionType.OFFSET).findAny();
        }

        if (optionOffset.isPresent() && ((Offset) optionOffset.get()).getOffset() > 0) {
            return Result.<List<AccountAsset>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
        }

        try {
            List<AccountAsset> data = KoiosDataBuilder.getAccountAssets();
            // For testing purposes, we make sure the account "stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy" does not have any handle
            data.removeIf(a -> a.getStakeAddress().equals("stake1uxpdrerp9wrxunfh6ukyv5267j70fzxgw0fr3z8zeac5vyqhf9jhy") &&
                    a.getPolicyId().equals(AbstractCheckerTask.ADA_HANDLE_POLICY_ID));

            // Thoth NFTs
            if (addressList.contains("stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz")) {
                List<AccountAsset> thothNFTs = KoiosDataBuilder.getThothNfts("stake1u9ttjzthgk2y7x55c9f363a6vpcthv0ukl2d5mhtxvv4kusv5fmtz");
                data.addAll(thothNFTs);
            }

            return Result.<List<AccountAsset>>builder()
                    .code(200)
                    .response("")
                    .successful(true)
                    .value(data.stream().filter(d -> addressList.contains(d.getStakeAddress())).collect(Collectors.toList()))
                    .build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(List<String> addressList, Integer epochNo, Options options) throws ApiException {
        return null;
    }
}
