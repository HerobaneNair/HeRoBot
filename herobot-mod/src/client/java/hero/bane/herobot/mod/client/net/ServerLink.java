package hero.bane.herobot.mod.client.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ServerLink {
    private static volatile boolean handshooked;

    private ServerLink() {
    }

    public static void onHandshake() {
        handshooked = true;
    }

    public static void reset() {
        handshooked = false;
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type) || handshooked;
    }
}
