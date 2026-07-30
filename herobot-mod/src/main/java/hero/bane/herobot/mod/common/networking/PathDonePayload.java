package hero.bane.herobot.mod.common.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record PathDonePayload(int seq) implements CustomPacketPayload {
    public static final Type<PathDonePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "path_done"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PathDonePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PathDonePayload::seq,
                    PathDonePayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
