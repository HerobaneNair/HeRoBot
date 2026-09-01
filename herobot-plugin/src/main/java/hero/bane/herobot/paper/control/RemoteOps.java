package hero.bane.herobot.paper.control;

import hero.bane.herobot.paper.networking.HeroBotNetwork;
import net.minecraft.server.level.ServerPlayer;

public final class RemoteOps {
    private RemoteOps() {}

    public static boolean canSend(ServerPlayer player) {
        return HeroBotNetwork.canControl(player);
    }

    public static boolean send(ServerPlayer player, ControlOp op) {
        return HeroBotNetwork.sendControl(player, op);
    }
}
