package hero.bane.herobot.control;

import hero.bane.herobot.bot.BotPlayer;
import hero.bane.herobot.bot.connection.ServerPlayerInterface;
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
