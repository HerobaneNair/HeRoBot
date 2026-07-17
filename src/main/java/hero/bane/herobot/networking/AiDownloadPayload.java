package hero.bane.herobot.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AiDownloadPayload(String name, int index, int count, byte[] data) implements CustomPacketPayload {
    public static final Type<AiDownloadPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "ai_download"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AiDownloadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AiDownloadPayload::name,
                    ByteBufCodecs.VAR_INT, AiDownloadPayload::index,
                    ByteBufCodecs.VAR_INT, AiDownloadPayload::count,
                    ByteBufCodecs.byteArray(hero.bane.herobot.networking.ScriptCompression.MAX_CHUNK), AiDownloadPayload::data,
                    AiDownloadPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
