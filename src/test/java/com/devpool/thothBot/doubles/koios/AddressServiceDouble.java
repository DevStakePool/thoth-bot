package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.address.AddressService;
import rest.koios.client.backend.api.address.model.AddressAsset;
import rest.koios.client.backend.api.address.model.AddressInfo;
import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.common.TxHash;
import rest.koios.client.backend.api.base.common.UTxO;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.options.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AddressServiceDouble implements AddressService {
    private final BackendServiceDouble.BackendBehavior backendBehavior;

    public AddressServiceDouble(BackendServiceDouble.BackendBehavior backendBehavior) {
        this.backendBehavior = backendBehavior;
    }

    @Override
    public Result<AddressInfo> getAddressInformation(String address) throws ApiException {
        Result<List<AddressInfo>> result = getAddressInformation(List.of(address), SortType.DESC, null);
        if (result.getValue().isEmpty())
            return Result.<AddressInfo>builder().code(200).response("").successful(true).value(null).build();
        else
            return Result.<AddressInfo>builder().code(200).response("").successful(true).value(result.getValue().get(0)).build();

    }

    @Override
    public Result<List<AddressInfo>> getAddressInformation(List<String> addressList, SortType utxoSortType, Options options) throws ApiException {
        try {
            List<AddressInfo> allAddrInfo = KoiosDataBuilder.getAddressInformationTestData();
            List<AddressInfo> filteredList = allAddrInfo.stream().filter(r -> addressList.contains(r.getAddress())).collect(Collectors.toList());
            return Result.<List<AddressInfo>>builder().code(200).response("").successful(true).value(filteredList).build();

        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<UTxO>> getAddressUTxOs(List<String> addresses, boolean extended, Options options) throws ApiException {
        Integer offset = Integer.parseInt(options.toMap().getOrDefault(OptionType.OFFSET.name().toLowerCase(), "0"));
        if (offset > 0) {
            return Result.<List<UTxO>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
        }

        try {
            List<UTxO> utxos = KoiosDataBuilder.getUTxOsForAddress();
            List<UTxO> filtered = utxos.stream().filter(u -> addresses.contains(u.getAddress())).collect(Collectors.toList());
            if (filtered.isEmpty()) {
                return Result.<List<UTxO>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
            }
            return Result.<List<UTxO>>builder().code(200).response("").successful(true).value(filtered).build();

        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
    }

    @Override
    public Result<List<UTxO>> getUTxOsFromPaymentCredentials(List<String> list, boolean b, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getAddressTransactions(List<String> addressList, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxHash>> getAddressTransactions(List<String> addressList, Integer afterBlockHeight, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<AddressAsset>> getAddressAssets(List<String> addressList, Options options) throws ApiException {
        try {
            List<AddressAsset> data = KoiosDataBuilder.getAddressAssets(this.backendBehavior);

            Optional<Option> optionOffset = Optional.empty();
            if (options != null) {
                optionOffset = options.getOptionList().stream().filter(o -> o.getOptionType() == OptionType.OFFSET).findAny();
            }

            if (optionOffset.isPresent() && ((Offset) optionOffset.get()).getOffset() > 0) {
                return Result.<List<AddressAsset>>builder().code(200).response("").successful(true).value(Collections.emptyList()).build();
            }

            if (this.backendBehavior == BackendServiceDouble.BackendBehavior.NOMINAL
                    && addressList.contains("addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m")) {
                List<AddressAsset> thothNFTs = KoiosDataBuilder.getThothNftsForAddresses("addr1wxwrp3hhg8xdddx7ecg6el2s2dj6h2c5g582yg2yxhupyns8feg4m");
                data.addAll(thothNFTs);
            }

            return Result.<List<AddressAsset>>builder().code(200).response("")
                    .successful(true)
                    .value(data.stream().filter(d -> addressList.contains(d.getAddress())).collect(Collectors.toList()))
                    .build();
        } catch (IOException e) {
            throw new ApiException(e.toString(), e);
        }
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
