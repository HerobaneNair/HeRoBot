package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.mod.common.bot.BotPlayer;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack;
import hero.bane.herobot.mod.common.bot.connection.ServerPlayerInterface;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {

    @Shadow
    public abstract @Nullable LivingEntity getControllingPassenger();

    @Shadow
    public abstract void setInput(boolean left, boolean right, boolean up, boolean down);

    @Invoker("controlBoat")
    abstract void invokeControlBoat();

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;floatBoat()V",
                    shift = At.Shift.AFTER
            )
    )
    private void botControlBoat(CallbackInfo ci) {
        if (((Entity) (Object) this).level().isClientSide()) return;
        if (!(this.getControllingPassenger() instanceof BotPlayer botPlayer)) return;

        BotPlayerActionPack ap = ((ServerPlayerInterface) botPlayer).getActionPack();
        float forward = ap.getForward();
        float strafing = ap.getStrafing();
        this.setInput(strafing > 0, strafing < 0, forward > 0, forward < 0);
        this.invokeControlBoat();
    }
}
