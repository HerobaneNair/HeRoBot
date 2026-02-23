package hero.bane.herobot.mixin;

import hero.bane.herobot.HeRoBotSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {
    @Inject(method = "createFire", at = @At("HEAD"), cancellable = true)
    private void explodeWithoutFire(List<BlockPos> list, CallbackInfo ci) {
        if (HeRoBotSettings.explosionNoFire) ci.cancel();
    }

    @ModifyVariable(method = "explode", at = @At("STORE"), ordinal = 0)
    private List<BlockPos> filterExplodedStuff(List<BlockPos> list) {
        var mode = HeRoBotSettings.explosionNoBlockDamage;

        if (!mode.enabled()) {
            return list;
        }

        Iterator<BlockPos> i = list.iterator();
        ServerExplosion explosion = (ServerExplosion) (Object) this;

        while (i.hasNext()) {
            BlockPos pos = i.next();
            BlockState state = explosion.level().getBlockState(pos);

            boolean remove = switch (mode) {
                case TRUE -> !state.isAir() && state.getBlock() != Blocks.FIRE;
                case MOST -> state.isSolidRender()
                        && state.getBlock() != Blocks.GLOWSTONE
                        || state.isSignalSource();
                default -> false;
            };

            if (remove) {
                i.remove();
            }
        }

        return list;
    }
}