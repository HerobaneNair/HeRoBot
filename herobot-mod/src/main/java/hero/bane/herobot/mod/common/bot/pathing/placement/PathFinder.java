package hero.bane.herobot.mod.common.bot.pathing.placement;

import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import hero.bane.herobot.mod.common.bot.pathing.PathStats;
import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.result.PathState;
import de.bsommerfeld.pathetic.api.pathing.result.PathfinderResult;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter.CostProcessor;
import hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter.EnvironmentContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter.LevelNavigationPointProvider;
import hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter.McHeuristicStrategy;
import hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter.NeighborStrategy;
import hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter.WalkabilityValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class PathFinder {
    private static final int NO_FALL = 256;
    private static final AStarPathfinderFactory FACTORY = new AStarPathfinderFactory();

    private static int getMaxFallDistance(Player player) {
        if (player instanceof ServerPlayer sp && sp.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            return NO_FALL;
        }
        return (int) player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
    }

    private static int getMaxJumpHeight(Player player) {
        int level = 0;
        if (player.hasEffect(MobEffects.JUMP_BOOST)) {
            level = Objects.requireNonNull(player.getEffect(MobEffects.JUMP_BOOST)).getAmplifier() + 1;
        }
        double velocity = 0.42 + level * 0.1;
        return (int) (velocity * velocity / 0.16);
    }

    public static BlockPos floorStart(Level world, BlockPos feet, PathSettings settings) {
        if (MovementHelper.isWater(world, feet.getX(), feet.getY(), feet.getZ())) return feet;
        for (int dy = 0; dy <= 64; dy++) {
            BlockPos pos = feet.below(dy);
            if (MovementHelper.canWalkOn(world, pos.getX(), pos.getY() - 1, pos.getZ(), settings)) return pos;
        }
        return feet;
    }

    public static List<BlockPos> findPath(Level world, BlockPos start, Vec3 target, PathSettings settings, Player player) {
        return findPath(world, start, target, settings, player, 50000, null);
    }

    public static List<BlockPos> findPath(Level world, BlockPos start, Vec3 target, PathSettings settings, Player player, int maxIterations) {
        return findPath(world, start, target, settings, player, maxIterations, null);
    }

    public static CompletableFuture<List<BlockPos>> findPathAsync(Level world, BlockPos start, Vec3 target, PathSettings settings, Player player, int maxIterations, PathStats stats) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return findPath(world, start, target, settings, player, maxIterations, stats);
            } catch (Throwable t) {
                return null;
            }
        }, PathingExecutor.get());
    }

    public static List<BlockPos> findPath(Level world, BlockPos start, Vec3 target, PathSettings settings, Player player, int maxIterations, PathStats stats) {
        long startNs = System.nanoTime();
        if (stats != null) stats.reset();

        int maxJump = getMaxJumpHeight(player);
        int maxFall = getMaxFallDistance(player);

        double waterEfficiency = player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
        boolean dolphinsGrace = player.hasEffect(MobEffects.DOLPHINS_GRACE);
        settings.calculateSwimCost(waterEfficiency, dolphinsGrace);

        EnvironmentContext env = new EnvironmentContext(world, settings, player, maxJump, maxFall);

        PathfinderConfiguration config = PathfinderConfiguration.builder()
                .provider(LevelNavigationPointProvider.INSTANCE)
                .neighborStrategy(NeighborStrategy.INSTANCE)
                .validationProcessors(List.of(WalkabilityValidator.INSTANCE))
                .costProcessor(List.of(CostProcessor.INSTANCE))
                .heuristicStrategy(McHeuristicStrategy.INSTANCE)
                .maxIterations(maxIterations)
                .async(false)
                .fallback(true)
                .build();

        Pathfinder pathfinder = FACTORY.createPathfinder(config);

        PathPosition startPos = new PathPosition(start.getX(), start.getY(), start.getZ());
        PathPosition targetPos = new PathPosition(target.x, target.y, target.z);

        PathfinderResult result = pathfinder.findPath(startPos, targetPos, env).resultBlocking();

        List<BlockPos> raw = new ArrayList<>();
        for (PathPosition p : result.getPath()) {
            raw.add(new BlockPos(p.getFlooredX(), p.getFlooredY(), p.getFlooredZ()));
        }

        int goalIdx = -1;
        for (int i = 0; i < raw.size(); i++) {
            if (isWithinGoal(raw.get(i), target, settings)) {
                goalIdx = i;
                break;
            }
        }
        List<BlockPos> nodes = goalIdx >= 0 ? new ArrayList<>(raw.subList(0, goalIdx + 1)) : raw;

        List<BlockPos> path = nodes.isEmpty() ? null : smoothPath(nodes, world, settings);

        if (stats != null) {
            stats.elapsedNs = System.nanoTime() - startNs;
            stats.hitIterationCap = result.getPathState() == PathState.MAX_ITERATIONS_REACHED;
            stats.pathLength = path == null ? 0 : path.size();
            stats.success = goalIdx >= 0;
        }

        return path;
    }

    private static boolean isWithinGoal(BlockPos pos, Vec3 target, PathSettings settings) {
        double hDist = closestHDistToBlock(pos, target);
        double vDist = Math.abs(pos.getY() - target.y);
        return settings.isWithinTarget(hDist, vDist);
    }

    public static double closestHDistToBlock(BlockPos pos, Vec3 target) {
        double closestX = Math.clamp(target.x, pos.getX(), pos.getX() + 1.0);
        double closestZ = Math.clamp(target.z, pos.getZ(), pos.getZ() + 1.0);
        double dx = closestX - target.x;
        double dz = closestZ - target.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<BlockPos> smoothPath(List<BlockPos> path, Level level, PathSettings settings) {
        if (path.size() <= 2) return path;

        List<BlockPos> smoothed = new ArrayList<>();
        smoothed.add(path.getFirst());

        int current = 0;
        while (current < path.size() - 1) {
            if (isParkourSegment(path, current, level, settings)) {
                smoothed.add(path.get(current + 1));
                current = current + 1;
                continue;
            }

            int farthest = current + 1;
            for (int i = path.size() - 1; i > current + 1; i--) {
                if (containsParkourSegment(path, current + 1, i, level, settings)) continue;

                boolean bothWater = MovementHelper.isWater(level, path.get(current).getX(), path.get(current).getY(), path.get(current).getZ())
                        && MovementHelper.isWater(level, path.get(i).getX(), path.get(i).getY(), path.get(i).getZ());

                if (bothWater) {
                    if (hasWaterLineOfSight(level, path.get(current), path.get(i))) {
                        farthest = i;
                        break;
                    }
                } else {
                    if (path.get(i).getY() != path.get(current).getY()) continue;
                    if (hasLineOfSight(level, path.get(current), path.get(i), settings)) {
                        farthest = i;
                        break;
                    }
                }
            }
            smoothed.add(path.get(farthest));
            current = farthest;
        }

        return smoothed;
    }

    private static boolean isParkourSegment(List<BlockPos> path, int index, Level level, PathSettings settings) {
        if (index + 1 >= path.size()) return false;
        BlockPos from = path.get(index);
        BlockPos to = path.get(index + 1);
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        int dist = dx + dz;
        if (dist < 2 || dist > 4) return false;
        if (dx != 0 && dz != 0) return false;
        int stepX = Integer.compare(to.getX() - from.getX(), 0);
        int stepZ = Integer.compare(to.getZ() - from.getZ(), 0);
        return !MovementHelper.canWalkOn(level, from.getX() + stepX, from.getY() - 1, from.getZ() + stepZ, settings);
    }

    private static boolean containsParkourSegment(List<BlockPos> path, int startIdx, int endIdx, Level level, PathSettings settings) {
        for (int i = startIdx; i < endIdx; i++) {
            if (isParkourSegment(path, i, level, settings)) return true;
        }
        return false;
    }

    private static boolean hasLineOfSight(Level level, BlockPos from, BlockPos to, PathSettings settings) {
        int y = from.getY();
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int sx = Integer.compare(dx, 0);
        int sz = Integer.compare(dz, 0);
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);

        int x = from.getX();
        int z = from.getZ();
        int error = adx - adz;

        while (x != to.getX() || z != to.getZ()) {
            int e2 = error * 2;
            if (e2 > -adz) {
                error -= adz;
                x += sx;
            }
            if (e2 < adx) {
                error += adx;
                z += sz;
            }
            if (!MovementHelper.isWalkable(level, x, y, z, settings)) return false;
        }

        return true;
    }

    private static boolean hasWaterLineOfSight(Level level, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) return true;

        for (int step = 1; step <= steps; step++) {
            int x = from.getX() + dx * step / steps;
            int y = from.getY() + dy * step / steps;
            int z = from.getZ() + dz * step / steps;
            if (!MovementHelper.canSwimThrough(level, x, y, z)) return false;
        }
        return true;
    }
}
