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
    private static final String INFO_CMD_JSON = "test-data/json/info-cmd.json";
    private static final String SUBSCRIBE_CMD_JSON = "test-data/json/subscribe-cmd.json";
    private static final String UNSUBSCRIBE_CMD_JSON = "test-data/json/unsubscribe-cmd.json";
    private static final String STAKE_CMD_JSON = "test-data/json/stake-cmd.json";

    private static final Gson GSON = new Gson();

    public static Update buildHelpCommandUpdate(boolean simulateStartCommand) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(HELP_CMD_JSON,
                jsonContent -> (simulateStartCommand ? jsonContent.replace("/help", "/start") : jsonContent));
        return resp.updates().get(0);
    }

    public static Update buildInfoCommandUpdate() throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(INFO_CMD_JSON, null);
        return resp.updates().get(0);
    }

    public static Update buildSubscribeCommandUpdate() throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(SUBSCRIBE_CMD_JSON, null);
        return resp.updates().get(0);
    }

    public static Update buildUnsubscribeCommandUpdate() throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(UNSUBSCRIBE_CMD_JSON, null);
        return resp.updates().get(0);
    }

    public static Update buildStakeCommandUpdate(String stakeAddress, int chatId) throws IOException {
        GetUpdatesResponse resp = buildUpdateResponseFromJsonFile(STAKE_CMD_JSON,
                jsonContent -> jsonContent.replace("$stake_addr", stakeAddress).replace("-1000", Integer.toString(chatId)));
        return resp.updates().get(0);
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

    interface JsonContentManipulator {
        String manipulateJson(String jsonContent);
    }

}
