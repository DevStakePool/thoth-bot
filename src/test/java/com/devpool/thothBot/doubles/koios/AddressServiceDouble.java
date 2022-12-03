package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.account.model.AccountAddress;
import rest.koios.client.backend.api.address.AddressService;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.common.TxHash;
import rest.koios.client.backend.factory.options.Option;
import rest.koios.client.backend.factory.options.OptionType;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.SortType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AddressServiceDouble implements AddressService {
    @Override
    public Result<AddressInfo> getAddressInformation(String address) throws ApiException {
        return null;
    }

    @Override
    public Result<AddressInfo> getAddressInformation(List<String> addressList, SortType utxoSortType, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getAddressTransactions(List<String> addressList, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getAddressTransactions(List<String> addressList, Integer afterBlockHeight, Options options) throws ApiException {
        if (options != null) {
            // Check the offset
            Optional<Option> optionOffset = options.getOptions().stream().filter(o -> o.getOptionType().equals(OptionType.OFFSET)).findFirst();
            if (optionOffset.isPresent() && Integer.parseInt(optionOffset.get().getValue()) > 0)
                return Result.<List<TxHash>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
        }

        try {
            List<AccountAddress> accountAddresses = KoiosDataBuilder.getAccountAddressesTestData();
            Optional<AccountAddress> accountAddressMatchingInput = accountAddresses.stream().filter(aa -> aa.getAddresses().containsAll(addressList)).findFirst();
            if (accountAddressMatchingInput.isEmpty())
                throw new RuntimeException("Cannot find account address for the list of addresses " + addressList);

            List<TxHash> data = KoiosDataBuilder.getAddressTransactionTestData(accountAddressMatchingInput.get().getStakeAddress());
            return Result.<List<TxHash>>builder().code(200).response("").successful(true).value(data).build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<AddressAsset>> getAddressAssets(List<String> addressList, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getTransactionsByPaymentCredentials(List<String> paymentCredentialsList, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getTransactionsByPaymentCredentials(List<String> paymentCredentialsList, Integer afterBlockHeight, Options options) throws ApiException {
        return null;
    }
}
