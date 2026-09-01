package hero.bane.herobot.mod.common.bot.pathing.placement;

import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class MovementHelper {

    private MovementHelper() {
    }

    public static boolean isPassable(Level level, int x, int y, int z, PathSettings settings) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) return false;
        return settings.isNotAvoided(state.getBlock());
    }

    public static boolean canWalkOn(Level level, int x, int y, int z, PathSettings settings) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.getCollisionShape(level, pos).isEmpty()) return false;
        return settings.isNotAvoided(state.getBlock());
    }

    public static boolean isWalkable(Level level, int x, int y, int z, PathSettings settings) {
        if (!isPassable(level, x, y, z, settings)) return false;
        if (!isPassable(level, x, y + 1, z, settings)) return false;
        return canWalkOn(level, x, y - 1, z, settings);
    }

    public static boolean isWater(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        FluidState fluid = state.getFluidState();
        return fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER) || state.is(Blocks.BUBBLE_COLUMN);
    }

    public static boolean isBubbleColumn(Level level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).is(Blocks.BUBBLE_COLUMN);
    }

    public static boolean isBubbleColumnUp(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return state.is(Blocks.BUBBLE_COLUMN) && !state.getValue(BubbleColumnBlock.DRAG_DOWN);
    }

    public static boolean isBubbleColumnDown(Level level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return state.is(Blocks.BUBBLE_COLUMN) && state.getValue(BubbleColumnBlock.DRAG_DOWN);
    }

    public static boolean canSwimThrough(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && isWater(level, x, y, z);
    }

    public static int getBubbleColumnHeight(Level level, int x, int y, int z, boolean goingUp) {
        int count = 0;
        int dy = goingUp ? 1 : -1;
        int cy = y;
        while (isBubbleColumn(level, x, cy, z)) {
            count++;
            cy += dy;
        }
        return count;
    }
}
