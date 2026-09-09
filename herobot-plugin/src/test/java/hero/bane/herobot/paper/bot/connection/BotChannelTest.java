package hero.bane.herobot.paper.bot.connection;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotChannelTest {

    private static ChannelHandler interceptor() {
        return new ChannelDuplexHandler();
    }

    private static EmbeddedChannel botChannel() {
        EmbeddedChannel channel = BotChannel.create();
        channel.pipeline().addLast(BotChannel.PACKET_HANDLER, new ChannelDuplexHandler());
        return channel;
    }

    @Test
    void carriesTheVanillaHandlerNames() {
        List<String> names = botChannel().pipeline().names();
        for (String required : List.of(BotChannel.TIMEOUT, BotChannel.SPLITTER, BotChannel.DECODER,
                BotChannel.UNBUNDLER, BotChannel.PREPENDER, BotChannel.ENCODER, BotChannel.BUNDLER,
                BotChannel.PACKET_HANDLER)) {
            assertTrue(names.contains(required), "missing '" + required + "' in " + names);
        }
    }

    @Test
    void channelIsActiveSoInjectorsDoNotSkipIt() {
        EmbeddedChannel channel = botChannel();
        assertTrue(channel.isActive());
        assertNotNull(channel.eventLoop());
    }

    @Test
    void acceptsProtocolLibInjection() {
        ChannelPipeline pipeline = botChannel().pipeline();

        String encoderName = pipeline.get("outbound_config") != null ? "outbound_config" : "encoder";
        String decoderName = pipeline.get("inbound_config") != null ? "inbound_config" : "decoder";

        assertDoesNotThrow(() -> {
            pipeline.addAfter(encoderName, "protocol_lib_wire_packet_encoder", interceptor());
            pipeline.addBefore(decoderName, "protocol_lib_inbound_protocol_getter", interceptor());
            pipeline.addAfter(decoderName, "protocol_lib_inbound_interceptor", interceptor());
        });
    }

    @Test
    void acceptsPacketEventsInjection() {
        ChannelPipeline pipeline = botChannel().pipeline();

        assertNotNull(pipeline.get("splitter"), "PacketEvents skips channels without a 'splitter'");

        String decoderName = pipeline.names().contains("inbound_config") ? "inbound_config" : "decoder";
        String encoderName = pipeline.names().contains("outbound_config") ? "outbound_config" : "encoder";

        assertDoesNotThrow(() -> {
            pipeline.addBefore(decoderName, "pe-decoder", interceptor());
            pipeline.addBefore(encoderName, "pe-encoder", interceptor());
        });
    }

    @Test
    void handlersStayInertWhenPacketsAreWritten() {
        EmbeddedChannel channel = botChannel();
        assertDoesNotThrow(() -> channel.writeOutbound("packet"));
        assertDoesNotThrow(() -> channel.writeInbound("packet"));
        channel.finishAndReleaseAll();
    }
}
