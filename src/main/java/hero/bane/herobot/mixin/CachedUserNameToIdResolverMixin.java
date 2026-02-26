package hero.bane.herobot.mixin;

import hero.bane.herobot.HeroBot;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CachedUserNameToIdResolver.class)
public abstract class CachedUserNameToIdResolverMixin {

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void disableSaveInSingleplayer(CallbackInfo ci) {
        if (HeroBot.currentServer != null) {
            if (HeroBot.currentServer.isSingleplayer()) { //Not an amazing fix but should work
                ci.cancel();
            }
        }
    }
}