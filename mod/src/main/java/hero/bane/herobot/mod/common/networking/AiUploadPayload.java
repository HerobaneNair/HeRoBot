package hero.bane.herobot.mod.common.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AiUploadPayload(String name, int index, int count, byte[] data) implements CustomPacketPayload {
    public static final Type<AiUploadPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "ai_upload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AiUploadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AiUploadPayload::name,
                    ByteBufCodecs.VAR_INT, AiUploadPayload::index,
                    ByteBufCodecs.VAR_INT, AiUploadPayload::count,
                    ByteBufCodecs.byteArray(hero.bane.herobot.mod.common.networking.ScriptCompression.MAX_CHUNK), AiUploadPayload::data,
                    AiUploadPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
