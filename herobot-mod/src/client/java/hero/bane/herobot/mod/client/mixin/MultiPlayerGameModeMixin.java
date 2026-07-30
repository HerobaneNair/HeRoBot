package hero.bane.herobot.mod.client.mixin;

import hero.bane.herobot.mod.client.record.MovementRecorder;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void herobot$recordSlotClick(int containerId, int slotId, int button, ClickType clickType, Player player,
                                         CallbackInfo ci) {
        MovementRecorder.INSTANCE.onSlotClick(containerId, slotId, button, clickType);
    }
}
