package hero.bane.herobot.mod.client.mixin;

import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.mod.client.HeroBotClient;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public class CameraMixin
{
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
    private boolean canSeeWorld(LocalPlayer clientPlayerEntity)
    {
        return clientPlayerEntity.isSpectator() || (HeroBotSettings.creativeNoClip && clientPlayerEntity.isCreative() && HeroBotClient.isHeroBotLoaded());
    }
}
