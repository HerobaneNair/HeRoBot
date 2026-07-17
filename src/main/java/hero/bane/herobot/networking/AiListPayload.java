package hero.bane.herobot.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record AiListPayload(List<String> names) implements CustomPacketPayload {
    public static final Type<AiListPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "ai_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AiListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AiListPayload::names,
                    AiListPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
