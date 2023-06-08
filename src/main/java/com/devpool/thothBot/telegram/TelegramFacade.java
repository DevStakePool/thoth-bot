package com.devpool.thothBot.telegram;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.monitoring.MetricsHelper;
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
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

@Component
public class TelegramFacade {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramFacade.class);

    @Autowired
    private UserDao userDao;

    @Autowired
    private List<IBotCommand> commands;

    @Autowired
    private MetricsHelper metricsHelper;

    private long totalMessages;
    private long totalCommands;

    private final Timer performanceSampler = new Timer("Telegram Facade Sampler", true);

    private Instant lastSampleInstant;

    private TelegramBot bot;

    @Value("${telegram.bot.token}")
    private String botToken;

    @PostConstruct
    public void post() {
        // Create your bot passing the token received from @BotFather
        this.bot = new TelegramBot(this.botToken);

        // Create performance samples
        performanceSampler.schedule(new TimerTask() {
            @Override
            public void run() {
                sampleMetrics();
            }
        }, 1000, 5000);

        // Register for updates
        bot.setUpdatesListener(updates -> {
            updates.forEach(u -> processUpdate(u, bot));
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        LOG.info("Telegram Facade initialised");
    }

    private void sampleMetrics() {
        synchronized (this.performanceSampler) {
            Instant now = Instant.now();
            if (this.lastSampleInstant == null) {
                this.lastSampleInstant = now;
                this.totalCommands = 0;
                this.totalMessages = 0;
            } else {
                long totalMessagesCurr = this.totalMessages;
                this.totalMessages = 0;
                long totalCommandsCurr = this.totalCommands;
                this.totalCommands = 0;
                int millis = (int) (now.toEpochMilli() - lastSampleInstant.toEpochMilli());
                lastSampleInstant = now;
                double recvMessagesPerSecond = (totalMessagesCurr / (millis / 1000.0));
                double recvCommandsPerSecond = (totalCommandsCurr / (millis / 1000.0));

                // Update gauge metric
                this.metricsHelper.hitGauge("telegram_messages_per_sec", (long) recvMessagesPerSecond);
                this.metricsHelper.hitGauge("telegram_commands_per_sec", (long) recvCommandsPerSecond);
                LOG.trace("Calculated new gauge sample for telegram facade {} msg/sec, {} cmd/sec",
                        recvMessagesPerSecond, recvCommandsPerSecond);
            }
        }
    }

    public void processUpdate(Update update, TelegramBot bot) {
        if (update == null) {
            LOG.warn("Update is null");
            return;
        }

        if (update.message() == null && update.callbackQuery() == null) {
            return;
        }

        synchronized (this.performanceSampler) {
            this.totalMessages++;
        }

        String payload;
        String from;
        Long id;
        if (update.message() != null && update.message().text() != null) {
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

        synchronized (this.performanceSampler) {
            this.totalCommands++;
        }

        command.execute(update, this.bot);
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down...");
        this.bot.shutdown();
        this.performanceSampler.cancel();
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
