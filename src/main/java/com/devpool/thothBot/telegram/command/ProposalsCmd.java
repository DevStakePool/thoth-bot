package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.governance.model.Proposal;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class ProposalsCmd extends AbstractCheckerTask implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(ProposalsCmd.class);
    public static final String CMD_PREFIX = "/proposals";

    @Override
    public boolean canTrigger(String username, String message) {
        return message.trim().equals(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp(String username) {
        return true;
    }

    @Override
    public String getDescription() {
        return "Shows the active governance proposals";
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        Long chatId = update.message().chat().id();

        try {
            var tip = koiosFacade.getKoiosService().getNetworkService().getChainTip();
            if (!tip.isSuccessful()) {
                sendBackIssueMessage(bot, chatId, "Cannot get block-chain data right now. Please try later");
                return;
            }

            // Only active proposals
            var options = Options.builder()
                    .option(
                            Filter.of("expiration", FilterType.GTE, tip.getValue().getEpochNo().toString()))
                    .build();

            var proposals = this.koiosFacade.getKoiosService().getGovernanceService().getProposalList(options);
            if (!proposals.isSuccessful()) {
                sendBackIssueMessage(bot, chatId, "Cannot get proposals right now. Please try later");
                return;
            }

            var toRender = renderProposals(proposals.getValue(), tip.getValue().getEpochNo());
            bot.execute(new SendMessage(update.message().chat().id(), toRender)
                    .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true))
                    .parseMode(ParseMode.HTML));

        } catch (Exception e) {
            LOG.warn("Could not get proposals information due to {}", e, e);
            sendBackIssueMessage(bot, chatId,
                    "There was a problem retrieving the proposals. Please try again later");

        }
    }

    private String renderProposals(List<Proposal> proposals, Integer epochNo) throws URISyntaxException {
        StringBuilder sb = new StringBuilder("Found ").append(proposals.size()).append(" active proposal(s)\n\n");

        for (Proposal proposal : proposals) {
            var proposalContent = getProposalContent(proposal.getMetaJson(), proposal.getMetaUrl(), proposal.getProposalId());
            sb.append(EmojiParser.parseToUnicode(":page_with_curl: <a href=\""))
                    .append(String.format(GOV_TOOLS_PROPOSAL, proposal.getProposalId()))
                    .append("\">")
                    .append(proposalContent.title())
                    .append("</a>\n")
                    .append(EmojiParser.parseToUnicode(":label: "))
                    .append(proposal.getProposalType()).append("\n")
                    .append(EmojiParser.parseToUnicode(":hourglass_flowing_sand: Expiring epoch "))
                    .append(proposal.getExpiration() == null ? "unknown" : proposal.getExpiration());

            if (Objects.equals(epochNo, proposal.getExpiration()))
                sb.append(" (current)");

            if (!Optional.ofNullable(proposalContent.authors()).orElse(List.of()).isEmpty()) {
                sb.append(EmojiParser.parseToUnicode("\n:black_nib: Authors "))
                        .append(String.join(",", proposalContent.authors()));
            }
            sb.append("\n\n");
        }

        return sb.toString();
    }

    private void sendBackIssueMessage(TelegramBot bot, long chatId, String message) {
        bot.execute(new SendMessage(chatId,
                EmojiParser.parseToUnicode(":warning: ") + message));
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 30L;
    }
}
