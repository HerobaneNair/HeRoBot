package hero.bane.herobot.mod.common.bot.connection;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class BotClientConnection extends Connection {
    public BotClientConnection(PacketFlow p) {
        super(p);
        ((ClientConnectionInterface) this).setChannel(new EmbeddedChannel());
    }

    @Override
    public void setReadOnly() {
    }

    @Override
    public void send(@NonNull Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl) {
    }

    @Override
    public void handleDisconnection() {
    }

    @Override
    public void setListenerForServerboundHandshake(@NonNull PacketListener packetListener) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(@NonNull ProtocolInfo<T> protocolInfo, @NonNull T packetListener) {
    }
}
