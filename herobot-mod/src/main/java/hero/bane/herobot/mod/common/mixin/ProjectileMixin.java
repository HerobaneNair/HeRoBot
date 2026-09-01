package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(
            method = "getMovementToShoot(DDDFF)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void noProjectileRandomness(
            double dirX,
            double dirY,
            double dirZ,
            float velocity,
            float inaccuracy,
            CallbackInfoReturnable<Vec3> cir
    ) {
        if (HeroBotSettings.noProjectileRandom) {
            cir.setReturnValue(new Vec3(dirX, dirY, dirZ).normalize().scale(velocity));
        }
    }
}
