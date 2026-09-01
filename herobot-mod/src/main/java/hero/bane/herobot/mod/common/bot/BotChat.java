package hero.bane.herobot.mod.common.bot;

import hero.bane.herobot.common.ping.PingDelayOptions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

import java.util.function.BooleanSupplier;

public final class BotChat {

    private BotChat() {
    }

    public static void send(ServerPlayer player, String message, boolean asOperator) {
        send(player, message, () -> asOperator);
    }

    public static void send(ServerPlayer player, String message, BooleanSupplier asOperator) {
        if (message == null || message.isBlank()) return;

        boolean operator = asOperator.getAsBoolean();
        if (player instanceof BotPlayer bot
                && bot.deferByPing(PingDelayOptions.Category.CHAT, () -> dispatch(bot, message, operator))) {
            return;
        }
        dispatch(player, message, operator);
    }

    private static void dispatch(ServerPlayer player, String message, boolean asOperator) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        if (message.startsWith("/")) {
            CommandSourceStack source = player.createCommandSourceStack();
            if (asOperator) source = source.withPermission(PermissionSet.ALL_PERMISSIONS);
            server.getCommands().performPrefixedCommand(source, message);
            return;
        }

        server.getPlayerList().broadcastChatMessage(
                PlayerChatMessage.unsigned(player.getUUID(), message),
                player,
                ChatType.bind(ChatType.CHAT, player));
    }
}
