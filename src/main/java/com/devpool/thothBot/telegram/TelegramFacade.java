package com.devpool.thothBot.telegram;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.koios.KoiosFacade;
import com.devpool.thothBot.monitoring.MetricsHelper;
import com.devpool.thothBot.telegram.command.HelpCmd;
import com.devpool.thothBot.telegram.command.IBotCommand;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class TelegramFacade {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramFacade.class);
    private static final int COMMAND_MAX_THREADS = 50;

    @Autowired
    private List<IBotCommand> commands;

    @Autowired
    private MetricsHelper metricsHelper;

    @Autowired
    private UserDao userDao;

    @Autowired
    private KoiosFacade koiosFacade;

    private long totalMessages;
    private long totalCommands;

    private long errorCommands;

    private long timeoutCommands;

    private long totalNotificationsSentSuccessful;
    private long totalNotificationsSentFailed;

    private final Timer performanceSampler = new Timer("Telegram Facade Sampler", true);

    private final ExecutorService commandRunnerExecutor = Executors.newFixedThreadPool(COMMAND_MAX_THREADS,
            new CustomizableThreadFactory("Telegram-Cmd-Thread"));

    private final ScheduledExecutorService commandCompletionExecutor = Executors.newScheduledThreadPool(COMMAND_MAX_THREADS,
            new CustomizableThreadFactory("Telegram-Cmd-Completion-Thread"));
    private TelegramBot bot;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${thoth.admin.username}")
    private String adminUsername;

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

            // Update gauge metric
            this.metricsHelper.hitGauge("telegram_tot_messages", this.totalMessages);
            this.metricsHelper.hitGauge("telegram_tot_commands", this.totalCommands);
            this.metricsHelper.hitGauge("telegram_error_commands", this.errorCommands);
            this.metricsHelper.hitGauge("telegram_timeout_commands", this.timeoutCommands);
            this.metricsHelper.hitGauge("telegram_tot_notifications_success", this.totalNotificationsSentSuccessful);
            this.metricsHelper.hitGauge("telegram_tot_notifications_failed", this.totalNotificationsSentFailed);

            LOG.trace("Calculated new gauge sample for telegram facade {} msgs, {} cmds, {} errors, {} timeout, {} notifications successful, {} notifications failed",
                    this.totalMessages, this.totalCommands, this.errorCommands, this.timeoutCommands,
                    this.totalNotificationsSentSuccessful, this.totalNotificationsSentFailed);
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
            id = update.callbackQuery().maybeInaccessibleMessage().chat().id();
        } else {
            LOG.warn("Update.message and callbackQuery are null");
            return;
        }

        LOG.debug("Received message {} from {} on chat {}",
                payload, from, id);

        // The bot is private: only the admin can use it
        if (!isAdmin(from)) {
            LOG.info("Ignoring a message from the non-admin user {} on chat {}", from, id);
            final Long notAdminChatId = id;
            this.commandRunnerExecutor.submit(() -> bot.execute(new SendMessage(notAdminChatId, INACTIVE_BOT_MESSAGE)));
            return;
        }


        List<IBotCommand> matchingCommands = this.commands.stream().filter(c -> c.canTrigger(from, payload)).collect(Collectors.toList());
        if (matchingCommands.isEmpty()) {
            LOG.debug("Unknown command {}", payload);
            final Long unknownCmdChatId = id;
            this.commandRunnerExecutor.submit(() -> {
                reactivateIfNeeded(unknownCmdChatId);
                bot.execute(new SendMessage(unknownCmdChatId,
                        "Unknown command. Try " + HelpCmd.CMD_PREFIX + " or " + HelpCmd.CMD_PREFIX_ALIAS));
            });
            return;
        }

        if (matchingCommands.size() > 1) {
            LOG.warn("Message {} is matching more than ne command", payload);
        }

        IBotCommand command = matchingCommands.get(0);

        synchronized (this.performanceSampler) {
            this.totalCommands++;
        }

        TelegramMessageCallable commandCallable = new TelegramMessageCallable(id, command, update, this.bot, from, payload);
        final Long chatId = id;
        Future<Boolean> commandFuture = this.commandRunnerExecutor.submit(() -> {
            // A chat that wrote to us is reachable again
            reactivateIfNeeded(chatId);
            return commandCallable.call();
        });

        // check after XYZ secs if the command is completed. If not, we kill it.
        // This way we don't block other commands in the pipeline
        this.commandCompletionExecutor.schedule(() -> {
            try {
                Boolean outcome = commandFuture.get(100, TimeUnit.MILLISECONDS);
                if (Boolean.FALSE.equals(outcome))
                    synchronized (this.performanceSampler) {
                        this.errorCommands++;
                    }
            } catch (TimeoutException e) {
                synchronized (this.performanceSampler) {
                    this.timeoutCommands++;
                }
                commandFuture.cancel(true); // Interrupt the command thread
                LOG.warn("The command execution {}, from {}, timed out", payload, from);
                bot.execute(new SendMessage(id,
                        "Sorry, your command timed out. Please retry later"));
            } catch (ExecutionException | InterruptedException e) {
                LOG.error("Exception while waiting for command {} to complete its execution", payload, e);
                if (Thread.interrupted())
                    Thread.currentThread().interrupt();
            }
        }, command.getCommandExecutionTimeoutSeconds(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("Shutting down...");
        this.bot.shutdown();
        this.performanceSampler.cancel();
        this.commandRunnerExecutor.shutdown();
        this.commandCompletionExecutor.shutdown();
    }

    public void sendMessageTo(Long chatId, String message) {
        SendMessage sm = new SendMessage(chatId, message)
                .parseMode(ParseMode.HTML)
                .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true));
        SendResponse outcome = bot.execute(sm);
        if (outcome.isOk()) {
            LOG.debug("Sent message to {} with result isOk={} errorCode={} description={} ",
                    chatId, outcome.isOk(), outcome.errorCode(), outcome.description());
            synchronized (this.performanceSampler) {
                this.totalNotificationsSentSuccessful++;
            }
        } else {
            if (isChatUnreachable(outcome)) {
                LOG.warn("Chat {} is unreachable (code={} description={}). Marking its subscriptions as inactive",
                        chatId, outcome.errorCode(), outcome.description());
                try {
                    this.userDao.deactivateChat(chatId);
                } catch (Exception e) {
                    LOG.error("Can't deactivate the chat {}: {}", chatId, e.toString());
                }
            } else {
                LOG.error("Can't send message due to code={} description={} message={}", outcome.errorCode(), outcome.description(), message);
            }
            synchronized (this.performanceSampler) {
                this.totalNotificationsSentFailed++;
            }
        }
    }

    /**
     * @param response the Telegram response
     * @return true if the response is a 403 Forbidden, e.g. the user blocked the bot, deleted the account or kicked the bot
     */
    private boolean isAdmin(String username) {
        return username != null && this.adminUsername != null && this.adminUsername.equalsIgnoreCase(username);
    }

    static final String INACTIVE_BOT_MESSAGE = "This bot is no longer active. Please use @AdaWatchBot instead.";

    public static boolean isChatUnreachable(BaseResponse response) {
        return response != null && !response.isOk() && response.errorCode() == 403;
    }

    /**
     * If the chat has inactive subscriptions, reactivates them fast-forwarding their checkpoints to the chain tip.
     * Never throws: failing to reactivate must not prevent the user from using the bot.
     */
    private void reactivateIfNeeded(Long chatId) {
        try {
            if (!this.userDao.hasInactiveSubscriptions(chatId))
                return;

            Integer blockHeight = null;
            Integer epochNumber = null;
            try {
                var tipResult = this.koiosFacade.getKoiosService().getNetworkService().getChainTip();
                if (tipResult.isSuccessful()) {
                    blockHeight = tipResult.getValue().getBlockNo();
                    epochNumber = tipResult.getValue().getEpochNo();
                } else {
                    LOG.warn("Can't get the chain tip to reactivate chat {}: {} {}",
                            chatId, tipResult.getCode(), tipResult.getResponse());
                }
            } catch (Exception e) {
                LOG.warn("Can't get the chain tip to reactivate chat {}: {}", chatId, e.toString());
            }

            this.userDao.reactivateChat(chatId, blockHeight, epochNumber, System.currentTimeMillis() / 1000);
        } catch (Exception e) {
            LOG.error("Can't reactivate the chat {}: {}", chatId, e.toString());
        }
    }

    // Needed for testing only
    public void setCommands(List<IBotCommand> commands) {
        this.commands = commands;
    }

    // Needed for testing only
    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    // Needed for testing only
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    // Needed for testing only
    public void setKoiosFacade(KoiosFacade koiosFacade) {
        this.koiosFacade = koiosFacade;
    }

    // Needed for testing only
    public void setBot(TelegramBot bot) {
        this.bot = bot;
    }
}
