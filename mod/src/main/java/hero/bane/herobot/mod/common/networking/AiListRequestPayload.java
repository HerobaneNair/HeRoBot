package hero.bane.herobot.mod.common.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AiListRequestPayload() implements CustomPacketPayload {
    public static final AiListRequestPayload INSTANCE = new AiListRequestPayload();

    public static final Type<AiListRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "ai_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AiListRequestPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
