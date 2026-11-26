package com.devpool.thothBot.scheduler;

import com.devpool.thothBot.dao.PoolBlocksDao;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.TelegramFacade;
import com.vdurmont.emoji.EmojiParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.api.block.model.Block;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.Order;
import rest.koios.client.backend.factory.options.SortType;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Notifies the admin when one of the configured stake pools mints a new block
 */
@Component
public class PoolBlocksCheckerTask extends AbstractCheckerTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PoolBlocksCheckerTask.class);
    private static final String FIELD_POOL = "pool";
    private static final String FIELD_EPOCH_NO = "epoch_no";
    private static final String FIELD_BLOCK_HEIGHT = "block_height";

    private final TelegramFacade telegramFacade;
    private final MetricsHelper metricsHelper;
    private final PoolBlocksDao poolBlocksDao;

    @Value("${thoth.admin.user-id:0}")
    private long adminUserId;

    @Value("${thoth.pool-blocks.pool-ids:}")
    private List<String> poolIds;

    public PoolBlocksCheckerTask(TelegramFacade telegramFacade, MetricsHelper metricsHelper, PoolBlocksDao poolBlocksDao) {
        this.telegramFacade = telegramFacade;
        this.metricsHelper = metricsHelper;
        this.poolBlocksDao = poolBlocksDao;
    }

    @PostConstruct
    public void post() {
        execTimer = metricsHelper.registerNewTimer(io.micrometer.core.instrument.Timer
                .builder("thoth.scheduler.pool.blocks.time")
                .description("Time spent checking new blocks minted by the configured pools")
                .publishPercentiles(0.9, 0.95, 0.99));
    }

    @Override
    public void run() {
        execTimer.record(() -> {
            if (this.adminUserId == 0) {
                LOG.warn("thoth.admin.user-id is not set: skipping the pool blocks check");
                return;
            }

            var pools = Optional.ofNullable(this.poolIds).orElse(List.of()).stream()
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .distinct()
                    .toList();
            if (pools.isEmpty()) {
                LOG.debug("No pool configured in thoth.pool-blocks.pool-ids: skipping the pool blocks check");
                return;
            }

            try {
                var tip = koiosFacade.getKoiosService().getNetworkService().getChainTip();
                if (!tip.isSuccessful()) {
                    LOG.warn("Cannot retrieve the chain tip due to {} (code {})", tip.getResponse(), tip.getCode());
                    return;
                }
                int currentEpoch = tip.getValue().getEpochNo();

                for (String poolId : pools) {
                    try {
                        checkPool(poolId, currentEpoch);
                    } catch (Exception e) {
                        LOG.warn("Cannot check new blocks for pool {}", poolId, e);
                    }
                }
            } catch (Exception e) {
                LOG.error("Caught throwable while checking pool blocks", e);
            }
        });
    }

    private void checkPool(String poolId, int currentEpoch) throws ApiException {
        var lastBlockHeight = this.poolBlocksDao.getLastBlockHeight(poolId);

        var optionsBuilder = Options.builder()
                .option(Limit.of(DEFAULT_PAGINATION_SIZE))
                .option(Order.by(FIELD_BLOCK_HEIGHT, SortType.ASC))
                .option(Filter.of(FIELD_POOL, FilterType.EQ, poolId));
        if (lastBlockHeight.isPresent()) {
            // Look at the previous epoch too: a block may have been minted right before the epoch rolled over
            optionsBuilder.option(Filter.of(FIELD_EPOCH_NO, FilterType.GTE, String.valueOf(currentEpoch - 1)))
                    .option(Filter.of(FIELD_BLOCK_HEIGHT, FilterType.GT, lastBlockHeight.get().toString()));
        } else {
            // First check for this pool: do not replay the history, only the current epoch
            optionsBuilder.option(Filter.of(FIELD_EPOCH_NO, FilterType.EQ, String.valueOf(currentEpoch)));
        }

        var blocksResp = koiosFacade.getKoiosService().getBlockService().getBlockList(optionsBuilder.build());
        if (!blocksResp.isSuccessful()) {
            LOG.warn("Cannot retrieve the blocks of pool {} due to {} (code {})",
                    poolId, blocksResp.getResponse(), blocksResp.getCode());
            return;
        }

        var newBlocks = Optional.ofNullable(blocksResp.getValue()).orElse(List.of()).stream()
                .filter(b -> b.getBlockHeight() != null)
                .filter(b -> lastBlockHeight.map(h -> b.getBlockHeight() > h).orElse(true))
                .toList();
        if (newBlocks.isEmpty()) {
            LOG.debug("No new blocks minted by pool {}", poolId);
            return;
        }

        LOG.info("Pool {} minted {} new block(s)", poolId, newBlocks.size());
        var poolName = resolvePoolName(poolId);
        for (Block block : newBlocks) {
            this.telegramFacade.sendMessageTo(this.adminUserId, renderBlock(poolId, poolName, block));
            this.poolBlocksDao.upsertLastBlockHeight(poolId, block.getBlockHeight());
        }
    }

    private String resolvePoolName(String poolId) {
        try {
            var poolInfoResp = koiosFacade.getKoiosService().getPoolService().getPoolInformation(List.of(poolId), null);
            if (poolInfoResp.isSuccessful() && poolInfoResp.getValue() != null && !poolInfoResp.getValue().isEmpty()) {
                return getPoolName(poolInfoResp.getValue().get(0));
            }
            LOG.warn("Cannot retrieve the info of pool {} due to {} (code {})",
                    poolId, poolInfoResp.getResponse(), poolInfoResp.getCode());
        } catch (Exception e) {
            LOG.warn("Cannot retrieve the info of pool {}: {}", poolId, e.toString());
        }
        return getPoolName(null, poolId);
    }

    private String renderBlock(String poolId, String poolName, Block block) {
        var sb = new StringBuilder();
        sb.append(EmojiParser.parseToUnicode(":package: "))
                .append("<a href=\"").append(CARDANO_SCAN_STAKE_POOL).append(poolId).append("\">")
                .append(poolName).append("</a> minted a new block!\n")
                .append(EmojiParser.parseToUnicode(":link: "))
                .append("<a href=\"").append(CARDANO_SCAN_BLOCK).append(block.getBlockHeight()).append("\">")
                .append("Block ").append(block.getBlockHeight()).append("</a>\n")
                .append(EmojiParser.parseToUnicode(":hourglass_flowing_sand: Epoch "))
                .append(block.getEpochNo());
        if (block.getEpochSlot() != null)
            sb.append(", epoch slot ").append(block.getEpochSlot());
        sb.append("\n")
                .append(EmojiParser.parseToUnicode(":straight_ruler: Height: ")).append(block.getBlockHeight()).append("\n")
                .append(EmojiParser.parseToUnicode(":round_pushpin: Absolute slot: ")).append(valueOrUnknown(block.getAbsSlot())).append("\n")
                .append(EmojiParser.parseToUnicode(":page_facing_up: Transactions: ")).append(valueOrUnknown(block.getTxCount())).append("\n")
                .append(EmojiParser.parseToUnicode(":key: Op cert counter: ")).append(valueOrUnknown(block.getOpCertCounter())).append("\n")
                .append(EmojiParser.parseToUnicode(":floppy_disk: Size: "));
        if (block.getBlockSize() != null)
            sb.append(String.format(Locale.US, "%.1f KB (%d bytes)", block.getBlockSize() / 1024.0, block.getBlockSize()));
        else
            sb.append("unknown");
        if (block.getBlockTime() != null) {
            sb.append("\n").append(EmojiParser.parseToUnicode(":calendar: "))
                    .append(TX_DATETIME_FORMATTER.format(Instant.ofEpochSecond(block.getBlockTime()).atZone(ZoneId.of("UTC"))))
                    .append(" UTC");
        }
        return sb.toString();
    }

    private static String valueOrUnknown(Object value) {
        return value == null ? "unknown" : value.toString();
    }
}
