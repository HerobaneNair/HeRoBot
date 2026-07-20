package hero.bane.herobot.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record HeroBotSyncPayload(int protocol, String settingsJson) implements CustomPacketPayload {

    public static final int PROTOCOL = 1;

    public static final Type<HeroBotSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("herobot", "sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HeroBotSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HeroBotSyncPayload::protocol,
                    ByteBufCodecs.STRING_UTF8, HeroBotSyncPayload::settingsJson,
                    HeroBotSyncPayload::new
            );

    public static HeroBotSyncPayload of(String settingsJson) {
        return new HeroBotSyncPayload(PROTOCOL, settingsJson);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
