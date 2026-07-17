package hero.bane.herobot.control;

import hero.bane.herobot.networking.ControlPlayerPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class RemoteOps {
    private RemoteOps() {}

    public static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, ControlPlayerPayload.TYPE);
    }

    public static boolean send(ServerPlayer player, ControlOp op) {
        if (!canSend(player)) return false;
        ServerPlayNetworking.send(player, new ControlPlayerPayload(op));
        return true;
    }
}
