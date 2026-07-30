package hero.bane.herobot.mod.common.networking;

import hero.bane.herobot.mod.common.control.ControlOp;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record ControlPlayerPayload(ControlOp op) implements CustomPacketPayload {
    public static final Type<ControlPlayerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ControlPlayerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ControlOp.STREAM_CODEC, ControlPlayerPayload::op,
                    ControlPlayerPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
