package hero.bane.herobot.mod.client.mixin;

import hero.bane.herobot.mod.client.HeroBotClient;
import hero.bane.herobot.mod.common.rule.ModRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Redirect(
            method = "precipitationAt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos ignoreMovingPistonForRain(Level level, Heightmap.Types type, BlockPos pos) {
        BlockPos top = level.getHeightmapPos(type, pos);

        if (type != Heightmap.Types.MOTION_BLOCKING) {
            return top;
        }

        if (top.getY() <= pos.getY()) {
            return top;
        }

        if (onlyRainPassThroughAbove(level, pos, top.getY())) {
            return pos;
        }

        return top;
    }

    @Unique
    private static boolean onlyRainPassThroughAbove(Level level, BlockPos pos, int topY) {
        BlockPos.MutableBlockPos cursor = pos.mutable();

        for (int y = pos.getY() + 1; y <= topY; y++) {
            cursor.set(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(cursor);

            if (isRainPassThroughOrMovingPiston(state)) {
                continue;
            }

            return false;
        }

        return true;
    }

    @Unique
    private static boolean isRainPassThroughOrMovingPiston(BlockState state) {
        if (ModRules.rainThroughMovingPiston && state.is(Blocks.MOVING_PISTON) && HeroBotClient.isHeroBotLoaded()) {
            return true;
        }

        return !Heightmap.Types.MOTION_BLOCKING.isOpaque().test(state);
    }
}
