package hero.bane.herobot;

import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.AiScriptIO;
import hero.bane.herobot.ai.AiScriptRegistry;
import hero.bane.herobot.command.*;
import hero.bane.herobot.control.RemotePathState;
import hero.bane.herobot.networking.AiDeleteRequestPayload;
import hero.bane.herobot.networking.AiDownloadFailedPayload;
import hero.bane.herobot.networking.AiDownloadPayload;
import hero.bane.herobot.networking.AiDownloadRequestPayload;
import hero.bane.herobot.networking.AiListPayload;
import hero.bane.herobot.networking.AiListRequestPayload;
import hero.bane.herobot.networking.AiUploadPayload;
import hero.bane.herobot.networking.ChunkReassembler;
import hero.bane.herobot.networking.ControlPlayerPayload;
import hero.bane.herobot.networking.HeroBotSyncPayload;
import hero.bane.herobot.networking.PathDonePayload;
import hero.bane.herobot.networking.ScriptCompression;
import hero.bane.herobot.rule.RuleConfigIO;
import hero.bane.herobot.util.delayer.DelayedQueue;
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

public class HeroBot implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeroBot");
    public static MinecraftServer currentServer = null;

    private static final ChunkReassembler UPLOAD_REASSEMBLER = new ChunkReassembler();
    private static final java.util.Set<String> ANNOUNCED_SAVES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        HeroBotSettings.init();

        PayloadTypeRegistry.playS2C().register(HeroBotSyncPayload.TYPE, HeroBotSyncPayload.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(ControlPlayerPayload.TYPE, ControlPlayerPayload.STREAM_CODEC);

        PayloadTypeRegistry.playC2S().register(PathDonePayload.TYPE, PathDonePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PathDonePayload.TYPE, (payload, context) ->
                RemotePathState.finish(context.player(), payload.seq()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                RemotePathState.clear(handler.player.getUUID()));

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
            PlayerSpawnCommand.register(dispatcher, registryAccess);
            DistanceCommand.register(dispatcher, registryAccess);
            HeroBotCommand.register(dispatcher, registryAccess);
            DelayedCommand.register(dispatcher, registryAccess);
            ChunkResetterCommand.register(dispatcher, registryAccess);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            AiScriptRegistry.reset();
        });

        // Respawn replaces the ServerPlayer instance but keeps the UUID, so the runner survives.
        // Stop the script and point it at the new entity; the bot keeps it loaded for `ai run`.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                AiScriptRegistry.stopRunning(newPlayer));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                syncSettingsToPlayer(handler.player));

        RuleConfigIO.onSettingsChanged = HeroBot::syncSettingsToAllPlayers;

        ServerTickEvents.END_SERVER_TICK.register(DelayedQueue::tick);
        ServerTickEvents.END_SERVER_TICK.register(AiScriptRegistry::tickAll);
    }

    private static boolean validScriptName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }

    private void registerAiNetworkHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(AiUploadPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
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
                    AiScript script = AiScriptIO.fromJson(json, payload.name());
                    AiScriptIO.saveByName(server, payload.name(), script);
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
            if (server == null) return;
            server.execute(() -> {
                if (!validScriptName(payload.name())) return;
                try {
                    AiScript script = AiScriptRegistry.load(server, payload.name());
                    if (script == null) return;
                    byte[] data = ScriptCompression.compress(AiScriptIO.toJson(script));
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
            if (server == null) return;
            server.execute(() ->
                    ServerPlayNetworking.send(player, new AiListPayload(AiScriptRegistry.list(server))));
        });

        ServerPlayNetworking.registerGlobalReceiver(AiDeleteRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
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
            ServerPlayNetworking.send(player, new HeroBotSyncPayload(RuleConfigIO.serializeCurrentSettings()));
        }
    }

    public static void syncSettingsToAllPlayers() {
        if (currentServer == null) return;
        HeroBotSyncPayload payload = new HeroBotSyncPayload(RuleConfigIO.serializeCurrentSettings());
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, HeroBotSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
