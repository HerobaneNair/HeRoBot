package hero.bane.herobot.mod.common.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("checkTotemDeathProtection")
    boolean invokeCheckTotemDeathProtection(DamageSource source);

    @Invoker("playSecondaryHurtSound")
    void invokePlaySecondaryHurtSound(DamageSource source);

    @Accessor("lastDamageSource")
    void setLastDamageSource(DamageSource source);

    @Accessor("lastDamageStamp")
    void setLastDamageStamp(long stamp);
}
