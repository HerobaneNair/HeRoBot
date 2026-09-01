package hero.bane.herobot.paper.bot.pathing.placement.mcadapter;

import hero.bane.herobot.paper.bot.pathing.PathSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class BlockCache {
    private static final byte VALID = 1;
    private static final byte COLLISION_EMPTY = 2;
    private static final byte AVOIDED = 4;
    private static final byte WATER = 8;
    private static final byte BUBBLE = 16;
    private static final byte BUBBLE_DOWN = 32;

    private final Level level;
    private final PathSettings settings;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    private long[] keys;
    private byte[] flags;
    private int mask;
    private int size;
    private int growAt;

    public BlockCache(Level level, PathSettings settings) {
        this.level = level;
        this.settings = settings;
        allocate(1 << 14);
    }

    public PathSettings settings() {
        return settings;
    }

    public Level level() {
        return level;
    }

    public boolean isPassable(int x, int y, int z) {
        byte f = flags(x, y, z);
        return (f & COLLISION_EMPTY) != 0 && (f & AVOIDED) == 0;
    }

    public boolean canWalkOn(int x, int y, int z) {
        byte f = flags(x, y, z);
        return (f & COLLISION_EMPTY) == 0 && (f & AVOIDED) == 0;
    }

    public boolean isWalkable(int x, int y, int z) {
        return isPassable(x, y, z) && isPassable(x, y + 1, z) && canWalkOn(x, y - 1, z);
    }

    public boolean isWater(int x, int y, int z) {
        return (flags(x, y, z) & WATER) != 0;
    }

    public boolean canSwimThrough(int x, int y, int z) {
        byte f = flags(x, y, z);
        return (f & COLLISION_EMPTY) != 0 && (f & WATER) != 0;
    }

    public boolean isBubbleColumnUp(int x, int y, int z) {
        byte f = flags(x, y, z);
        return (f & BUBBLE) != 0 && (f & BUBBLE_DOWN) == 0;
    }

    public boolean isBubbleColumnDown(int x, int y, int z) {
        byte f = flags(x, y, z);
        return (f & BUBBLE) != 0 && (f & BUBBLE_DOWN) != 0;
    }

    public int getBubbleColumnHeight(int x, int y, int z, boolean goingUp) {
        int count = 0;
        int dy = goingUp ? 1 : -1;
        int cy = y;
        while ((flags(x, cy, z) & BUBBLE) != 0) {
            count++;
            cy += dy;
        }
        return count;
    }

    private byte flags(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        int idx = index(key);
        while (true) {
            byte f = flags[idx];
            if (f == 0) break;
            if (keys[idx] == key) return f;
            idx = (idx + 1) & mask;
        }

        byte computed = compute(x, y, z);
        keys[idx] = key;
        flags[idx] = computed;
        if (++size >= growAt) grow();
        return computed;
    }

    private byte compute(int x, int y, int z) {
        scratch.set(x, y, z);
        BlockState state = level.getBlockState(scratch);

        byte f = VALID;
        if (state.getCollisionShape(level, scratch).isEmpty()) f |= COLLISION_EMPTY;
        if (!settings.isNotAvoided(state.getBlock())) f |= AVOIDED;

        boolean bubble = state.is(Blocks.BUBBLE_COLUMN);
        FluidState fluid = state.getFluidState();
        if (bubble || fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) f |= WATER;
        if (bubble) {
            f |= BUBBLE;
            if (state.getValue(BubbleColumnBlock.DRAG_DOWN)) f |= BUBBLE_DOWN;
        }
        return f;
    }

    private int index(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & mask;
    }

    private void allocate(int capacity) {
        this.keys = new long[capacity];
        this.flags = new byte[capacity];
        this.mask = capacity - 1;
        this.size = 0;
        this.growAt = capacity - (capacity >> 2);
    }

    private void grow() {
        long[] oldKeys = keys;
        byte[] oldFlags = flags;
        allocate(oldKeys.length << 1);
        for (int i = 0; i < oldKeys.length; i++) {
            byte f = oldFlags[i];
            if (f == 0) continue;
            long key = oldKeys[i];
            int idx = index(key);
            while (flags[idx] != 0) idx = (idx + 1) & mask;
            keys[idx] = key;
            flags[idx] = f;
            size++;
        }
    }
}
