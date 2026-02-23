package hero.bane.herobot.client.mixin;

import hero.bane.herobot.HeRoBotSettings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class Minecraft_tickMixin
{
    @Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
    private void onGetTickTargetMillis(final float f, final CallbackInfoReturnable<Float> cir)
    {
        if (!HeRoBotSettings.smoothClientAnimations) {
            cir.setReturnValue(f);
        }
    }
}
