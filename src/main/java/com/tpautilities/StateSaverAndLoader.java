package com.tpautilities;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class StateSaverAndLoader extends PersistentState {

    public HashMap<UUID,PlayerData> players = new HashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup){
        NbtCompound playersNbt = new NbtCompound();
        players.forEach(((uuid, playerData) -> {
            NbtCompound playerNbt = new NbtCompound();
            playerNbt.putString("language",playerData.toString());
            playersNbt.put(uuid.toString(),playerNbt);
        }));
        nbt.put("players", playersNbt);
        return nbt;
    }

    public static StateSaverAndLoader createFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        StateSaverAndLoader state = new StateSaverAndLoader();

        NbtCompound playersNbt = tag.getCompound("players");
        playersNbt.getKeys().forEach(key -> {
            PlayerData playerData = new PlayerData();
            playerData.setLanguage(playersNbt.getCompound(key).getString("language"));
            UUID uuid = UUID.fromString(key);
            state.players.put(uuid, playerData);
        });
        return state;
    }

    private static final Type<StateSaverAndLoader> type = new Type<>(
            StateSaverAndLoader::new,
            StateSaverAndLoader::createFromNbt,
            null
    );

    public static StateSaverAndLoader getServerState(MinecraftServer server){
        PersistentStateManager persistentStateManager = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager();

        StateSaverAndLoader state = persistentStateManager.getOrCreate(type, TPAUtilities.getMOD_ID());

        state.markDirty();

        return state;
    }

    public static PlayerData getPlayerState(LivingEntity player){
        StateSaverAndLoader serverState = getServerState(Objects.requireNonNull(player.getServer()));
        return serverState.players.computeIfAbsent(player.getUuid(), uuid -> new PlayerData());
    }

    public static void resetPlayerState(MinecraftServer server){
        StateSaverAndLoader serverState = getServerState(server);
        serverState.players.forEach(((uuid, playerData) -> playerData.setLanguage("en")));
        saveState(server);
    }

    public static void saveState(MinecraftServer server) {
        PersistentStateManager persistentStateManager = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager();
        persistentStateManager.save();
    }
}
