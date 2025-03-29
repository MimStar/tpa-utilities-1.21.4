package com.tpautilities;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class PlayerData {
    private String language;

    public PlayerData(){
        language = "en";
    }

    public PlayerData(String language){
        this.language = language;
    }

    public String getLanguage() {
         return language;
    }

    public void setLanguage(String language){
        this.language = language;
    }

    public String toString(){
        return language;
    }

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("language").forGetter(PlayerData::getLanguage)
    ).apply(instance, PlayerData::new));
}
