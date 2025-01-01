package com.tpautilities;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TeleportCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TPAUtilities implements ModInitializer {
	private static final String MOD_ID = "tpa-utilities";
	private static final ConcurrentHashMap<UUID, Queue<UUID>> playerTPAMap = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, Queue<UUID>> playerTPAHEREMap = new ConcurrentHashMap<>();
	private static final List<ScheduledExecutorService> schedulers = new ArrayList<>();
	private static final List<UUID> lockedTPAPlayers = new ArrayList<>();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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
					.executes(this::tpacceptExecute));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpadeny")
					.executes(this::tpadenyExecute));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpacancel")
					.executes(this::tpacancelExecute));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("tpalock")
					.executes(this::tpalockExecute));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for(ScheduledExecutorService scheduler : schedulers){
				LOGGER.info("TPA Utilities is shutting down a scheduler, please wait...");
				scheduler.shutdownNow();
			}
		});
		LOGGER.info("TPA Utilities has been loaded successfully!");
	}

	private int tpaExecute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		if (context.getSource().isExecutedByPlayer()) {
			UUID player_uuid = Objects.requireNonNull(context.getSource().getPlayer()).getUuid();
			String player_name = context.getSource().getPlayer().getName().getString();
			UUID target_uuid = EntityArgumentType.getPlayer(context, "player").getUuid();
			if (context.getSource().getPlayer().getUuid() == target_uuid){
				context.getSource().sendFeedback(() -> Text.literal("Error : You can't tpa to yourself.").formatted(Formatting.RED), false);
				return 1;
			}
			if (lockedTPAPlayers.contains(target_uuid)){
				context.getSource().sendFeedback(() -> Text.literal("This player has disabled tpa.").formatted(Formatting.RED), false);
				return 1;
			}
			if (playerTPAMap.containsKey(target_uuid)){
				if (playerTPAMap.get(target_uuid).contains(player_uuid)){
					context.getSource().sendFeedback(() -> Text.literal("Error : You've already asked to tpa to this player.").formatted(Formatting.RED), false);
					return 1;
				}
				playerTPAMap.get(target_uuid).add(player_uuid);
			}
			else{
				Queue<UUID> newStack = new LinkedList<>();
				newStack.add(player_uuid);
				playerTPAMap.put(target_uuid,newStack);
			}
			ServerPlayerEntity player_target = context.getSource().getServer().getPlayerManager().getPlayer(target_uuid);
            assert player_target != null;
            player_target.sendMessage(Text.literal(String.format("%s wants to teleport to you! Accept with /tpaccept or click here!", player_name)).formatted(Formatting.GOLD).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))));
			player_target.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 1.0f, 1.0f);
			context.getSource().sendFeedback(() -> Text.literal("Your tpa request has been sent!").formatted(Formatting.GREEN), false);
			ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			schedulers.add(scheduler);
			scheduler.schedule(() -> {
				if(playerTPAMap.containsKey(target_uuid)){
					if(playerTPAMap.get(target_uuid).contains(player_uuid)){
						playerTPAMap.get(target_uuid).remove(player_uuid);
						if (playerTPAMap.get(target_uuid).isEmpty()){
							playerTPAMap.remove(target_uuid);
						}
						context.getSource().sendFeedback(() -> Text.literal("Your tpa request has expired.").formatted(Formatting.RED), false);
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
			if (context.getSource().getPlayer().getUuid() == target_uuid){
				context.getSource().sendFeedback(() -> Text.literal("Error : You can't tpahere to yourself.").formatted(Formatting.RED), false);
				return 1;
			}
			if (lockedTPAPlayers.contains(target_uuid)){
				context.getSource().sendFeedback(() -> Text.literal("This player has disabled tpahere.").formatted(Formatting.RED), false);
				return 1;
			}
			if (playerTPAHEREMap.containsKey(target_uuid)){
				if (playerTPAHEREMap.get(target_uuid).contains(player_uuid)){
					context.getSource().sendFeedback(() -> Text.literal("Error : You've already asked to tpahere to this player.").formatted(Formatting.RED), false);
					return 1;
				}
				playerTPAHEREMap.get(target_uuid).add(player_uuid);
			}
			else{
				Queue<UUID> newStack = new LinkedList<>();
				newStack.add(player_uuid);
				playerTPAHEREMap.put(target_uuid,newStack);
			}
			ServerPlayerEntity player_target = context.getSource().getServer().getPlayerManager().getPlayer(target_uuid);
			assert player_target != null;
			player_target.sendMessage(Text.literal(String.format("%s wants you to teleport to him! Accept with /tpaccept or click here!", player_name)).formatted(Formatting.GOLD).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"))));
			player_target.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.MASTER, 1.0f, 1.0f);
			context.getSource().sendFeedback(() -> Text.literal("Your tpahere request has been sent!").formatted(Formatting.GREEN), false);
			ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			schedulers.add(scheduler);
			scheduler.schedule(() -> {
				if(playerTPAHEREMap.containsKey(target_uuid)){
					if(playerTPAHEREMap.get(target_uuid).contains(player_uuid)){
						playerTPAHEREMap.get(target_uuid).remove(player_uuid);
						if (playerTPAHEREMap.get(target_uuid).isEmpty()){
							playerTPAHEREMap.remove(target_uuid);
						}
						context.getSource().sendFeedback(() -> Text.literal("Your tpahere request has expired.").formatted(Formatting.RED), false);
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

	private int tpacceptExecute(CommandContext<ServerCommandSource> context){
		if (context.getSource().isExecutedByPlayer()){
			ServerPlayerEntity player = Objects.requireNonNull(context.getSource().getPlayer());
			if (playerTPAMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAMap.get(player.getUuid()).poll();
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				TeleportTarget teleport_target = new TeleportTarget(player.getServerWorld(),player.getPos(),target_player.getVelocity(),target_player.getYaw(),target_player.getPitch(), TeleportTarget.ADD_PORTAL_CHUNK_TICKET);
				target_player.teleportTo(teleport_target);
				if (playerTPAMap.get(player.getUuid()).isEmpty()){
					playerTPAMap.remove(player.getUuid());
				}
				target_player.sendMessage(Text.literal("You have been successfully teleported!").formatted(Formatting.GREEN));
				target_player.playSoundToPlayer(SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else if (playerTPAHEREMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAHEREMap.get(player.getUuid()).poll();
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				TeleportTarget teleport_target = new TeleportTarget(target_player.getServerWorld(), target_player.getPos(),player.getVelocity(),player.getYaw(),player.getPitch(),TeleportTarget.ADD_PORTAL_CHUNK_TICKET);
				player.teleportTo(teleport_target);
				if (playerTPAHEREMap.get(player.getUuid()).isEmpty()){
					playerTPAHEREMap.remove(player.getUuid());
				}
				player.sendMessage(Text.literal("You have been successfully teleported!").formatted(Formatting.GREEN));
				player.playSoundToPlayer(SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else{
				context.getSource().sendFeedback(() -> Text.literal("Error : There is no tpa to accept.").formatted(Formatting.RED), false);
			}
		}
		else{
			context.getSource().sendFeedback(() -> Text.literal("This command can't be called by server.").formatted(Formatting.RED), false);
		}
		return 1;
	}

	private int tpadenyExecute(CommandContext<ServerCommandSource> context) {
		if (context.getSource().isExecutedByPlayer()){
			ServerPlayerEntity player = Objects.requireNonNull(context.getSource().getPlayer());
			if (playerTPAMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAMap.get(player.getUuid()).poll();
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				if (playerTPAMap.get(player.getUuid()).isEmpty()){
					playerTPAMap.remove(player.getUuid());
				}
				target_player.sendMessage(Text.literal("Your tpa request has been refused.").formatted(Formatting.RED));
				target_player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else if (playerTPAHEREMap.containsKey(player.getUuid())){
				UUID target_uuid = playerTPAHEREMap.get(player.getUuid()).poll();
				ServerPlayerEntity target_player = Objects.requireNonNull(context.getSource().getServer().getPlayerManager().getPlayer(target_uuid));
				if (playerTPAHEREMap.get(player.getUuid()).isEmpty()){
					playerTPAHEREMap.remove(player.getUuid());
				}
				target_player.sendMessage(Text.literal("Your tpahere request has been refused.").formatted(Formatting.RED));
				target_player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
			}
			else{
				context.getSource().sendFeedback(() -> Text.literal("Error : There is no tpa to deny.").formatted(Formatting.RED), false);
			}
		}
		else{
			context.getSource().sendFeedback(() -> Text.literal("This command can't be called by server.").formatted(Formatting.RED), false);
		}
		return 1;
	}

	private int tpacancelExecute(CommandContext<ServerCommandSource> context){
		UUID player_uuid = Objects.requireNonNull(context.getSource().getPlayer()).getUuid();
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
		context.getSource().sendFeedback(() -> Text.literal("All of your tpa and tpahere requests have been cancelled!").formatted(Formatting.GREEN), false);
		return 1;
	}

	private int tpalockExecute(CommandContext<ServerCommandSource> context){
		UUID player_uuid = Objects.requireNonNull(context.getSource().getPlayer()).getUuid();
		if (lockedTPAPlayers.contains(player_uuid)){
			lockedTPAPlayers.remove(player_uuid);
			context.getSource().sendFeedback(() -> Text.literal("TPA lock deactivated!").formatted(Formatting.GREEN), false);
		}
		else{
			lockedTPAPlayers.add(player_uuid);
			context.getSource().sendFeedback(() -> Text.literal("TPA lock activated!").formatted(Formatting.GREEN), false);
		}
		return 1;
	}
}