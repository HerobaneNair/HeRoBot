package hero.bane.herobot.mod.client.mixin;

import hero.bane.herobot.mod.client.HeroBotClient;
import hero.bane.herobot.mod.client.control.ClientPlayerController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityViewRotationMixin {
    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void herobot$smoothViewYaw(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!HeroBotClient.isHeroBotLoaded()) return;
        if ((Object) this != Minecraft.getInstance().player) return;
        Float yaw = ClientPlayerController.INSTANCE.viewYaw();
        if (yaw != null) cir.setReturnValue(yaw);
    }

    @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
    private void herobot$smoothViewPitch(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!HeroBotClient.isHeroBotLoaded()) return;
        if ((Object) this != Minecraft.getInstance().player) return;
        Float pitch = ClientPlayerController.INSTANCE.viewPitch();
        if (pitch != null) cir.setReturnValue(pitch);
    }
}
