package hero.bane.herobot.paper.networking;

import hero.bane.herobot.paper.control.ControlOp;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import hero.bane.herobot.common.networking.ScriptCompression;

public final class HeroBotCodecs {

    public record NamedChunk(String name, int index, int count, byte[] data) {
    }

    private HeroBotCodecs() {
    }

    private static FriendlyByteBuf out() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static FriendlyByteBuf in(byte[] message) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
    }

    private static byte[] drain(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    public static byte[] writeSync(String settingsJson) {
        FriendlyByteBuf buf = out();
        buf.writeVarInt(HeroBotChannels.PROTOCOL);
        buf.writeUtf(settingsJson);
        return drain(buf);
    }

    public static byte[] writeControl(ControlOp op) {
        FriendlyByteBuf buf = out();
        buf.writeVarInt(op.kind());
        buf.writeDouble(op.x());
        buf.writeDouble(op.y());
        buf.writeDouble(op.z());
        buf.writeFloat(op.f0());
        buf.writeFloat(op.f1());
        buf.writeInt(op.i0());
        buf.writeInt(op.i1());
        buf.writeInt(op.i2());
        buf.writeInt(op.i3());
        buf.writeUtf(op.s0());
        return drain(buf);
    }

    public static byte[] writeNameList(List<String> names) {
        FriendlyByteBuf buf = out();
        buf.writeVarInt(names.size());
        for (String name : names) buf.writeUtf(name);
        return drain(buf);
    }

    public static byte[] writeName(String name) {
        FriendlyByteBuf buf = out();
        buf.writeUtf(name);
        return drain(buf);
    }

    public static byte[] writeNamedChunk(String name, int index, int count, byte[] data) {
        FriendlyByteBuf buf = out();
        buf.writeUtf(name);
        buf.writeVarInt(index);
        buf.writeVarInt(count);
        buf.writeByteArray(data);
        return drain(buf);
    }

    public static int readVarInt(byte[] message) {
        return in(message).readVarInt();
    }

    public static String readName(byte[] message) {
        return in(message).readUtf();
    }

    public static List<String> readNameList(byte[] message) {
        FriendlyByteBuf buf = in(message);
        int size = buf.readVarInt();
        List<String> names = new ArrayList<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) names.add(buf.readUtf());
        return names;
    }

    public static NamedChunk readNamedChunk(byte[] message) {
        FriendlyByteBuf buf = in(message);
        return new NamedChunk(
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray(ScriptCompression.MAX_CHUNK));
    }
}
