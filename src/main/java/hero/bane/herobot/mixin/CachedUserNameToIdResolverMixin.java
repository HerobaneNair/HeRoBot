package hero.bane.herobot.mixin;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CachedUserNameToIdResolver.class)
public abstract class CachedUserNameToIdResolverMixin {

    @Unique
    private static MinecraftServer hero_bane$server;

    static {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            hero_bane$server = server;
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            hero_bane$server = null;
        });
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void hero_bane$disableSaveInSingleplayer(CallbackInfo ci) {
        if (hero_bane$server != null && hero_bane$server.isSingleplayer()) {
            ci.cancel();
        }
    }
}