package hero.bane.herobot.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AiDownloadFailedPayload(String name) implements CustomPacketPayload {
    public static final Type<AiDownloadFailedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "ai_download_failed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AiDownloadFailedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AiDownloadFailedPayload::name,
                    AiDownloadFailedPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
