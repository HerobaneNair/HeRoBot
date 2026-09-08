package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.mod.common.bot.BotPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public class AbstractArrowMixin {

    @Redirect(
            method = "doKnockback(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;push(DDD)V")
    )
    private void punchKBPing(LivingEntity entity, double x, double y, double z) {
        if (entity instanceof BotPlayer botPlayer) {
            botPlayer.delayedPush(new Vec3(x, y, z));
        } else {
            entity.push(x, y, z);
        }
    }
}
