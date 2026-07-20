package hero.bane.herobot.mixin.carpet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "carpet.commands.PlayerCommand", remap = false)
public class CarpetPlayerCommandMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true, remap = false)
    private static void herobot$cancel(CallbackInfo ci) {
        ci.cancel();
    }
}
