package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {
    @Inject(method = "createFire", at = @At("HEAD"), cancellable = true)
    private void explodeWithoutFire(List<BlockPos> list, CallbackInfo ci) {
        if (HeroBotSettings.explosionNoFire) ci.cancel();
    }

    @ModifyVariable(method = "explode", at = @At("STORE"), ordinal = 0)
    private List<BlockPos> filterExplodedStuff(List<BlockPos> list) {
        var mode = HeroBotSettings.explosionNoBlockDamage;

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
                case MOST -> removeOnMost(state, explosion, pos);
                default -> false;
            };

            if (remove) {
                i.remove();
            }
        }

        return list;
    }

    @Redirect(method = "hurtEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(Lnet/minecraft/world/phys/Vec3;)V"))
    private void explosionKBPing(Entity entity, Vec3 vec3) {
        if (entity instanceof BotPlayer botPlayer) {
            botPlayer.delayedExplosionKB(vec3);
        } else {
            entity.push(vec3);
        }
    }

    @Unique
    private static boolean removeOnMost(BlockState state, ServerExplosion explosion, BlockPos pos) {
        if (state.isSignalSource()) return true;
        if (state.getBlock() == Blocks.GLOWSTONE || state.getBlock() == Blocks.COBWEB) return false;
        return !state.getCollisionShape(explosion.level(), pos).isEmpty();
    }
}
