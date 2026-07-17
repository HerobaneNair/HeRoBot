package hero.bane.herobot.mixin;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void herobot$readInvisible(ValueInput input, CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        self.setInvisible(input.getBooleanOr("Invisible", false));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void herobot$saveInvisible(ValueOutput output, CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        output.putBoolean("Invisible", self.isInvisible());
    }
}
