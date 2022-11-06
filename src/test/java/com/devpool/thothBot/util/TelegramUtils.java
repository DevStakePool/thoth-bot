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
    private static final Gson GSON = new Gson();

    public static Update buildHelpCommandUpdate(boolean simulateStartCommand) throws IOException {
        ClassLoader classLoader = KoiosDataBuilder.class.getClassLoader();
        String f = classLoader.getResource(HELP_CMD_JSON).getFile();
        File jsonFile = new File(f);
        String json = Files.readString(jsonFile.toPath());
        if (simulateStartCommand)
            json = json.replace("/help", "/start");
        GetUpdatesResponse resp = GSON.fromJson(json, GetUpdatesResponse.class);
        return resp.updates().get(0);
    }
}
