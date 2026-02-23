package hero.bane.herobot.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldowns.class)
public interface ItemCooldownsInterface {

    @Accessor("cooldowns")
    Map<Identifier, ?> getCooldowns();

    @Accessor("tickCount")
    int getTickCount();
}