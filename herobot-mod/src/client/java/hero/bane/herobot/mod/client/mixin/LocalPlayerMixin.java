package hero.bane.herobot.mod.client.mixin;

import hero.bane.herobot.mod.common.HeroBotSettings;
import hero.bane.herobot.mod.client.HeroBotClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @ModifyConstant(
            method = "aiStep",
            constant = @Constant(floatValue = 3.0F)
    )
    private float changeCreativeVerticalSpeed(float original) {
        return (float) (original * (HeroBotClient.isHeroBotLoaded() ? HeroBotSettings.creativeFlySpeed : 1.0));
    }
}
