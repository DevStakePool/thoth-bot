package com.devpool.thothBot.telegram.command.admin;

import com.devpool.thothBot.dao.UserDao;
import com.devpool.thothBot.dao.data.User;
import com.devpool.thothBot.telegram.command.IBotCommand;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ForceReply;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class AdminNotifyAllCmd implements IBotCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AdminNotifyAllCmd.class);

    public static final String CMD_PREFIX = "/notifyall";

    @Value("${thoth.admin.username}")
    private String adminUsername;

    @Autowired
    private UserDao userDao;

    private ScheduledExecutorService notifyAllExecutor = Executors.newSingleThreadScheduledExecutor(
            new CustomizableThreadFactory("NotifyAllThread"));

    @Override
    public boolean canTrigger(String username, String message) {
        return this.adminUsername.equals(username) && message.startsWith(CMD_PREFIX);
    }

    @Override
    public String getCommandPrefix() {
        return CMD_PREFIX;
    }

    @Override
    public boolean showHelp(String username) {
        return this.adminUsername.equals(username);
    }

    @Override
    public String getDescription() {
        return "[ADMIN] Send a message to all the subscribers";
    }

    @Override
    public void execute(Update update, TelegramBot bot) {
        if (!this.adminUsername.equals(update.message().from().username())) {
            // Not authorized!
            LOG.warn("The user {} is trying to execute the {} command but not authorized",
                    update.message().from().username(), CMD_PREFIX);
            bot.execute(new SendMessage(update.message().chat().id(), "NOT AUTHORIZED"));
            return;
        }

        String msg = update.message().text();
        String args[] = msg.trim().split("\\s");
        List<String> argsAsList = new ArrayList<>(Arrays.asList(args));

        if (argsAsList.size() < 2) {
            bot.execute(new SendMessage(update.message().chat().id(), String.format("Invalid format. Expected %s <MESSAGE>", CMD_PREFIX)));
            return;
        }

        // Remove the command prefix
        String prefixPos0 = argsAsList.get(0);
        String notifyMsg = msg.replaceFirst(prefixPos0, "").trim();

        List<Long> allUsersChatIds = userDao.getUsers().stream().map(User::getChatId).distinct().collect(Collectors.toList());

        bot.execute(new SendMessage(update.message().chat().id(),
                String.format("Ok, notifying all %d user(s) with the following message:%n%s", allUsersChatIds.size(), notifyMsg)));

        int counter = 0;
        for (Long chatId : allUsersChatIds) {
            ScheduledFuture<Boolean> future = this.notifyAllExecutor.schedule(new AsyncMessageSender(notifyMsg, chatId, bot), 100, TimeUnit.MILLISECONDS);

            try {
                if (future.get())
                    counter++;
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Unexpected error while getting the future value for chatId {}: {}", chatId, e.toString());
                Thread.currentThread().interrupt();
            }
        }

        bot.execute(new SendMessage(update.message().chat().id(),
                String.format("All Done! Broadcast message(s) %d/%d", counter, allUsersChatIds.size())));
    }

    public class AsyncMessageSender implements Callable<Boolean> {
        private final Logger LOG = LoggerFactory.getLogger(AsyncMessageSender.class);

        private final TelegramBot bot;
        private String message;
        private long chatId;

        public AsyncMessageSender(String message, long chatId, TelegramBot bot) {
            this.message = message;
            this.chatId = chatId;
            this.bot = bot;
        }


        @Override
        public Boolean call() throws Exception {
            try {
                this.bot.execute(new SendMessage(this.chatId, this.message));
            } catch (Exception e) {
                LOG.error("Unknown error while notifying the user from the " + AdminNotifyAllCmd.CMD_PREFIX + " command: " + e, e);
                return false;
            }
            return true;
        }
    }

}
