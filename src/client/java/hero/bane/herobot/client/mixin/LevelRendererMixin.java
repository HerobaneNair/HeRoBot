package hero.bane.herobot.client.mixin;

import hero.bane.herobot.HeroBotSettings;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin
{
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
    private boolean canSeeWorld(LocalPlayer clientPlayerEntity)
    {
        return clientPlayerEntity.isSpectator() || (HeroBotSettings.creativeNoClip && clientPlayerEntity.isCreative());
    }
}
