package hero.bane.herobot.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AiDownloadRequestPayload(String name) implements CustomPacketPayload {
    public static final Type<AiDownloadRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "ai_download_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AiDownloadRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AiDownloadRequestPayload::name,
                    AiDownloadRequestPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
