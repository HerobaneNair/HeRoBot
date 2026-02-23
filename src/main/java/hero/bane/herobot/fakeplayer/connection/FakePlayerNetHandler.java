package hero.bane.herobot.fakeplayer.connection;

import hero.bane.herobot.fakeplayer.FakePlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;

import java.util.Set;

public class FakePlayerNetHandler extends ServerGamePacketListenerImpl {

    public FakePlayerNetHandler(
            MinecraftServer server,
            Connection connection,
            ServerPlayer player,
            CommonListenerCookie cookie
    ) {
        super(server, connection, player, cookie);
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void disconnect(Component message) {
        if (message.getContents() instanceof TranslatableContents text) {
            String key = text.getKey();
            if (key.equals("multiplayer.disconnect.idling")
                    || key.equals("multiplayer.disconnect.duplicate_login")) {
                ((FakePlayer) player).kill(message);
            }
        }
    }

    @Override
    public void teleport(PositionMoveRotation pos, Set<Relative> relatives) {
        super.teleport(pos, relatives);
        if (player.level().getPlayerByUUID(player.getUUID()) != null) {
            resetPosition();
            player.level().getChunkSource().move(player);
        }
    }
}
