package hero.bane.herobot.mod.common;

import hero.bane.herobot.common.ai.AiScriptCodec;
import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.mod.common.ai.AiScriptIO;
import hero.bane.herobot.mod.common.ai.AiScriptRegistry;
import hero.bane.herobot.mod.common.command.*;
import hero.bane.herobot.mod.common.control.RemotePathSettings;
import hero.bane.herobot.mod.common.control.RemotePathState;
import hero.bane.herobot.mod.common.networking.AiDeleteRequestPayload;
import hero.bane.herobot.mod.common.networking.AiDownloadFailedPayload;
import hero.bane.herobot.mod.common.networking.AiDownloadPayload;
import hero.bane.herobot.mod.common.networking.AiDownloadRequestPayload;
import hero.bane.herobot.mod.common.networking.AiListPayload;
import hero.bane.herobot.mod.common.networking.AiListRequestPayload;
import hero.bane.herobot.mod.common.networking.AiUploadPayload;
import hero.bane.herobot.common.networking.ChunkReassembler;
import hero.bane.herobot.mod.common.networking.ControlPlayerPayload;
import hero.bane.herobot.mod.common.networking.HeroBotSyncPayload;
import hero.bane.herobot.mod.common.networking.PathDonePayload;
import hero.bane.herobot.common.networking.ScriptCompression;
import hero.bane.herobot.mod.common.rule.RuleConfigIO;
import hero.bane.herobot.mod.common.util.BlockBreakTasks;
import hero.bane.herobot.mod.common.bot.BotVision;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import hero.bane.herobot.mod.common.bot.BotRegistry;
import hero.bane.herobot.common.bot.PlayerLogouts;
import hero.bane.herobot.mod.common.ping.PingBoosters;
import hero.bane.herobot.common.ping.PingDelays;
import hero.bane.herobot.mod.common.voice.ModVoice;
import hero.bane.herobot.mod.common.voice.VoiceOps;
import hero.bane.herobot.mod.common.util.delayer.DelayedQueue;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import hero.bane.herobot.mod.common.rule.ModRules;

public class HeroBot implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeroBot");
    public static MinecraftServer currentServer = null;

    private static final ChunkReassembler UPLOAD_REASSEMBLER = new ChunkReassembler();
    private static final java.util.Set<String> ANNOUNCED_SAVES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        ModRules.init();
        HeroBotSelectorOptions.register();
        ModVoice.init();

        PayloadTypeRegistry.playS2C().register(HeroBotSyncPayload.TYPE, HeroBotSyncPayload.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(ControlPlayerPayload.TYPE, ControlPlayerPayload.STREAM_CODEC);

        PayloadTypeRegistry.playC2S().register(PathDonePayload.TYPE, PathDonePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PathDonePayload.TYPE, (payload, context) ->
                RemotePathState.finish(context.player(), payload.seq()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer leaving = handler.player;
            PlayerLogouts.record(leaving.getUUID(), leaving.getGameProfile().name(),
                    leaving.level().dimension().identifier().toString(),
                    leaving.getX(), leaving.getY(), leaving.getZ(), leaving.getYRot(), leaving.getXRot());
            if (handler.player instanceof BotPlayer bot) BotRegistry.fireDespawn(bot);
            RemotePathState.clear(handler.player.getUUID());
            RemotePathSettings.clear(handler.player.getUUID());
            BlockBreakTasks.clear(handler.player.getUUID());
            VoiceOps.forget(handler.player.getUUID());
            PingBoosters.forget(handler.player.getUUID());
            PingDelays.forget(handler.player.getUUID());
        });

        PayloadTypeRegistry.playC2S().register(AiUploadPayload.TYPE, AiUploadPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AiDownloadRequestPayload.TYPE, AiDownloadRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AiListRequestPayload.TYPE, AiListRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AiDeleteRequestPayload.TYPE, AiDeleteRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AiDownloadPayload.TYPE, AiDownloadPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AiListPayload.TYPE, AiListPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AiDownloadFailedPayload.TYPE, AiDownloadFailedPayload.STREAM_CODEC);

        registerAiNetworkHandlers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
        {
            PlayerCommand.register(dispatcher, registryAccess);
            PlayerSpawnCommand.register(dispatcher);
            DistanceCommand.register(dispatcher, registryAccess);
            HeroBotCommand.register(dispatcher, registryAccess);
            DelayedCommand.register(dispatcher, registryAccess);
            ChunkResetterCommand.register(dispatcher, registryAccess);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            PingBoosters.shutdown(server);
            AiScriptRegistry.reset();
            ModVoice.shutdown();
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                AiScriptRegistry.stopRunning(newPlayer));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!(handler.player instanceof BotPlayer)) {
                BotRegistry.despawnMatching(server, handler.player.getUUID(),
                        handler.player.getGameProfile().name());
            }
            syncSettingsToPlayer(handler.player);
        });

        RuleConfigIO.onSettingsChanged = HeroBot::syncSettingsToAllPlayers;

        ServerTickEvents.END_SERVER_TICK.register(DelayedQueue::tick);
        ServerTickEvents.END_SERVER_TICK.register(BlockBreakTasks::tick);
        ServerTickEvents.END_SERVER_TICK.register(AiScriptRegistry::tickAll);
        ServerTickEvents.END_SERVER_TICK.register(PingBoosters::tick);
        ServerTickEvents.END_SERVER_TICK.register(BotVision::tick);
        ServerTickEvents.END_SERVER_TICK.register(ModVoice::tick);
    }

    private static boolean validScriptName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }

    private void registerAiNetworkHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(AiUploadPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            server.execute(() -> {
                if (!server.getPlayerList().isOp(player.nameAndId())) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cYou lack permission to upload HeroScript files"));
                    return;
                }
                if (!validScriptName(payload.name())) return;
                String key = player.getUUID() + ":" + payload.name();
                byte[] full = UPLOAD_REASSEMBLER.accept(key, payload.index(), payload.count(), payload.data());
                if (full == null) return;
                try {
                    String json = ScriptCompression.decompress(full);
                    AiScript script = AiScriptCodec.fromJson(json, payload.name());
                    AiScriptIO.saveByName(server, payload.name(), json);
                    AiScriptRegistry.put(payload.name(), script);
                    if (ANNOUNCED_SAVES.add(key)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§aSaved HeroScript '" + payload.name() + "'"));
                    }
                    ServerPlayNetworking.send(player, new AiListPayload(AiScriptRegistry.list(server)));
                } catch (Exception e) {
                    LOGGER.warn("Failed to save uploaded HeroScript '{}'", payload.name(), e);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cFailed to save '" + payload.name() + "': " + e.getMessage()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiDownloadRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            server.execute(() -> {
                if (!validScriptName(payload.name())) return;
                try {
                    AiScript script = AiScriptRegistry.load(server, payload.name());
                    if (script == null) return;
                    String json = AiScriptIO.rawJsonByName(server, payload.name());
                    if (json == null) return;
                    byte[] data = ScriptCompression.compress(json);
                    java.util.List<byte[]> chunks = ScriptCompression.chunk(data);
                    int count = chunks.size();
                    for (int i = 0; i < count; i++) {
                        ServerPlayNetworking.send(player, new AiDownloadPayload(payload.name(), i, count, chunks.get(i)));
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to load HeroScript '{}'", payload.name(), e);
                    ServerPlayNetworking.send(player, new AiDownloadFailedPayload(payload.name()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AiListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            server.execute(() ->
                    ServerPlayNetworking.send(player, new AiListPayload(AiScriptRegistry.list(server))));
        });

        ServerPlayNetworking.registerGlobalReceiver(AiDeleteRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            server.execute(() -> {
                if (!server.getPlayerList().isOp(player.nameAndId())) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cYou lack permission to delete HeroScript files"));
                    return;
                }
                if (!validScriptName(payload.name())) return;
                try {
                    AiScriptIO.deleteByName(server, payload.name());
                    AiScriptRegistry.invalidate(payload.name());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§aDeleted HeroScript '" + payload.name() + "'"));
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete HeroScript '{}'", payload.name(), e);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cFailed to delete '" + payload.name() + "': " + e.getMessage()));
                }
                ServerPlayNetworking.send(player, new AiListPayload(AiScriptRegistry.list(server)));
            });
        });
    }

    public static void syncSettingsToPlayer(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, HeroBotSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, HeroBotSyncPayload.of(RuleConfigIO.serializeCurrentSettings()));
        }
    }

    public static void syncSettingsToAllPlayers() {
        if (currentServer == null) return;
        HeroBotSyncPayload payload = HeroBotSyncPayload.of(RuleConfigIO.serializeCurrentSettings());
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, HeroBotSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
