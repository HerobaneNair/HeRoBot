package hero.bane.herobot.fakeplayer.connection;

import io.netty.channel.Channel;

public interface ClientConnectionInterface {
    void setChannel(Channel channel);
}
