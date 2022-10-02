package com.devpool.thothBot.telegram.command;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
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
public class HelpCmd extends AbstractCommand {

    public static final String CMD_PREFIX = "/help";

    private static final Logger LOG = LoggerFactory.getLogger(HelpCmd.class);

    /**
     * Some global constants
     */
    public static final Map<String, String> CONSTANTS = Map.of(
            "$thoth.version", "1.0.0-beta",
            "$donation.handle", "$thoth-bot",
            "$url", "https://github.com/DevStakePool/thoth-bot");

    @Value("classpath:help.cdm.html")
    private Resource helpTextResource;

    @Autowired
    private List<AbstractCommand> commands;

    @Override
    public boolean canTrigger(String message) {
        return message.equals(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Show this message";
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        bot.execute(new SendMessage(update.message().chat().id(), getHelpText())
                .disableWebPagePreview(true)
                .parseMode(ParseMode.HTML));
    }

    private String getHelpText() {
        StringBuilder sb = new StringBuilder();
        try {
            String helpText = new BufferedReader(new InputStreamReader(
                    this.helpTextResource.getInputStream())).lines().collect(Collectors.joining("\n"));
            for (Map.Entry<String, String> var : CONSTANTS.entrySet()) {
                helpText = helpText.replace(var.getKey(), var.getValue());
            }

            StringBuilder commandsHelp = new StringBuilder();
            // Add commands help
            commandsHelp
                    .append(EmojiParser.parseToUnicode(":white_small_square: "))
                    .append(this.getCommandPrefix())
                    .append(" - ")
                    .append(this.getDescription())
                    .append("\n");
            for (AbstractCommand command : this.commands) {
                if (!command.showHelp()) continue;

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
            helpText = helpText.replace("%information_source", EmojiParser.parseToUnicode(":information_source:"));
            helpText = helpText.replace("%speech_balloon", EmojiParser.parseToUnicode(":speech_balloon:"));
            sb.append(helpText);
        } catch (IOException e) {
            LOG.error("IO Exception while loading the help text {}", e, e);
        }
        return sb.toString();
    }
}
