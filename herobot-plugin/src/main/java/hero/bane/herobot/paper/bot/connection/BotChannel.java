package hero.bane.herobot.paper.bot.connection;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;

public final class BotChannel {

    public static final String TIMEOUT = "timeout";
    public static final String SPLITTER = "splitter";
    public static final String DECODER = "decoder";
    public static final String UNBUNDLER = "unbundler";
    public static final String PREPENDER = "prepender";
    public static final String ENCODER = "encoder";
    public static final String BUNDLER = "bundler";
    public static final String PACKET_HANDLER = "packet_handler";

    private BotChannel() {
    }

    public static EmbeddedChannel create() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline()
                .addLast(TIMEOUT, new Passthrough())
                .addLast(SPLITTER, new Passthrough())
                .addLast(DECODER, new Passthrough())
                .addLast(UNBUNDLER, new Passthrough())
                .addLast(PREPENDER, new Passthrough())
                .addLast(ENCODER, new Passthrough())
                .addLast(BUNDLER, new Passthrough());
        return channel;
    }

    private static final class Passthrough extends ChannelDuplexHandler {
    }
}
