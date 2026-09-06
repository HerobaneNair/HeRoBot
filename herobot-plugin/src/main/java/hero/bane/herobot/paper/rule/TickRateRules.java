package hero.bane.herobot.paper.rule;

import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TickRateRules {

    private static final float NORMAL_TICK_RATE = 20.0F;

    private static final Map<UUID, Integer> OVERRIDDEN = new HashMap<>();

    private static float lastTickRate = NORMAL_TICK_RATE;
    private static boolean lastFrozen;
    private static boolean lastEnabled;
    private static int stateVersion;

    private TickRateRules() {
    }

    public static void tick(MinecraftServer server) {
        ServerTickRateManager manager = server.tickRateManager();
        float tickRate = manager.tickrate();
        boolean frozen = manager.isFrozen();
        boolean enabled = HeroBotSettings.clientsIgnoreSlowTickRate;

        if (tickRate != lastTickRate || frozen != lastFrozen || enabled != lastEnabled) {
            lastTickRate = tickRate;
            lastFrozen = frozen;
            lastEnabled = enabled;
            stateVersion++;
        }

        if (!enabled) {
            restore(server);
            return;
        }

        if (tickRate == NORMAL_TICK_RATE && !frozen) {
            OVERRIDDEN.clear();
            return;
        }

        ClientboundTickingStatePacket packet = new ClientboundTickingStatePacket(NORMAL_TICK_RATE, false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer version = OVERRIDDEN.get(player.getUUID());
            if (version != null && version == stateVersion) continue;
            player.connection.send(packet);
            OVERRIDDEN.put(player.getUUID(), stateVersion);
        }
    }

    public static void forget(UUID playerId) {
        OVERRIDDEN.remove(playerId);
    }

    public static void restore(MinecraftServer server) {
        if (OVERRIDDEN.isEmpty()) return;

        ClientboundTickingStatePacket packet = ClientboundTickingStatePacket.from(server.tickRateManager());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
        OVERRIDDEN.clear();
    }
}
