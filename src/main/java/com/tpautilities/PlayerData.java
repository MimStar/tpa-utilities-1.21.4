package com.tpautilities;

public class PlayerData {
    private String language = "en";

    public String getLanguage() {
         return language;
    }

    public void setLanguage(String language){
        this.language = language;
    }

    public String toString(){
        return language;
    }
}
