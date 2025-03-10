package com.devpool.thothBot.util;

import com.devpool.thothBot.doubles.koios.KoiosDataBuilder;
import com.google.gson.Gson;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.response.GetUpdatesResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TelegramUtils {

    private static final String HELP_CMD_JSON = "test-data/json/help-cmd.json";
    private static final String ANY_CMD_JSON = "test-data/json/any-cmd.json";
    private static final String INFO_CMD_JSON = "test-data/json/info-cmd.json";
    private static final String SUBSCRIBE_CMD_JSON = "test-data/json/subscribe-cmd.json";
    private static final String UNSUBSCRIBE_CMD_JSON = "test-data/json/unsubscribe-cmd.json";
    private static final String STAKE_CMD_JSON = "test-data/json/stake-cmd.json";
    private static final String ASSETS_CMD_JSON = "test-data/json/assets-cmd.json";
    private static final String DETAILS_CMD_JSON = "test-data/json/details-cmd.json";
    private static final String NOTIFY_ALL_CMD_JSON = "test-data/json/notifyall-cmd.json";
    private static final String EPOCH_CMD_JSON = "test-data/json/epoch-cmd.json";
    private static final String REWARDS_CMD_JSON = "test-data/json/rewards-cmd.json";
    private static final String PROPOSALS_CMD_JSON = "test-data/json/proposals-cmd.json";

    private static final Gson GSON = new Gson();

    public static Update buildHelpCommandUpdate(boolean simulateStartCommand, String username) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(HELP_CMD_JSON,
                jsonContent -> {
                    jsonContent = jsonContent.replace("$USERNAME", username);
                    if (simulateStartCommand)
                        jsonContent = jsonContent.replace("/help", "/start");

                    return jsonContent;
                });
        return resp.updates().getFirst();
    }

    public static Update buildAnyCommandUpdate(String commandTag, String username) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(ANY_CMD_JSON,
                jsonContent -> {
                    jsonContent = jsonContent.replace("$USERNAME", username);
                    jsonContent = jsonContent.replace("$COMMAND", commandTag);

                    return jsonContent;
                });
        return resp.updates().getFirst();
    }

    public static Update buildNotifyAllCommandUpdate(String username, String msg) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(NOTIFY_ALL_CMD_JSON,
                jsonContent -> {
                    jsonContent = jsonContent.replace("$USERNAME", username);
                    jsonContent = jsonContent.replace("$MESSAGE", msg);
                    return jsonContent;
                });
        return resp.updates().getFirst();
    }

    public static Update buildInfoCommandUpdate(String chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(INFO_CMD_JSON,
                j -> j.replace("$chat_id", chatId));
        return resp.updates().getFirst();
    }

    public static Update buildEpochCommandUpdate(String chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(EPOCH_CMD_JSON,
                j -> j.replace("$chat_id", chatId));
        return resp.updates().getFirst();
    }

    public static Update buildProposalsCommandUpdate(String chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(PROPOSALS_CMD_JSON,
                j -> j.replace("$chat_id", chatId));
        return resp.updates().getFirst();
    }

    public static Update buildAssetsCommandUpdate(String chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(ASSETS_CMD_JSON,
                jc -> jc.replace("$chat_id", chatId));
        return resp.updates().getFirst();
    }

    public static Update buildRewardsCommandUpdate(String chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(REWARDS_CMD_JSON,
                jc -> jc.replace("$chat_id", chatId));
        return resp.updates().getFirst();
    }

    public static Update buildSubscribeCommandUpdate() throws IOException {
        return buildSubscribeCommandUpdate("-1000");
    }

    public static Update buildSubscribeCommandUpdate(String chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(SUBSCRIBE_CMD_JSON,
                jc -> jc.replace("$chat_id", chatId));
        return resp.updates().getFirst();
    }

    public static Update buildUnsubscribeCommandUpdate() throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(UNSUBSCRIBE_CMD_JSON, null);
        return resp.updates().getFirst();
    }

    public static Update buildAddrCommandUpdate(String stakeAddress, int chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(STAKE_CMD_JSON,
                jsonContent -> jsonContent.replace("$stake_addr", stakeAddress).replaceAll("-1000", Integer.toString(chatId)));
        return resp.updates().getFirst();
    }

    private static GetUpdatesResponse buildUpdateResponseFromJsonFile(String jsonFile, JsonContentManipulator jsonContentManipulator) throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(jsonFile).getFile();
        File jFile = new File(f);
        String json = Files.readString(jFile.toPath());
        if (jsonContentManipulator != null) {
            json = jsonContentManipulator.manipulateJson(json);
        }
        return GSON.fromJson(json, GetUpdatesResponse.class);
    }

    public static Update buildCallbackCommandUpdate(String callbackCmd) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(DETAILS_CMD_JSON,
                jsonContent -> jsonContent.replace("$details", callbackCmd));
        return resp.updates().getFirst();
    }

    interface JsonContentManipulator {
        String manipulateJson(String jsonContent);
    }

}
