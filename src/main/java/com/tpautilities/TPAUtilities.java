package com.tpautilities;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.TeleportTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TPAUtilities implements ModInitializer {
	private static final String MOD_ID = "tpa-utilities";
	private static final ConcurrentHashMap<UUID, List<UUID>> playerTPAMap = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, List<UUID>> playerTPAHEREMap = new ConcurrentHashMap<>();
	private static final List<ScheduledExecutorService> schedulers = new ArrayList<>();
	private static final List<UUID> lockedTPAPlayers = new ArrayList<>();
	private static JsonObject translations = new JsonObject();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static String getMOD_ID(){
		return MOD_ID;
	}

	public static Set<String> listTranslations(){
		return translations.keySet();
	}

	public static List<UUID> listPlayerTPA (UUID player_uuid){
		return playerTPAMap.get(player_uuid);
	}
	public static List<UUID> listPlayerTPAHERE (UUID player_uuid){
		return playerTPAHEREMap.get(player_uuid);
	}

	public static String getTranslation(String language, String sentence){
		if (!translations.has(language)) return "(Error : there is a problem in the tpa_translations.json, please delete the file and restart the server or correct your translation)";
		return translations.get(language).getAsJsonObject().get(sentence).getAsString();
	}

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpa")
					.then(CommandManager.argument("player", EntityArgumentType.player())
					.executes(this::tpaExecute)));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpahere")
					.then(CommandManager.argument("player", EntityArgumentType.player())
							.executes(this::tpahereExecute)));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpaccept")
					.then(CommandManager.argument("player", EntityArgumentType.player())
							.suggests(new PlayerSuggestionProvider())
								.executes(this::tpacceptExecute)));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpadeny")
					.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(this::tpadenyExecute)));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpacancel")
					.executes(this::tpacancelExecute));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpalock")
					.executes(this::tpalockExecute));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpalanguage")
					.then(CommandManager.argument("language", StringArgumentType.string())
							.suggests(new LanguageSuggestionProvider())
								.executes(this::tpalanguageExecute)));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for(ScheduledExecutorService scheduler : schedulers){
				LOGGER.info("TPA Utilities is shutting down a scheduler, please wait...");
				scheduler.shutdownNow();
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			translations = JsonHandler.loadTranslations(server);
		});
		LOGGER.info("TPA Utilities has been loaded successfully!");
	}

	private int tpaExecute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		if (context.getSource().isExecutedByPlayer()) {
			UUID player_uuid = Objects.requireNonNull(context.getSource().getPlayer()).getUuid();
			String player_name = context.getSource().getPlayer().getName().getString();
			UUID target_uuid = EntityArgumentType.getPlayer(context, "player").getUuid();
			String player_language = StateSaverAndLoader.getPlayerState(context.getSource().getPlayer()).getLanguage();
			String target_language = StateSaverAndLoader.getPlayerState(EntityArgumentType.getPlayer(context, "player")).getLanguage();
			if (context.getSource().getPlayer().getUuid() == target_uuid){
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpa_yourself")).formatted(Formatting.RED), false);
				return 1;
			}
			if (lockedTPAPlayers.contains(target_uuid)){
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpa_disabled")).formatted(Formatting.RED), false);
				return 1;
			}
			if (playerTPAHEREMap.containsKey(target_uuid) && playerTPAHEREMap.get(target_uuid).contains(player_uuid)){
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpahere_already_asked")).formatted(Formatting.RED), false);
				return 1;
			}
			if (playerTPAMap.containsKey(target_uuid)){
				if (playerTPAMap.get(target_uuid).contains(player_uuid)){
					context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpa_already_asked")).formatted(Formatting.RED), false);
					return 1;
				}
				playerTPAMap.get(target_uuid).add(player_uuid);
			}
			else{
				List<UUID> newList = new ArrayList<>();
				newList.add(player_uuid);
				playerTPAMap.put(target_uuid,newList);
			}
			ServerPlayerEntity player_target = context.getSource().getServer().getPlayerManager().getPlayer(target_uuid);
            assert player_target != null;
            player_target.sendMessage(Text.literal(String.format(getTranslation(target_language,"wants_tpa_teleport"), player_name)).formatted(Formatting.GOLD).styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/tpaccept"))));
			player_target.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1.0f, 1.0f);
			context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"tpa_sent")).formatted(Formatting.GREEN), false);
			ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			schedulers.add(scheduler);
			scheduler.schedule(() -> {
				if(playerTPAMap.containsKey(target_uuid)){
					if(playerTPAMap.get(target_uuid).contains(player_uuid)){
						playerTPAMap.get(target_uuid).remove(player_uuid);
						if (playerTPAMap.get(target_uuid).isEmpty()){
							playerTPAMap.remove(target_uuid);
						}
						context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"tpa_expired")).formatted(Formatting.RED), false);
					}
				}
				schedulers.remove(scheduler);
				scheduler.shutdown();
			}, 60, TimeUnit.SECONDS);
		}
		else{
			context.getSource().sendFeedback(() -> Text.literal("This command can't be called by server.").formatted(Formatting.RED), false);
		}
		return 1;
	}

	private int tpahereExecute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		if (context.getSource().isExecutedByPlayer()) {
			UUID player_uuid = Objects.requireNonNull(context.getSource().getPlayer()).getUuid();
			String player_name = context.getSource().getPlayer().getName().getString();
			UUID target_uuid = EntityArgumentType.getPlayer(context, "player").getUuid();
			String player_language = StateSaverAndLoader.getPlayerState(context.getSource().getPlayer()).getLanguage();
			String target_language = StateSaverAndLoader.getPlayerState(EntityArgumentType.getPlayer(context, "player")).getLanguage();
			if (context.getSource().getPlayer().getUuid() == target_uuid){
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpahere_yourself")).formatted(Formatting.RED), false);
				return 1;
			}
			if (lockedTPAPlayers.contains(target_uuid)){
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpahere_disabled")).formatted(Formatting.RED), false);
				return 1;
			}
			if (playerTPAMap.containsKey(target_uuid) && playerTPAMap.get(target_uuid).contains(player_uuid)){
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpa_already_asked")).formatted(Formatting.RED), false);
				return 1;
			}
			if (playerTPAHEREMap.containsKey(target_uuid)){
				if (playerTPAHEREMap.get(target_uuid).contains(player_uuid)){
					context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpahere_already_asked")).formatted(Formatting.RED), false);
					return 1;
				}
				playerTPAHEREMap.get(target_uuid).add(player_uuid);
			}
			else{
				List<UUID> newList = new ArrayList<>();
				newList.add(player_uuid);
				playerTPAHEREMap.put(target_uuid,newList);
			}
			ServerPlayerEntity player_target = context.getSource().getServer().getPlayerManager().getPlayer(target_uuid);
			assert player_target != null;
			player_target.sendMessage(Text.literal(String.format(getTranslation(target_language,"wants_tpahere_teleport"), player_name)).formatted(Formatting.GOLD).styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/tpaccept"))));
			player_target.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.MASTER, 1.0f, 1.0f);
			context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"tpa_here_sent")).formatted(Formatting.GREEN), false);
			ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			schedulers.add(scheduler);
			scheduler.schedule(() -> {
				if(playerTPAHEREMap.containsKey(target_uuid)){
					if(playerTPAHEREMap.get(target_uuid).contains(player_uuid)){
						playerTPAHEREMap.get(target_uuid).remove(player_uuid);
						if (playerTPAHEREMap.get(target_uuid).isEmpty()){
							playerTPAHEREMap.remove(target_uuid);
						}
						context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"tpahere_expired")).formatted(Formatting.RED), false);
						context.getSource().getPlayer().playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
					}
				}
				schedulers.remove(scheduler);
				scheduler.shutdown();
			}, 60, TimeUnit.SECONDS);
		}
		else{
			context.getSource().sendFeedback(() -> Text.literal("This command can't be called by server.").formatted(Formatting.RED), false);
		}
		return 1;
	}

	private int tpacceptExecute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException{
		if (context.getSource().isExecutedByPlayer()){
			ServerPlayerEntity player = Objects.requireNonNull(context.getSource().getPlayer());
			String player_language = StateSaverAndLoader.getPlayerState(player).getLanguage();
			UUID player_uuid = EntityArgumentType.getPlayer(context, "player").getUuid();
			if (playerTPAMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAMap.get(player.getUuid()).remove(playerTPAMap.get(player.getUuid()).indexOf(player_uuid));
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				String target_language = StateSaverAndLoader.getPlayerState(target_player).getLanguage();
				TeleportTarget teleport_target = new TeleportTarget(player.getWorld(),player.getPos(),target_player.getVelocity(),target_player.getYaw(),target_player.getPitch(), TeleportTarget.ADD_PORTAL_CHUNK_TICKET);
				target_player.teleportTo(teleport_target);
				if (playerTPAMap.get(player.getUuid()).isEmpty()){
					playerTPAMap.remove(player.getUuid());
				}
				target_player.sendMessage(Text.literal(getTranslation(target_language,"teleport_success")).formatted(Formatting.GREEN));
				target_player.playSoundToPlayer(SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else if (playerTPAHEREMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAHEREMap.get(player.getUuid()).remove(playerTPAHEREMap.get(player.getUuid()).indexOf(player_uuid));
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				TeleportTarget teleport_target = new TeleportTarget(target_player.getWorld(), target_player.getPos(),player.getVelocity(),player.getYaw(),player.getPitch(),TeleportTarget.ADD_PORTAL_CHUNK_TICKET);
				player.teleportTo(teleport_target);
				if (playerTPAHEREMap.get(player.getUuid()).isEmpty()){
					playerTPAHEREMap.remove(player.getUuid());
				}
				player.sendMessage(Text.literal(getTranslation(player_language,"teleport_success")).formatted(Formatting.GREEN));
				player.playSoundToPlayer(SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else{
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpaccept")).formatted(Formatting.RED), false);
			}
		}
		else{
			context.getSource().sendFeedback(() -> Text.literal("This command can't be called by server.").formatted(Formatting.RED), false);
		}
		return 1;
	}

	private int tpadenyExecute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		if (context.getSource().isExecutedByPlayer()){
			ServerPlayerEntity player = Objects.requireNonNull(context.getSource().getPlayer());
			String player_language = StateSaverAndLoader.getPlayerState(player).getLanguage();
			UUID player_uuid = EntityArgumentType.getPlayer(context, "player").getUuid();
			if (playerTPAMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAMap.get(player.getUuid()).remove(playerTPAMap.get(player.getUuid()).indexOf(player_uuid));
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				String target_language = StateSaverAndLoader.getPlayerState(target_player).getLanguage();
				if (playerTPAMap.get(player.getUuid()).isEmpty()){
					playerTPAMap.remove(player.getUuid());
				}
				target_player.sendMessage(Text.literal(getTranslation(target_language,"tpa_refused")).formatted(Formatting.RED));
				target_player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else if (playerTPAHEREMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAHEREMap.get(player.getUuid()).remove(playerTPAHEREMap.get(player.getUuid()).indexOf(player_uuid));
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				String target_language = StateSaverAndLoader.getPlayerState(target_player).getLanguage();
				if (playerTPAHEREMap.get(player.getUuid()).isEmpty()){
					playerTPAHEREMap.remove(player.getUuid());
				}
				target_player.sendMessage(Text.literal(getTranslation(target_language,"tpahere_refused")).formatted(Formatting.RED));
				target_player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else{
				context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"error_tpadeny")).formatted(Formatting.RED), false);
			}
		}
		else{
			context.getSource().sendFeedback(() -> Text.literal("This command can't be called by server.").formatted(Formatting.RED), false);
		}
		return 1;
	}

	private int tpacancelExecute(CommandContext<ServerCommandSource> context){
		UUID player_uuid = Objects.requireNonNull(context.getSource().getPlayer()).getUuid();
		String player_language = StateSaverAndLoader.getPlayerState(context.getSource().getPlayer()).getLanguage();
		List<UUID> keysTPA = Collections.list(playerTPAMap.keys());
		List<UUID> keysTPAHERE = Collections.list(playerTPAHEREMap.keys());
		for (UUID target_uuid : keysTPA){
			playerTPAMap.get(target_uuid).remove(player_uuid);
			if (playerTPAMap.get(target_uuid).isEmpty()){
				playerTPAMap.remove(target_uuid);
			}
		}
		for (UUID target_uuid : keysTPAHERE){
			playerTPAHEREMap.get(target_uuid).remove(player_uuid);
			if (playerTPAHEREMap.get(target_uuid).isEmpty()){
				playerTPAHEREMap.remove(target_uuid);
			}
		}
		context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"tpacancel_success")).formatted(Formatting.GREEN), false);
		return 1;
	}

	private int tpalockExecute(CommandContext<ServerCommandSource> context){
		UUID player_uuid = Objects.requireNonNull(context.getSource().getPlayer()).getUuid();
		String player_language = StateSaverAndLoader.getPlayerState(context.getSource().getPlayer()).getLanguage();
		if (lockedTPAPlayers.contains(player_uuid)){
			lockedTPAPlayers.remove(player_uuid);
			context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"tpalock_activated")).formatted(Formatting.GREEN), false);
		}
		else{
			lockedTPAPlayers.add(player_uuid);
			context.getSource().sendFeedback(() -> Text.literal(getTranslation(player_language,"tpalock_deactivated")).formatted(Formatting.GREEN), false);
		}
		return 1;
	}

	private int tpalanguageExecute(CommandContext<ServerCommandSource> context){
		PlayerData playerData = StateSaverAndLoader.getPlayerState(Objects.requireNonNull(context.getSource().getPlayer()));
		String new_language = StringArgumentType.getString(context,"language");
		if (translations.has(new_language)){
			playerData.setLanguage(new_language);
			StateSaverAndLoader.saveState(Objects.requireNonNull(context.getSource().getServer()));
			context.getSource().sendFeedback(() -> Text.literal(getTranslation(playerData.getLanguage(),"tpalanguage_success")).formatted(Formatting.GREEN), false);
			context.getSource().getPlayer().playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
		}
		else{
			context.getSource().sendFeedback(() -> Text.literal(getTranslation(playerData.getLanguage(),"tpalanguage_failure")).formatted(Formatting.RED), false);
			context.getSource().getPlayer().playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
		}
		return 1;
	}
}