package hero.bane.herobot.mixin;

import hero.bane.herobot.HeroBot;
import hero.bane.herobot.ai.AiScriptRegistry;
import hero.bane.herobot.bot.connection.ChatCapture;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD")
    )
    private void herobot$captureChat(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        String line = ChatCapture.chatLineOf(packet);
        if (line == null) return;
        if (!((Object) this instanceof ServerGamePacketListenerImpl gl)) return;
        ServerPlayer player = gl.player;
        if (player == null) return;
        try {
            AiScriptRegistry.onChatMessage(player, line);
        } catch (Throwable t) {
            HeroBot.LOGGER.warn("Failed to route chat to On message event: {}", t.toString());
        }
    }
}
