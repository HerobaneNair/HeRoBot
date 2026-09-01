package hero.bane.herobot.paper.control;

import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.bot.connection.ServerPlayerInterface;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerControllers {
    private PlayerControllers() {}

    public static PlayerController of(ServerPlayer player) {
        if (player instanceof BotPlayer) {
            return ((ServerPlayerInterface) player).getActionPack();
        }
        return new RemotePlayerController(player);
    }
}
