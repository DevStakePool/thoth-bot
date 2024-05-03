package com.devpool.thothBot.telegram.command;

import com.devpool.thothBot.subscription.SubscriptionManager;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.vdurmont.emoji.EmojiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HelpCmd implements IBotCommand {

    public static final String CMD_PREFIX = "/help";
    public static final String CMD_PREFIX_ALIAS = "/start";

    private static final Logger LOG = LoggerFactory.getLogger(HelpCmd.class);

    /**
     * Some global constants
     */
    public static final Map<String, String> CONSTANTS = Map.of(
            "$thoth.version", "1.6.0",
            "$donation.handle", "$thoth-bot",
            "$url", "https://github.com/DevStakePool/thoth-bot");

    @Value("classpath:help.cdm.html")
    private Resource helpTextResource;

    @Autowired
    private List<IBotCommand> commands;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Override
    public boolean canTrigger(String username, String message) {
        return message.equals(CMD_PREFIX) || message.equals(CMD_PREFIX_ALIAS);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX + " or " + CMD_PREFIX_ALIAS;
    }

    @Override
    public boolean showHelp(String username) {
        return true;
    }

    @Override
    public String getDescription() {
        return "Shows this message";
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        SendResponse resp = bot.execute(new SendMessage(update.message().chat().id(), getHelpText(update.message().from().username()))
                .disableWebPagePreview(true)
                .parseMode(ParseMode.HTML));
        if (!resp.isOk())
            LOG.error("Error while sending the HELP text {}={}", resp.errorCode(), resp.description());
    }

    @Override
    public long getCommandExecutionTimeoutSeconds() {
        return 3;
    }

    private String getHelpText(String username) {
        StringBuilder sb = new StringBuilder();
        try {
            String helpText = new BufferedReader(new InputStreamReader(
                    this.helpTextResource.getInputStream())).lines().collect(Collectors.joining("\n"));
            for (Map.Entry<String, String> entry : CONSTANTS.entrySet()) {
                helpText = helpText.replace(entry.getKey(), entry.getValue());
            }

            StringBuilder commandsHelp = new StringBuilder();
            // Add commands help
            commandsHelp
                    .append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append(this.getCommandPrefix())
                    .append(" - ")
                    .append(this.getDescription())
                    .append("\n");
            for (IBotCommand command : this.commands) {
                if (!command.showHelp(username)) continue;

                commandsHelp
                        .append(EmojiParser.parseToUnicode(":white_small_square: "))
                        .append(command.getCommandPrefix())
                        .append(" - ")
                        .append(command.getDescription())
                        .append("\n");
            }
            helpText = helpText.replace("$commands", commandsHelp.toString());

            // substitute emojis
            helpText = helpText.replace("%robot", EmojiParser.parseToUnicode(":robot_face:"));
            helpText = helpText.replace("%coffee", EmojiParser.parseToUnicode(":coffee:"));
            helpText = helpText.replace("%information_source", EmojiParser.parseToUnicode(":information_source:"));
            helpText = helpText.replace("%speech_balloon", EmojiParser.parseToUnicode(":speech_balloon:"));
            helpText = helpText.replace("%speaking_head_in_silhouette", EmojiParser.parseToUnicode(":speaking_head_in_silhouette:"));
            helpText = helpText.replace("%art", EmojiParser.parseToUnicode(":art:"));
            sb.append(helpText);

            // Grab the subscription help text
            sb.append(this.subscriptionManager.getHelpText());
        } catch (IOException e) {
            LOG.error("IO Exception while loading the help text {}", e, e);
        }
        return sb.toString();
    }
}
