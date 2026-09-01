package hero.bane.herobot.paper.bot.connection;

import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.bot.BotRegistry;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.jspecify.annotations.NonNull;

import java.util.Set;

public class BotPlayerNetHandler extends ServerGamePacketListenerImpl {

    public BotPlayerNetHandler(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        super(server, connection, player, cookie);
    }

    @Override
    public void disconnect(@NonNull DisconnectionDetails details) {
        if (player instanceof BotPlayer bot) {
            BotRegistry.despawn(bot);
            return;
        }
        super.disconnect(details);
    }

    @Override
    public void teleport(@NonNull PositionMoveRotation pos, @NonNull Set<Relative> relatives) {
        super.teleport(pos, relatives);
        if (player.level().getPlayerByUUID(player.getUUID()) != null) {
            resetPosition();
            player.level().getChunkSource().move(player);
        }
    }
}
