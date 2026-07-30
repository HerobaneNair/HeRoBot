package hero.bane.herobot.mod.common.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AiDeleteRequestPayload(String name) implements CustomPacketPayload {
    public static final Type<AiDeleteRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "ai_delete_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AiDeleteRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AiDeleteRequestPayload::name,
                    AiDeleteRequestPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
