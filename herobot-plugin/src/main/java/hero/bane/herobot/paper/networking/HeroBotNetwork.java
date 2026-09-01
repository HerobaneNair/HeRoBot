package hero.bane.herobot.paper.networking;

import hero.bane.herobot.common.ai.AiScriptCodec;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.paper.ai.AiScriptIO;
import hero.bane.herobot.paper.ai.AiScriptRegistry;
import hero.bane.herobot.paper.control.ControlOp;
import hero.bane.herobot.paper.control.RemotePathState;
import hero.bane.herobot.paper.rule.RuleConfigIO;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import hero.bane.herobot.common.networking.ChunkReassembler;
import hero.bane.herobot.common.networking.ScriptCompression;

public final class HeroBotNetwork implements PluginMessageListener, Listener {

    public static final String EDIT_PERMISSION = "herobot.ai.edit";

    private static final ChunkReassembler UPLOAD_REASSEMBLER = new ChunkReassembler();
    private static final Set<String> ANNOUNCED_SAVES = ConcurrentHashMap.newKeySet();

    private static volatile JavaPlugin plugin;
    private static volatile MinecraftServer server;

    public static void init(JavaPlugin owner, MinecraftServer minecraftServer) {
        plugin = owner;
        server = minecraftServer;

        HeroBotNetwork listener = new HeroBotNetwork();
        Messenger messenger = Bukkit.getMessenger();
        for (String channel : HeroBotChannels.OUTGOING) {
            messenger.registerOutgoingPluginChannel(owner, channel);
        }
        for (String channel : HeroBotChannels.INCOMING) {
            messenger.registerIncomingPluginChannel(owner, channel, listener);
        }
        Bukkit.getPluginManager().registerEvents(listener, owner);
    }

    public static void shutdown(JavaPlugin owner) {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(owner);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(owner);
        ANNOUNCED_SAVES.clear();
        plugin = null;
        server = null;
    }

    public static void forget(UUID id) {
        ANNOUNCED_SAVES.removeIf(key -> key.startsWith(id + ":"));
    }

    public static boolean canControl(ServerPlayer player) {
        return isListening(player.getBukkitEntity(), HeroBotChannels.CONTROL);
    }

    public static boolean sendControl(ServerPlayer player, ControlOp op) {
        if (!canControl(player)) return false;
        return send(player.getBukkitEntity(), HeroBotChannels.CONTROL, HeroBotCodecs.writeControl(op));
    }

    public static void sendSettings(Player player) {
        send(player, HeroBotChannels.SYNC, HeroBotCodecs.writeSync(RuleConfigIO.serializeCurrentSettings()));
    }

    public static void sendSettingsToAll() {
        if (plugin == null) return;
        byte[] message = HeroBotCodecs.writeSync(RuleConfigIO.serializeCurrentSettings());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isListening(player, HeroBotChannels.SYNC)) {
                send(player, HeroBotChannels.SYNC, message);
            }
        }
    }

    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (HeroBotChannels.SYNC.equals(event.getChannel())) sendSettings(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isListening(event.getPlayer(), HeroBotChannels.SYNC)) sendSettings(event.getPlayer());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player bukkitPlayer, byte @NotNull [] message) {
        MinecraftServer minecraftServer = server;
        if (minecraftServer == null) return;

        ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();
        try {
            switch (channel) {
                case HeroBotChannels.PATH_DONE -> {
                    int seq = HeroBotCodecs.readVarInt(message);
                    minecraftServer.execute(() -> RemotePathState.finish(player, seq));
                }
                case HeroBotChannels.AI_LIST_REQUEST ->
                        minecraftServer.execute(() -> sendList(bukkitPlayer, minecraftServer));
                case HeroBotChannels.AI_DOWNLOAD_REQUEST -> {
                    String name = HeroBotCodecs.readName(message);
                    minecraftServer.execute(() -> handleDownload(bukkitPlayer, minecraftServer, name));
                }
                case HeroBotChannels.AI_DELETE_REQUEST -> {
                    String name = HeroBotCodecs.readName(message);
                    minecraftServer.execute(() -> handleDelete(bukkitPlayer, player, minecraftServer, name));
                }
                case HeroBotChannels.AI_UPLOAD -> {
                    HeroBotCodecs.NamedChunk chunk = HeroBotCodecs.readNamedChunk(message);
                    minecraftServer.execute(() -> handleUpload(bukkitPlayer, player, minecraftServer, chunk));
                }
                default -> {
                }
            }
        } catch (Exception e) {
            HeroBot.LOGGER.warn("Malformed HeroBot packet on '{}' from {}", channel, bukkitPlayer.getName(), e);
        }
    }

    private static void handleUpload(Player bukkitPlayer, ServerPlayer player, MinecraftServer minecraftServer,
                                     HeroBotCodecs.NamedChunk chunk) {
        if (!bukkitPlayer.hasPermission(EDIT_PERMISSION)) {
            player.sendSystemMessage(Component.literal("§cYou lack permission to upload HeroScript files"));
            return;
        }
        if (!validScriptName(chunk.name())) return;

        String key = player.getUUID() + ":" + chunk.name();
        byte[] full = UPLOAD_REASSEMBLER.accept(key, chunk.index(), chunk.count(), chunk.data());
        if (full == null) return;

        try {
            String json = ScriptCompression.decompress(full);
            AiScript script = AiScriptCodec.fromJson(json, chunk.name());
            AiScriptIO.saveByName(minecraftServer, chunk.name(), json);
            AiScriptRegistry.put(chunk.name(), script);
            if (ANNOUNCED_SAVES.add(key)) {
                player.sendSystemMessage(Component.literal("§aSaved HeroScript '" + chunk.name() + "'"));
            }
            sendList(bukkitPlayer, minecraftServer);
        } catch (Exception e) {
            HeroBot.LOGGER.warn("Failed to save uploaded HeroScript '{}'", chunk.name(), e);
            player.sendSystemMessage(Component.literal(
                    "§cFailed to save '" + chunk.name() + "': " + e.getMessage()));
        }
    }

    private static void handleDownload(Player bukkitPlayer, MinecraftServer minecraftServer, String name) {
        if (!validScriptName(name)) return;
        try {
            AiScript script = AiScriptRegistry.load(minecraftServer, name);
            if (script == null) return;
            String json = AiScriptIO.rawJsonByName(minecraftServer, name);
            if (json == null) return;

            List<byte[]> chunks = ScriptCompression.chunk(ScriptCompression.compress(json));
            int count = chunks.size();
            for (int i = 0; i < count; i++) {
                send(bukkitPlayer, HeroBotChannels.AI_DOWNLOAD,
                        HeroBotCodecs.writeNamedChunk(name, i, count, chunks.get(i)));
            }
        } catch (Exception e) {
            HeroBot.LOGGER.warn("Failed to load HeroScript '{}'", name, e);
            send(bukkitPlayer, HeroBotChannels.AI_DOWNLOAD_FAILED, HeroBotCodecs.writeName(name));
        }
    }

    private static void handleDelete(Player bukkitPlayer, ServerPlayer player, MinecraftServer minecraftServer,
                                     String name) {
        if (!bukkitPlayer.hasPermission(EDIT_PERMISSION)) {
            player.sendSystemMessage(Component.literal("§cYou lack permission to delete HeroScript files"));
            return;
        }
        if (!validScriptName(name)) return;

        try {
            AiScriptIO.deleteByName(minecraftServer, name);
            AiScriptRegistry.invalidate(name);
            player.sendSystemMessage(Component.literal("§aDeleted HeroScript '" + name + "'"));
        } catch (Exception e) {
            HeroBot.LOGGER.warn("Failed to delete HeroScript '{}'", name, e);
            player.sendSystemMessage(Component.literal(
                    "§cFailed to delete '" + name + "': " + e.getMessage()));
        }
        sendList(bukkitPlayer, minecraftServer);
    }

    private static void sendList(Player bukkitPlayer, MinecraftServer minecraftServer) {
        send(bukkitPlayer, HeroBotChannels.AI_LIST,
                HeroBotCodecs.writeNameList(AiScriptRegistry.list(minecraftServer)));
    }

    private static boolean send(Player player, String channel, byte[] message) {
        JavaPlugin owner = plugin;
        if (owner == null || !player.isOnline()) return false;
        try {
            player.sendPluginMessage(owner, channel, message);
            return true;
        } catch (Exception e) {
            HeroBot.LOGGER.warn("Failed sending HeroBot packet on '{}' to {}", channel, player.getName(), e);
            return false;
        }
    }

    private static boolean isListening(Player player, String channel) {
        return player.getListeningPluginChannels().contains(channel);
    }

    private static boolean validScriptName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }
}
