package com.tpautilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class JsonHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("tpa_translations.json");

    public static JsonObject loadTranslations(MinecraftServer server){

        if (!CONFIG_PATH.toFile().exists()){
            createDefaultConfig(CONFIG_PATH.toFile());
            StateSaverAndLoader.resetPlayerState(server);
        }

        try(FileReader reader = new FileReader(CONFIG_PATH.toFile())){
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e){
            e.printStackTrace();
            return new JsonObject();
        }
    }

    private static void createDefaultConfig(File configFile){
        JsonObject defaultConfig = new JsonObject();
        JsonObject en = new JsonObject();

        en.addProperty("error_tpa_yourself","Error : You can't tpa to yourself.");
        en.addProperty("error_tpa_disabled","This player has disabled tpa.");
        en.addProperty("error_tpa_already_asked","Error : You've already asked to tpa to this player.");
        en.addProperty("wants_tpa_teleport","%s wants to teleport to you! Accept with /tpaccept or click here!");
        en.addProperty("tpa_sent","Your tpa request has been sent!");
        en.addProperty("tpa_expired","Your tpa request has expired.");
        en.addProperty("error_tpahere_yourself","Error : You can't tpahere to yourself.");
        en.addProperty("error_tpahere_disabled","This player has disabled tpahere.");
        en.addProperty("error_tpahere_already_asked","Error : You've already asked to tpahere to this player.");
        en.addProperty("wants_tpahere_teleport","%s wants you to teleport to him! Accept with /tpaccept or click here!");
        en.addProperty("tpa_here_sent","Your tpahere request has been sent!");
        en.addProperty("tpahere_expired","Your tpahere request has expired.");
        en.addProperty("teleport_success","You have been successfully teleported!");
        en.addProperty("error_tpaccept","Error : There is no tpa to accept.");
        en.addProperty("tpa_refused","Your tpa request has been refused.");
        en.addProperty("tpahere_refused","Your tpahere request has been refused.");
        en.addProperty("error_tpadeny","Error : There is no tpa to deny.");
        en.addProperty("tpacancel_success","All of your tpa and tpahere requests have been cancelled!");
        en.addProperty("tpalock_activated","TPA lock activated!");
        en.addProperty("tpalock_deactivated","TPA lock deactivated!");
        en.addProperty("tpalanguage_success", "TPA language changed!");
        en.addProperty("tpalanguage_failure","Error : The language provided is invalid.");
        en.addProperty("version", "1.1");

        defaultConfig.add("en",en);

        try (FileWriter writer = new FileWriter(configFile)){
            GSON.toJson(defaultConfig,writer);
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
