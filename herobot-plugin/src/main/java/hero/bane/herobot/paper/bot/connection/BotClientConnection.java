package hero.bane.herobot.paper.bot.connection;

import hero.bane.herobot.paper.bot.BotEvents;
import hero.bane.herobot.paper.bot.BotPlayer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.NonNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class BotClientConnection extends Connection {

    private static final SocketAddress LOOPBACK =
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    private BotPlayer owner;

    public BotClientConnection(PacketFlow flow) {
        super(flow);
        this.channel = new EmbeddedChannel();
        this.address = LOOPBACK;
    }

    @Override
    public @NonNull SocketAddress getRemoteAddress() {
        return LOOPBACK;
    }

    @Override
    public void setReadOnly() {
    }

    public void setOwner(BotPlayer owner) {
        this.owner = owner;
    }

    @Override
    public void send(@NonNull Packet<?> packet, ChannelFutureListener channelFutureListener, boolean flush) {
        if (owner == null) return;
        String line = ChatCapture.chatLineOf(packet);
        if (line != null) BotEvents.chat(owner, line);
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
