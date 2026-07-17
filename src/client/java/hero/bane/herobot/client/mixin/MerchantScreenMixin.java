package hero.bane.herobot.client.mixin;

import hero.bane.herobot.client.record.MovementRecorder;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {
    @Shadow
    private int shopItem;

    @Inject(method = "postButtonClick", at = @At("HEAD"))
    private void herobot$recordTradeButton(CallbackInfo ci) {
        MovementRecorder.INSTANCE.onTradeButton(this.shopItem);
    }
}
