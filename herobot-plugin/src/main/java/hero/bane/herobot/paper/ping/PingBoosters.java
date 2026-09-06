package hero.bane.herobot.paper.ping;

import hero.bane.herobot.common.ping.BurstClock;
import hero.bane.herobot.common.ping.PingBurstSpec;
import hero.bane.herobot.common.ping.PingDelaySpec;
import hero.bane.herobot.common.ping.PingDelays;
import hero.bane.herobot.common.ping.PingProfile;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.bot.BotPlayer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PingBoosters {

    private static final String PACKET_HANDLER = "packet_handler";

    private static final Field LATENCY_FIELD = findLatencyField();

    private static final class Boost {
        final int realMs;
        final BurstClock clock = new BurstClock();
        int lastWritten = Integer.MIN_VALUE;
        boolean settled;

        Boost(int realMs) {
            this.realMs = realMs;
        }
    }

    private static final Map<UUID, Boost> boosts = new ConcurrentHashMap<>();

    private PingBoosters() {}

    public static boolean set(ServerPlayer player, int targetMs) {
        return setDelay(player, PingDelaySpec.of(targetMs));
    }

    public static boolean setDelay(ServerPlayer player, PingDelaySpec spec) {
        if (player instanceof BotPlayer) return false;
        PingDelays.profile(player.getUUID()).setDelay(spec);
        return apply(player);
    }

    public static boolean setBurst(ServerPlayer player, PingBurstSpec spec) {
        if (player instanceof BotPlayer) return false;
        PingDelays.profile(player.getUUID()).setBurst(spec);
        return apply(player);
    }

    public static void reset(ServerPlayer player) {
        PingDelays.profile(player.getUUID()).reset();
        clear(player);
    }

    private static boolean apply(ServerPlayer player) {
        UUID id = player.getUUID();
        PingProfile profile = PingDelays.profile(id);

        if (!profile.delay().isActive() && !profile.burst().isActive()) {
            clear(player);
            return true;
        }

        int real = knownRealPing(player, id);

        PingBoostHandler handler = ensureHandler(player);
        if (handler == null) return false;

        Boost boost = boosts.computeIfAbsent(id, key -> new Boost(real));
        boost.settled = false;
        boost.clock.setSpec(profile.burst());

        handler.reactivate();
        handler.setOptions(profile.options());
        handler.seedBase(real);
        handler.setDelaySpec(profile.delay());
        handler.setBurstSpec(profile.burst());

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

    public static PingDelaySpec delayOf(ServerPlayer player) {
        return PingDelays.profile(player.getUUID()).delay();
    }

    public static PingBurstSpec burstOf(ServerPlayer player) {
        return PingDelays.profile(player.getUUID()).burst();
    }

    public static PingBoostHandler handlerOf(ServerPlayer player) {
        if (player instanceof BotPlayer) return null;
        ChannelPipeline pipeline = pipelineOf(player);
        if (pipeline == null) return null;
        return pipeline.get(PingBoostHandler.HANDLER_NAME) instanceof PingBoostHandler handler ? handler : null;
    }

    public static void tickPlayer(ServerPlayer player) {
        if (boosts.isEmpty() || player instanceof BotPlayer) return;
        UUID id = player.getUUID();
        Boost boost = boosts.get(id);
        if (boost == null) return;

        PingProfile profile = PingDelays.profile(id);
        PingBoostHandler handler = ensureHandler(player);
        if (handler == null) return;

        handler.reactivate();
        handler.setOptions(profile.options());
        handler.seedBase(boost.realMs);
        handler.setDelaySpec(profile.delay());
        handler.setBurstSpec(profile.burst());

        switch (boost.clock.tick()) {
            case START_BURST -> handler.beginBurst();
            case END_BURST -> handler.releaseBurst();
            default -> {
            }
        }

        if (boost.clock.isFinished() && profile.burst().isActive() && !profile.burst().repeats()) {
            profile.setBurst(PingBurstSpec.NONE);
            handler.setBurstSpec(PingBurstSpec.NONE);
            boost.settled = false;
            if (!profile.delay().isActive()) {
                clear(player);
                return;
            }
        }

        if (boost.settled) return;

        int display = handler.displayPingMs();
        int measured = player.connection.latency();
        if (measured == boost.lastWritten) return;
        if (Math.abs(measured - display) <= tolerance(display)) {
            boost.settled = true;
            return;
        }
        applyDisplayedPing(player, display);
        boost.lastWritten = display;
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
        if (LATENCY_FIELD == null || player.connection == null) return;
        if (player.connection.latency() == pingMs) return;
        try {
            LATENCY_FIELD.setInt(player.connection, pingMs);
        } catch (IllegalAccessException e) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, player));
    }

    private static Field findLatencyField() {
        try {
            Field field = ServerCommonPacketListenerImpl.class.getDeclaredField("latency");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            HeroBot.LOGGER.warn("Could not resolve the latency field; boosted pings will not show in the tab list");
            return null;
        }
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
        Connection connection = player.connection.connection;
        if (connection == null) return null;
        Channel channel = connection.channel;
        if (channel == null || !channel.isOpen()) return null;
        return channel.pipeline();
    }
}
