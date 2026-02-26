package hero.bane.herobot.mixin;

import hero.bane.herobot.HeroBotSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    protected abstract float getFlyingSpeed();

    protected LivingEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    //creativeFlyDrag can be changed with the herobot command thing, can ignore the yellow squiggly
    @SuppressWarnings("ConstantConditions")
    @ModifyConstant(method = "travelInAir", constant = @Constant(floatValue = 0.91F))
    private float dragAir(float original) {
        if (HeroBotSettings.creativeFlyDrag != 0.09 && (Object) this instanceof Player self) {
            if (self.getAbilities().flying && !onGround())
                return (float) (1.0 - HeroBotSettings.creativeFlyDrag);
        }
        return original;
    }

    //creativeFlySpeed can be changed with the herobot command thing, can ignore the yellow squiggly
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "getFrictionInfluencedSpeed(F)F", at = @At("HEAD"), cancellable = true)
    private void flyingAltSpeed(float slipperiness, CallbackInfoReturnable<Float> cir) {
        if (HeroBotSettings.creativeFlySpeed != 1.0D && (Object) this instanceof Player self) {
            if (self.getAbilities().flying && !onGround())
                cir.setReturnValue(getFlyingSpeed() * (float) HeroBotSettings.creativeFlySpeed);
        }
    }

    @Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
    private void canChangeDimensions(CallbackInfoReturnable<Boolean> cir) {
        if (HeroBotSettings.isCreativeFlying(this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "getKnockback(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifyKnockback(Entity entity, DamageSource damageSource, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (entity instanceof LivingEntity target && target.invulnerableTime < 20) {
            cir.setReturnValue(0.0F);
            return;
        }

        float baseKnockback = (float) self.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        Level level = self.level();

        if (level instanceof ServerLevel serverLevel) {
            float modifiedKnockback = EnchantmentHelper.modifyKnockback(
                    serverLevel,
                    self.getMainHandItem(),
                    entity,
                    damageSource,
                    baseKnockback
            );
            cir.setReturnValue(modifiedKnockback);
        } else {
            cir.setReturnValue(baseKnockback);
        }
    }
}
