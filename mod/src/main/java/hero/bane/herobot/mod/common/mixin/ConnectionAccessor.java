package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.mod.common.bot.connection.ClientConnectionInterface;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface ConnectionAccessor extends ClientConnectionInterface {
    @Accessor("channel")
    void setChannel(Channel channel);
}
