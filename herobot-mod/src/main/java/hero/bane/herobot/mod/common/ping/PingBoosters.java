package hero.bane.herobot.mod.common.ping;

import hero.bane.herobot.common.ping.PingDelays;
import hero.bane.herobot.mod.common.HeroBot;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import hero.bane.herobot.mod.common.mixin.ConnectionAccessor;
import hero.bane.herobot.mod.common.mixin.ServerCommonPacketListenerImplAccessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PingBoosters {

    private static final String PACKET_HANDLER = "packet_handler";

    private static final class Boost {
        final int targetMs;
        final int realMs;
        int lastWritten = Integer.MIN_VALUE;
        boolean settled;

        Boost(int targetMs, int realMs) {
            this.targetMs = targetMs;
            this.realMs = realMs;
        }
    }

    private static final Map<UUID, Boost> boosts = new ConcurrentHashMap<>();

    private PingBoosters() {}

    public static boolean set(ServerPlayer player, int targetMs) {
        if (player instanceof BotPlayer) return false;
        if (targetMs <= 0) {
            clear(player);
            return true;
        }

        UUID id = player.getUUID();
        int real = knownRealPing(player, id);

        PingBoostHandler handler = ensureHandler(player);
        if (handler == null) return false;

        Boost boost = new Boost(targetMs, real);
        boosts.put(id, boost);
        handler.reactivate();
        handler.setOptions(PingDelays.of(player.getUUID()));
        handler.seedBase(real);
        handler.setTarget(targetMs);

        int display = handler.displayPingMs();
        applyDisplayedPing(player, display);
        boost.lastWritten = display;
        return true;
    }

    public static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        Boost boost = boosts.remove(id);
        PingBoostHandler handler = handlerOf(player);
        int real = handler != null && handler.hasMeasuredBase()
                ? handler.baseMs()
                : boost != null ? boost.realMs : -1;
        removeHandler(player);
        if (real >= 0) applyDisplayedPing(player, real);
    }

    public static void forget(UUID id) {
        boosts.remove(id);
    }

    public static int target(ServerPlayer player) {
        Boost boost = boosts.get(player.getUUID());
        return boost == null ? 0 : boost.targetMs;
    }

    public static PingBoostHandler handlerOf(ServerPlayer player) {
        if (player instanceof BotPlayer) return null;
        ChannelPipeline pipeline = pipelineOf(player);
        if (pipeline == null) return null;
        return pipeline.get(PingBoostHandler.HANDLER_NAME) instanceof PingBoostHandler handler ? handler : null;
    }

    public static void tick(MinecraftServer server) {
        if (boosts.isEmpty()) return;
        for (Map.Entry<UUID, Boost> entry : boosts.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player instanceof BotPlayer) continue;

            Boost boost = entry.getValue();
            PingBoostHandler handler = ensureHandler(player);
            if (handler == null) continue;

            handler.reactivate();
            handler.setOptions(PingDelays.of(player.getUUID()));
            handler.seedBase(boost.realMs);
            handler.setTarget(boost.targetMs);

            if (boost.settled) continue;

            int display = handler.displayPingMs();
            int measured = player.connection.latency();
            if (measured == boost.lastWritten) continue;
            if (Math.abs(measured - display) <= tolerance(display)) {
                boost.settled = true;
                continue;
            }
            applyDisplayedPing(player, display);
            boost.lastWritten = display;
        }
    }

    public static void shutdown(MinecraftServer server) {
        for (UUID id : boosts.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) removeHandler(player);
        }
        boosts.clear();
    }

    private static int tolerance(int displayMs) {
        return Math.max(5, displayMs / 20);
    }

    private static int knownRealPing(ServerPlayer player, UUID id) {
        PingBoostHandler existing = handlerOf(player);
        if (existing != null && existing.hasMeasuredBase()) return existing.baseMs();
        Boost previous = boosts.get(id);
        if (previous != null) return previous.realMs;
        return Math.max(0, player.connection.latency());
    }

    private static void applyDisplayedPing(ServerPlayer player, int pingMs) {
        if (player.connection == null) return;
        if (player.connection.latency() == pingMs) return;
        ((ServerCommonPacketListenerImplAccessor) player.connection).setLatency(pingMs);
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, player));
    }

    private static PingBoostHandler ensureHandler(ServerPlayer player) {
        ChannelPipeline pipeline = pipelineOf(player);
        if (pipeline == null) return null;

        if (pipeline.get(PingBoostHandler.HANDLER_NAME) instanceof PingBoostHandler existing) return existing;

        if (pipeline.get(PACKET_HANDLER) == null) {
            HeroBot.LOGGER.warn("No '{}' in {}'s pipeline ({}); refusing to install the ping boost handler",
                    PACKET_HANDLER, player.getGameProfile().name(), pipeline.names());
            return null;
        }

        PingBoostHandler handler = new PingBoostHandler();
        try {
            pipeline.addBefore(PACKET_HANDLER, PingBoostHandler.HANDLER_NAME, handler);
            return handler;
        } catch (RuntimeException e) {
            ChannelHandler raced = pipeline.get(PingBoostHandler.HANDLER_NAME);
            if (raced instanceof PingBoostHandler racedHandler) return racedHandler;
            HeroBot.LOGGER.warn("Could not install ping boost handler for {}: {}",
                    player.getGameProfile().name(), e.toString());
            return null;
        }
    }

    private static void removeHandler(ServerPlayer player) {
        ChannelPipeline pipeline = pipelineOf(player);
        if (pipeline == null) return;
        if (!(pipeline.get(PingBoostHandler.HANDLER_NAME) instanceof PingBoostHandler handler)) return;
        handler.shutdown();
        try {
            pipeline.remove(PingBoostHandler.HANDLER_NAME);
        } catch (RuntimeException ignored) {
        }
    }

    private static ChannelPipeline pipelineOf(ServerPlayer player) {
        if (player instanceof BotPlayer || player.connection == null) return null;
        Connection connection = ((ServerCommonPacketListenerImplAccessor) player.connection).getConnection();
        if (connection == null) return null;
        Channel channel = ((ConnectionAccessor) connection).getChannel();
        if (channel == null || !channel.isOpen()) return null;
        return channel.pipeline();
    }
}
