package com.devpool.thothBot.telegram;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.telegram.command.IBotCommand;
import com.devpool.thothBot.telegram.command.HelpCmd;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TelegramFacade {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramFacade.class);

    @Autowired
    private UserDao userDao;

    @Autowired
    private List<IBotCommand> commands;

    private TelegramBot bot;

    @Value("${telegram.bot.token}")
    private String botToken;

    @PostConstruct
    public void post() {
        // Create your bot passing the token received from @BotFather
        this.bot = new TelegramBot(this.botToken);

        // Register for updates
        bot.setUpdatesListener(updates -> {
            updates.forEach(u -> processUpdate(u, bot));
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        LOG.info("Telegram Facade initialised");
    }

    public void processUpdate(Update update, TelegramBot bot) {
        if (update == null) {
            LOG.warn("Update is null");
            return;
        }

        if (update.message() == null && update.callbackQuery() == null) {
            return;
        }

        String payload;
        String from;
        Long id;
        if (update.message() != null) {
            payload = update.message().text().trim();
            from = update.message().from().username();
            id = update.message().chat().id();
        } else if (update.callbackQuery() != null) {
            payload = update.callbackQuery().data();
            from = update.callbackQuery().from().username();
            id = update.callbackQuery().message().chat().id();
        } else {
            LOG.warn("Update.message and callbackQuery are null");
            return;
        }

        LOG.debug("Received message {} from {} on chat {}",
                payload, from, id);


        List<IBotCommand> matchingCommands = this.commands.stream().filter(c -> c.canTrigger(from, payload)).collect(Collectors.toList());
        if (matchingCommands.isEmpty()) {
            LOG.debug("Unknown command {}", payload);
            bot.execute(new SendMessage(id,
                    "Unknown command. Try " + HelpCmd.CMD_PREFIX + " or " + HelpCmd.CMD_PREFIX_ALIAS));
            return;
        }

        if (matchingCommands.size() > 1) {
            LOG.warn("Message {} is matching more than ne command", payload);
        }

        IBotCommand command = matchingCommands.get(0);

        command.execute(update, this.bot);
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down...");
        this.bot.shutdown();
    }

    public void sendMessageTo(Long chatId, String message) {
        SendMessage sm = new SendMessage(chatId, message)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true);
        SendResponse outcome = bot.execute(sm);
        if (outcome.isOk())
            LOG.debug("Sent message to {} with result isOk={} errorCode={} description={} ",
                    chatId, outcome.isOk(), outcome.errorCode(), outcome.description());
        else
            LOG.error("Can't send message due to code={} description={} message={}", outcome.errorCode(), outcome.description(), message);
    }
}
