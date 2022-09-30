package com.devpool.thothBot.telegram;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.telegram.command.AbstractCommand;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
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
    private List<AbstractCommand> commands;

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

    private void processUpdate(Update update, TelegramBot bot) {
        LOG.debug("Received message {} from {}",
                update.message().text(),
                update.message().from());

        if (update.message().from().isBot()) {
            LOG.debug("It's just a bot");
            return;
        }

        List<AbstractCommand> matchingCommands = this.commands.stream().filter(c -> c.canTrigger(update.message().text())).collect(Collectors.toList());
        if (matchingCommands.isEmpty()) {
            LOG.debug("Unknown command");
            bot.execute(new SendMessage(update.message().chat().id(), "Unknown command"));
            return;
        }

        if (matchingCommands.size() > 1) {
            LOG.warn("Message {} is matching more than ne command", update.message().text());
        }

        AbstractCommand command = matchingCommands.get(0);

        command.execute(update, this.bot);
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down...");
        this.bot.shutdown();
    }

    public void sendMessageTo(Long chatId, String message) {
        SendResponse outcome = bot.execute(new SendMessage(chatId, message));
        LOG.trace("Sent message to {} with result {}", chatId, outcome);
    }

    public void setBot(TelegramBot bot) {
        this.bot = bot;
    }
}
