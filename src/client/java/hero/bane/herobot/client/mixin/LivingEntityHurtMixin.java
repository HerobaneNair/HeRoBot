package hero.bane.herobot.client.mixin;

import hero.bane.herobot.HeroBotSettings;
import hero.bane.herobot.client.HeroBotClient;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityHurtMixin {

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void damageEventHurtLength(DamageSource damageSource, CallbackInfo ci) {
        if (stretchHurtAnimation()) {
            ((LivingEntity) (Object) this).invulnerableTime = HeroBotSettings.damageInvulnerableTicks();
        }
    }

    @Inject(method = "animateHurt", at = @At("TAIL"))
    private void animateHurtLength(float yaw, CallbackInfo ci) {
        stretchHurtAnimation();
    }

    @Unique
    private boolean stretchHurtAnimation() {
        if (!HeroBotClient.isHeroBotLoaded()) return false;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.hurtTime <= 0) return false;
        self.hurtDuration = HeroBotSettings.damageTicks;
        self.hurtTime = self.hurtDuration;
        return true;
    }
}
