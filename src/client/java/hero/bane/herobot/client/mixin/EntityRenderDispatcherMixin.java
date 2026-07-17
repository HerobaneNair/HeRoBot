package hero.bane.herobot.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void herobot$hideTaggedMinecarts(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractMinecart cart
                && (cart.isInvisible() || cart.getTags().contains("invisible"))) {
            LocalPlayer viewer = Minecraft.getInstance().player;
            if (viewer == null || !viewer.isSpectator()) {
                cir.setReturnValue(false);
            }
        }
    }
}
