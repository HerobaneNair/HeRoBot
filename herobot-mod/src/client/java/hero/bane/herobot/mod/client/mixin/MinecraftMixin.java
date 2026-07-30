package hero.bane.herobot.mod.client.mixin;

import hero.bane.herobot.mod.common.HeroBotSettings;
import hero.bane.herobot.mod.client.HeroBotClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin
{
    @Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
    private void onGetTickTargetMillis(final float f, final CallbackInfoReturnable<Float> cir)
    {
        if (HeroBotSettings.clientsIgnoreSlowTickRate && HeroBotClient.isHeroBotLoaded()) {
            cir.setReturnValue(f);
        }
    }
}
