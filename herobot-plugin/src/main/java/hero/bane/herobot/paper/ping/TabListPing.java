package hero.bane.herobot.paper.ping;

import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.List;

public final class TabListPing {

    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> LATENCY =
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY);

    private static int ticks;

    private TabListPing() {
    }

    public static void tick(MinecraftServer server) {
        int interval = HeroBotSettings.tabListPing;
        if (++ticks < interval) return;
        ticks = 0;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(LATENCY, players));
    }
}
