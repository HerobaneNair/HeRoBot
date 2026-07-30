package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.mod.common.HeroBotSettings;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlocksAttacks.class)
public class BlocksAttacksMixin {

    @Inject(method = "blockDelayTicks", at = @At("RETURN"), cancellable = true)
    private void overrideBlockDelay(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(HeroBotSettings.shieldDelayTicks);
    }
}
