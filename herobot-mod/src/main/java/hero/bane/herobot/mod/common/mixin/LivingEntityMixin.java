package hero.bane.herobot.mod.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import hero.bane.herobot.mod.common.rule.ModRules;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Shadow
    protected abstract float getFlyingSpeed();

    protected LivingEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @SuppressWarnings("ConstantConditions")
    @ModifyConstant(method = "travelInAir", constant = @Constant(floatValue = 0.91F))
    private float dragAir(float original) {
        if (HeroBotSettings.creativeFlyDrag != 0.09 && (Object) this instanceof Player self) {
            if (self.getAbilities().flying && !onGround())
                return (float) (1.0 - HeroBotSettings.creativeFlyDrag);
        }
        return original;
    }

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
        if (ModRules.isCreativeNoClipFlying(this)) {
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
        if (entity instanceof LivingEntity target && target.invulnerableTime < HeroBotSettings.damageInvulnerableTicks()) {
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
            cir.setReturnValue(modifiedKnockback / 2.0F);
        } else {
            cir.setReturnValue(baseKnockback / 2.0F);
        }
    }

    @ModifyConstant(method = "hurtServer", constant = @Constant(intValue = 10))
    private int hurtAnimationLength(int original) {
        return HeroBotSettings.damageTicks;
    }

    @ModifyConstant(method = "hurtServer", constant = @Constant(intValue = 20))
    private int hurtInvulnerableLength(int original) {
        return HeroBotSettings.damageInvulnerableTicks();
    }

    @ModifyConstant(method = "hurtServer", constant = @Constant(floatValue = 10.0F))
    private float hurtCooldownThreshold(float original) {
        return HeroBotSettings.damageTicks;
    }

    @ModifyExpressionValue(
            method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 knockbackIgnoreVerticalVelocity(Vec3 original) {
        if (HeroBotSettings.kbScaling) return original;
        return new Vec3(original.x, 0.0, original.z);
    }

    @ModifyExpressionValue(
            method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;onGround()Z")
    )
    private boolean knockbackAlwaysGrounded(boolean original) {
        return original || !HeroBotSettings.kbScaling;
    }

    @Unique
    private boolean blockedHit = false;

    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float trackBlockedHit(LivingEntity instance, ServerLevel serverLevel, DamageSource damageSource, float damageAmount, Operation<Float> original) {
        float blockedAmount = original.call(instance, serverLevel, damageSource, damageAmount);
        blockedHit = blockedAmount > 0.0F && instance instanceof Player && !(instance instanceof BotPlayer);
        return blockedAmount;
    }

    @ModifyReturnValue(method = "hurtServer", at = @At("RETURN"))
    private boolean handleBlockedHit(boolean original) {
        if (blockedHit) {
            blockedHit = false;
            if (HeroBotSettings.shieldStunning) {
                this.invulnerableTime = 0;
                return false;
            } else {
                LivingEntity self = (LivingEntity) (Object) this;
                self.hurtDuration = 0;
                self.hurtTime = 0;
            }
        }
        return original;
    }
}
