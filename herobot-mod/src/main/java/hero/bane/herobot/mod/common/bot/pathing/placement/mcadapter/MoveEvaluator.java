package hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter;

import hero.bane.herobot.mod.common.bot.pathing.placement.MovementHelper;
import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import net.minecraft.world.level.Level;

import static hero.bane.herobot.mod.common.bot.pathing.placement.MovementHelper.canSwimThrough;
import static hero.bane.herobot.mod.common.bot.pathing.placement.MovementHelper.canWalkOn;
import static hero.bane.herobot.mod.common.bot.pathing.placement.MovementHelper.isPassable;
import static hero.bane.herobot.mod.common.bot.pathing.placement.MovementHelper.isWater;

final class MoveEvaluator {
    static final double INF = Double.POSITIVE_INFINITY;

    static final double SQRT_2 = Math.sqrt(2);
    static final double JUMP_PENALTY = 0.5;
    static final double SPRINT_MULTIPLIER = 0.6;
    static final double FALL_COST_PER_BLOCK = 0.3;
    static final double BUBBLE_COST_PER_BLOCK = 0.1;

    private static final ThreadLocal<Memo> MEMO = ThreadLocal.withInitial(Memo::new);

    private MoveEvaluator() {}

    static double cachedTransitionCost(Level level, PathSettings settings,
                                       int px, int py, int pz, int cx, int cy, int cz,
                                       int maxJump, int maxFall) {
        Memo m = MEMO.get();
        if (m.level == level && m.settings == settings
                && m.px == px && m.py == py && m.pz == pz
                && m.cx == cx && m.cy == cy && m.cz == cz
                && m.maxJump == maxJump && m.maxFall == maxFall) {
            return m.cost;
        }

        double cost = transitionCost(level, settings, px, py, pz, cx, cy, cz, maxJump, maxFall);

        m.level = level;
        m.settings = settings;
        m.px = px;
        m.py = py;
        m.pz = pz;
        m.cx = cx;
        m.cy = cy;
        m.cz = cz;
        m.maxJump = maxJump;
        m.maxFall = maxFall;
        m.cost = cost;
        return cost;
    }

    static double transitionCost(Level level, PathSettings settings,
                                 int px, int py, int pz, int cx, int cy, int cz,
                                 int maxJump, int maxFall) {
        int dx = cx - px;
        int dy = cy - py;
        int dz = cz - pz;
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        int horiz = adx + adz;

        if (dx == 0 && dz == 0) {
            return verticalCost(level, settings, px, py, pz, cy, dy);
        }

        if (adx == 1 && adz == 1) {
            if (dy != 0) return INF;
            return diagonalCost(level, settings, px, py, pz, cx, cz);
        }

        boolean cardinal = (dx == 0) ^ (dz == 0);
        if (!cardinal) return INF;

        if (horiz == 1) {
            if (dy == 0) return traverseOrSwim(level, settings, px, py, pz, cx, cz);
            if (dy == 1) return ascendCost(level, settings, px, py, pz, cx, cz, maxJump);
            return descendCost(level, settings, px, py, pz, cx, cy, cz, maxFall);
        }

        if (horiz >= 2 && horiz <= 4 && (dy == 0 || dy == 1)) {
            return parkourCost(level, settings, px, py, pz, cx, cy, cz, dy, horiz);
        }

        return INF;
    }

    private static double min(double a, double b) {
        return a < b ? a : b;
    }

    private static double verticalCost(Level level, PathSettings settings, int px, int py, int pz, int cy, int dy) {
        if (dy == 0) return INF;
        double best = bubbleCost(level, settings, px, py, pz, cy, dy);
        if (dy == 1) {
            best = min(best, pillarCost(level, settings, px, py, pz));
            best = min(best, swimVerticalCost(level, settings, px, py, pz, 1));
        } else if (dy == -1) {
            best = min(best, downwardCost(level, settings, px, py, pz));
            best = min(best, swimVerticalCost(level, settings, px, py, pz, -1));
        }
        return best;
    }

    private static double pillarCost(Level level, PathSettings settings, int px, int py, int pz) {
        if (!isPassable(level, px, py + 2, pz, settings)) return INF;
        if (!canWalkOn(level, px, py, pz, settings)) return INF;
        return settings.getVerticalMoveCost() + JUMP_PENALTY;
    }

    private static double downwardCost(Level level, PathSettings settings, int px, int py, int pz) {
        if (!isPassable(level, px, py - 1, pz, settings)) return INF;
        if (!canWalkOn(level, px, py - 2, pz, settings)) return INF;
        return settings.getVerticalMoveCost();
    }

    private static double swimVerticalCost(Level level, PathSettings settings, int px, int py, int pz, int dy) {
        if (!isWater(level, px, py, pz)) return INF;
        int destY = py + dy;
        if (dy > 0) {
            if (!canSwimThrough(level, px, destY, pz) && !isPassable(level, px, destY, pz, settings)) return INF;
            if (!canSwimThrough(level, px, destY + 1, pz) && !isPassable(level, px, destY + 1, pz, settings)) return INF;
        } else {
            if (!canSwimThrough(level, px, destY, pz)) return INF;
        }
        return settings.getVerticalMoveCost() * settings.getSwimCostMultiplier();
    }

    private static double bubbleCost(Level level, PathSettings settings, int px, int py, int pz, int cy, int dy) {
        if (dy > 0) {
            if (!MovementHelper.isBubbleColumnUp(level, px, py, pz)) return INF;
            int height = MovementHelper.getBubbleColumnHeight(level, px, py, pz, true);
            int destY = py + height;
            if (destY != cy) return INF;
            if (canSwimThrough(level, px, destY, pz) || canWalkOn(level, px, destY - 1, pz, settings)) {
                return height * BUBBLE_COST_PER_BLOCK;
            }
            return INF;
        }
        if (!MovementHelper.isBubbleColumnDown(level, px, py, pz)) return INF;
        int depth = MovementHelper.getBubbleColumnHeight(level, px, py, pz, false);
        int destY = py - depth;
        if (destY != cy) return INF;
        if (canSwimThrough(level, px, destY, pz) || canWalkOn(level, px, destY - 1, pz, settings)) {
            return depth * BUBBLE_COST_PER_BLOCK;
        }
        return INF;
    }

    private static double traverseOrSwim(Level level, PathSettings settings, int px, int py, int pz, int cx, int cz) {
        return min(
                traverseCost(level, settings, py, cx, cz),
                swimHorizontalCost(level, settings, px, py, pz, cx, cz));
    }

    private static double traverseCost(Level level, PathSettings settings, int py, int destX, int destZ) {
        if (!isPassable(level, destX, py, destZ, settings)) return INF;
        if (!isPassable(level, destX, py + 1, destZ, settings)) return INF;
        if (!canWalkOn(level, destX, py - 1, destZ, settings)) return INF;
        double cost = settings.getHorizontalMoveCost();
        if (settings.getMoveType() != PathSettings.MoveType.WALK) cost *= SPRINT_MULTIPLIER;
        return cost;
    }

    private static double swimHorizontalCost(Level level, PathSettings settings, int px, int py, int pz, int destX, int destZ) {
        boolean srcWater = isWater(level, px, py, pz);
        boolean swimming = isWater(level, destX, py, destZ);
        if (!srcWater && !swimming) return INF;
        if (!canSwimThrough(level, destX, py, destZ) && !isPassable(level, destX, py, destZ, settings)) return INF;
        if (!canSwimThrough(level, destX, py + 1, destZ) && !isPassable(level, destX, py + 1, destZ, settings)) return INF;
        boolean landing = !swimming && canWalkOn(level, destX, py - 1, destZ, settings);
        if (!landing && !swimming) return INF;
        return swimming
                ? settings.getHorizontalMoveCost() * settings.getSwimCostMultiplier()
                : settings.getHorizontalMoveCost();
    }

    private static double ascendCost(Level level, PathSettings settings, int px, int py, int pz, int cx, int cz, int maxJump) {
        if (maxJump < 1) return INF;
        if (!isPassable(level, px, py + 2, pz, settings)) return INF;
        if (!isPassable(level, cx, py + 1, cz, settings)) return INF;
        if (!isPassable(level, cx, py + 2, cz, settings)) return INF;
        if (!canWalkOn(level, cx, py, cz, settings)) return INF;
        return settings.getHorizontalMoveCost() + settings.getVerticalMoveCost() + JUMP_PENALTY;
    }

    private static double descendCost(Level level, PathSettings settings, int px, int py, int pz, int cx, int cy, int cz, int maxFall) {
        if (!isPassable(level, cx, py, cz, settings)) return INF;
        if (!isPassable(level, cx, py + 1, cz, settings)) return INF;

        if (canWalkOn(level, cx, py - 2, cz, settings) && isPassable(level, cx, py - 1, cz, settings)) {
            return cy == py - 1
                    ? settings.getHorizontalMoveCost() + FALL_COST_PER_BLOCK
                    : INF;
        }

        if (!isPassable(level, cx, py - 1, cz, settings)) return INF;

        for (int drop = 2; drop <= maxFall; drop++) {
            int landY = py - drop;
            if (canWalkOn(level, cx, landY - 1, cz, settings)
                    && isPassable(level, cx, landY, cz, settings)
                    && isPassable(level, cx, landY + 1, cz, settings)) {
                return landY == cy
                        ? settings.getHorizontalMoveCost() + drop * FALL_COST_PER_BLOCK
                        : INF;
            }
            if (!isPassable(level, cx, landY, cz, settings)) return INF;
        }
        return INF;
    }

    private static double diagonalCost(Level level, PathSettings settings, int px, int py, int pz, int cx, int cz) {
        if (!isPassable(level, cx, py, cz, settings)) return INF;
        if (!isPassable(level, cx, py + 1, cz, settings)) return INF;
        if (!canWalkOn(level, cx, py - 1, cz, settings)) return INF;
        if (!isPassable(level, cx, py, pz, settings)) return INF;
        if (!isPassable(level, cx, py + 1, pz, settings)) return INF;
        if (!isPassable(level, px, py, cz, settings)) return INF;
        if (!isPassable(level, px, py + 1, cz, settings)) return INF;
        double cost = settings.getHorizontalMoveCost() * SQRT_2;
        if (settings.getMoveType() != PathSettings.MoveType.WALK) cost *= SPRINT_MULTIPLIER;
        return cost;
    }

    private static double parkourCost(Level level, PathSettings settings, int px, int py, int pz, int cx, int cy, int cz, int dy, int dist) {
        if (settings.getMoveType() == PathSettings.MoveType.WALK) return INF;

        int signX = Integer.compare(cx, px);
        int signZ = Integer.compare(cz, pz);

        if (!canWalkOn(level, px, py - 1, pz, settings)) return INF;

        int adjX = px + signX;
        int adjZ = pz + signZ;
        if (canWalkOn(level, adjX, py - 1, adjZ, settings)) return INF;
        if (!isPassable(level, adjX, py, adjZ, settings)) return INF;
        if (!isPassable(level, adjX, py + 1, adjZ, settings)) return INF;
        if (!isPassable(level, px, py + 2, pz, settings)) return INF;

        if (dy == 0) {
            int maxDist = settings.getMoveType() == PathSettings.MoveType.SPRINT_JUMP ? 4 : 3;
            if (dist > maxDist) return INF;
            for (int d = 2; d < dist; d++) {
                int midX = px + signX * d;
                int midZ = pz + signZ * d;
                if (!isPassable(level, midX, py, midZ, settings)) return INF;
                if (!isPassable(level, midX, py + 1, midZ, settings)) return INF;
                if (!isPassable(level, midX, py + 2, midZ, settings)) return INF;
            }
            if (!isPassable(level, cx, py + 2, cz, settings)) return INF;
            if (!isPassable(level, cx, py, cz, settings)) return INF;
            if (!isPassable(level, cx, py + 1, cz, settings)) return INF;
            if (!canWalkOn(level, cx, py - 1, cz, settings)) return INF;
            return settings.getHorizontalMoveCost() * SPRINT_MULTIPLIER + JUMP_PENALTY;
        }

        if (dist > 3) return INF;
        for (int d = 1; d < dist; d++) {
            int midX = px + signX * d;
            int midZ = pz + signZ * d;
            if (!isPassable(level, midX, py + 1, midZ, settings)) return INF;
            if (!isPassable(level, midX, py + 2, midZ, settings)) return INF;
        }
        if (!isPassable(level, cx, py + 1, cz, settings)) return INF;
        if (!isPassable(level, cx, py + 2, cz, settings)) return INF;
        if (!isPassable(level, cx, py + 3, cz, settings)) return INF;
        if (!canWalkOn(level, cx, py, cz, settings)) return INF;
        return settings.getHorizontalMoveCost() * SPRINT_MULTIPLIER + settings.getVerticalMoveCost() + JUMP_PENALTY;
    }

    private static final class Memo {
        Level level;
        PathSettings settings;
        int px = Integer.MIN_VALUE, py, pz, cx, cy, cz;
        int maxJump, maxFall;
        double cost = INF;
    }
}
