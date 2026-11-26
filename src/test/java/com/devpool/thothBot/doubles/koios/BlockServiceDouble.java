package com.devpool.thothBot.doubles.koios;

import rest.koios.client.backend.api.base.Result;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.block.BlockService;
import rest.koios.client.backend.api.block.model.Block;
import rest.koios.client.backend.api.block.model.BlockInfo;
import rest.koios.client.backend.api.block.model.BlockTxCbor;
import rest.koios.client.backend.api.block.model.BlockTxHash;
import rest.koios.client.backend.api.transactions.model.TxInfo;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Block service double: serves the blocks in {@link #BLOCKS}, honouring the
 * pool / epoch_no / block_height horizontal filters used by the bot
 */
public class BlockServiceDouble implements BlockService {
    public static final List<Block> BLOCKS = new CopyOnWriteArrayList<>();

    public static Block block(String pool, int epochNo, int blockHeight) {
        var b = new Block();
        b.setPool(pool);
        b.setEpochNo(epochNo);
        b.setEpochSlot(1000 + blockHeight % 1000);
        b.setBlockHeight(blockHeight);
        b.setAbsSlot(170000000 + blockHeight);
        b.setHash("hash" + blockHeight);
        b.setTxCount(12);
        b.setOpCertCounter(7);
        b.setBlockSize(46285);
        b.setBlockTime(1794810642L);
        return b;
    }

    @Override
    public Result<List<Block>> getBlockList(Options options) throws ApiException {
        Predicate<Block> predicate = b -> true;
        if (options != null) {
            for (var option : options.getOptionList()) {
                if (option instanceof Filter f) {
                    predicate = predicate.and(toPredicate(f));
                }
            }
        }
        var value = new ArrayList<>(BLOCKS.stream().filter(predicate)
                .sorted(Comparator.comparing(Block::getBlockHeight)).toList());
        return Result.<List<Block>>builder().successful(true).response("").code(200).value(value).build();
    }

    private static Predicate<Block> toPredicate(Filter f) {
        return switch (f.getField()) {
            case "pool" -> b -> compare(f, b.getPool());
            case "epoch_no" -> b -> compare(f, b.getEpochNo());
            case "block_height" -> b -> compare(f, b.getBlockHeight());
            default -> throw new IllegalArgumentException("Unsupported filter field " + f.getField());
        };
    }

    // Filter.getValue() returns the PostgREST form "<op>.<value>", e.g. "eq.371"
    private static String rawValue(Filter f) {
        return f.getValue().substring(f.getValue().indexOf('.') + 1);
    }

    private static boolean compare(Filter f, String actual) {
        return switch (f.getFilterType()) {
            case EQ -> rawValue(f).equals(actual);
            default -> throw new IllegalArgumentException("Unsupported filter type " + f.getFilterType());
        };
    }

    private static boolean compare(Filter f, Integer actual) {
        long expected = Long.parseLong(rawValue(f));
        return switch (f.getFilterType()) {
            case EQ -> actual == expected;
            case GT -> actual > expected;
            case GTE -> actual >= expected;
            case LT -> actual < expected;
            case LTE -> actual <= expected;
            default -> throw new IllegalArgumentException("Unsupported filter type " + f.getFilterType());
        };
    }

    @Override
    public Result<Block> getLatestBlock() throws ApiException {
        return null;
    }

    @Override
    public Result<BlockInfo> getBlockInformation(String blockHash) throws ApiException {
        return null;
    }

    @Override
    public Result<List<BlockInfo>> getBlocksInformation(List<String> blockHashes, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<BlockTxHash>> getBlockTransactions(List<String> blockHashes, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<BlockTxCbor>> getBlockTransactionsCbor(List<String> blockHashes, Options options) throws ApiException {
        return null;
    }

    @Override
    public Result<List<TxInfo>> getBlockTransactionsInfo(List<String> blockHashes, Boolean inputs, Boolean metadata, Boolean assets,
                                                         Boolean withdrawals, Boolean certs, Boolean scripts, Boolean bytecode,
                                                         Options options) throws ApiException {
        return null;
    }
}
