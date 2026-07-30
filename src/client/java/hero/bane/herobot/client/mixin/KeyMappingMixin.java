package hero.bane.herobot.client.mixin;

import hero.bane.herobot.client.record.MovementRecorder;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Inject(method = "setDown", at = @At("TAIL"))
    private void herobot$recordKeyState(boolean down, CallbackInfo ci) {
        KeyMapping self = (KeyMapping) (Object) this;
        MovementRecorder.INSTANCE.onKeyStateChanged(self, self.isDown());
    }
}
