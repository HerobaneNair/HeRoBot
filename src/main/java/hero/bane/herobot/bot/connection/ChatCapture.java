package hero.bane.herobot.bot.connection;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.jspecify.annotations.Nullable;

public final class ChatCapture {
    private ChatCapture() {}

    public static @Nullable String chatLineOf(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket(Component content1, boolean overlay)) {
            if (overlay) return null;
            return content1.getString();
        }
        if (packet instanceof ClientboundPlayerChatPacket p) {
            Component content = p.unsignedContent() != null
                    ? p.unsignedContent()
                    : Component.literal(p.body().content());
            return p.chatType().decorate(content).getString();
        }
        if (packet instanceof ClientboundDisguisedChatPacket(
                Component message, net.minecraft.network.chat.ChatType.Bound chatType
        )) {
            return chatType.decorate(message).getString();
        }
        return null;
    }
}
