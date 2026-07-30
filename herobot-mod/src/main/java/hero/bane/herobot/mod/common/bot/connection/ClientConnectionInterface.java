package hero.bane.herobot.mod.common.bot.connection;

import io.netty.channel.Channel;

public interface ClientConnectionInterface {
    void setChannel(Channel channel);
}
