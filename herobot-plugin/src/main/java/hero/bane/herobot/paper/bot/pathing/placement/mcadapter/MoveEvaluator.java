package hero.bane.herobot.paper.bot.pathing.placement.mcadapter;

import hero.bane.herobot.paper.bot.pathing.PathSettings;

final class MoveEvaluator {
    static final double INF = Double.POSITIVE_INFINITY;

    static final double SQRT_2 = Math.sqrt(2);
    static final double JUMP_PENALTY = 0.5;
    static final double SPRINT_MULTIPLIER = 0.6;
    static final double FALL_COST_PER_BLOCK = 0.3;
    static final double BUBBLE_COST_PER_BLOCK = 0.1;

    private static final ThreadLocal<Memo> MEMO = ThreadLocal.withInitial(Memo::new);

    private MoveEvaluator() {}

    static double cachedTransitionCost(BlockCache blocks,
                                       int px, int py, int pz, int cx, int cy, int cz,
                                       int maxJump, int maxFall) {
        Memo m = MEMO.get();
        if (m.blocks == blocks
                && m.px == px && m.py == py && m.pz == pz
                && m.cx == cx && m.cy == cy && m.cz == cz
                && m.maxJump == maxJump && m.maxFall == maxFall) {
            return m.cost;
        }

        double cost = transitionCost(blocks, px, py, pz, cx, cy, cz, maxJump, maxFall);

        m.blocks = blocks;
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

    static double transitionCost(BlockCache blocks,
                                 int px, int py, int pz, int cx, int cy, int cz,
                                 int maxJump, int maxFall) {
        int dx = cx - px;
        int dy = cy - py;
        int dz = cz - pz;
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        int horiz = adx + adz;

        if (dx == 0 && dz == 0) {
            return verticalCost(blocks, px, py, pz, cy, dy);
        }

        if (adx == 1 && adz == 1) {
            if (dy != 0) return INF;
            return diagonalCost(blocks, px, py, pz, cx, cz);
        }

        boolean cardinal = (dx == 0) ^ (dz == 0);
        if (!cardinal) return INF;

        if (horiz == 1) {
            if (dy == 0) return traverseOrSwim(blocks, px, py, pz, cx, cz);
            if (dy == 1) return ascendCost(blocks, px, py, pz, cx, cz, maxJump);
            return descendCost(blocks, py, cx, cy, cz, maxFall);
        }

        if (horiz >= 2 && horiz <= 4 && (dy == 0 || dy == 1)) {
            return parkourCost(blocks, px, py, pz, cx, cz, dy, horiz);
        }

        return INF;
    }

    private static double verticalCost(BlockCache blocks, int px, int py, int pz, int cy, int dy) {
        if (dy == 0) return INF;
        double best = bubbleCost(blocks, px, py, pz, cy, dy);
        if (dy == 1) {
            best = Math.min(best, pillarCost(blocks, px, py, pz));
            best = Math.min(best, swimVerticalCost(blocks, px, py, pz, 1));
        } else if (dy == -1) {
            best = Math.min(best, downwardCost(blocks, px, py, pz));
            best = Math.min(best, swimVerticalCost(blocks, px, py, pz, -1));
        }
        return best;
    }

    private static double pillarCost(BlockCache blocks, int px, int py, int pz) {
        if (!blocks.isPassable(px, py + 2, pz)) return INF;
        if (!blocks.canWalkOn(px, py, pz)) return INF;
        return blocks.settings().getVerticalMoveCost() + JUMP_PENALTY;
    }

    private static double downwardCost(BlockCache blocks, int px, int py, int pz) {
        if (!blocks.isPassable(px, py - 1, pz)) return INF;
        if (!blocks.canWalkOn(px, py - 2, pz)) return INF;
        return blocks.settings().getVerticalMoveCost();
    }

    private static double swimVerticalCost(BlockCache blocks, int px, int py, int pz, int dy) {
        if (!blocks.isWater(px, py, pz)) return INF;
        PathSettings settings = blocks.settings();
        int destY = py + dy;
        if (dy > 0) {
            if (!blocks.canSwimThrough(px, destY, pz) && !blocks.isPassable(px, destY, pz)) return INF;
            if (!blocks.canSwimThrough(px, destY + 1, pz) && !blocks.isPassable(px, destY + 1, pz)) return INF;
        } else {
            if (!blocks.canSwimThrough(px, destY, pz)) return INF;
        }
        return settings.getVerticalMoveCost() * settings.getSwimCostMultiplier();
    }

    private static double bubbleCost(BlockCache blocks, int px, int py, int pz, int cy, int dy) {
        if (dy > 0) {
            if (!blocks.isBubbleColumnUp(px, py, pz)) return INF;
            int height = blocks.getBubbleColumnHeight(px, py, pz, true);
            int destY = py + height;
            if (destY != cy) return INF;
            if (blocks.canSwimThrough(px, destY, pz) || blocks.canWalkOn(px, destY - 1, pz)) {
                return height * BUBBLE_COST_PER_BLOCK;
            }
            return INF;
        }
        if (!blocks.isBubbleColumnDown(px, py, pz)) return INF;
        int depth = blocks.getBubbleColumnHeight(px, py, pz, false);
        int destY = py - depth;
        if (destY != cy) return INF;
        if (blocks.canSwimThrough(px, destY, pz) || blocks.canWalkOn(px, destY - 1, pz)) {
            return depth * BUBBLE_COST_PER_BLOCK;
        }
        return INF;
    }

    private static double traverseOrSwim(BlockCache blocks, int px, int py, int pz, int cx, int cz) {
        return Math.min(
                traverseCost(blocks, py, cx, cz),
                swimHorizontalCost(blocks, px, py, pz, cx, cz));
    }

    private static double traverseCost(BlockCache blocks, int py, int destX, int destZ) {
        if (!blocks.isPassable(destX, py, destZ)) return INF;
        if (!blocks.isPassable(destX, py + 1, destZ)) return INF;
        if (!blocks.canWalkOn(destX, py - 1, destZ)) return INF;
        PathSettings settings = blocks.settings();
        double cost = settings.getHorizontalMoveCost();
        if (settings.getMoveType() != PathSettings.MoveType.WALK) cost *= SPRINT_MULTIPLIER;
        return cost;
    }

    private static double swimHorizontalCost(BlockCache blocks, int px, int py, int pz, int destX, int destZ) {
        boolean srcWater = blocks.isWater(px, py, pz);
        boolean swimming = blocks.isWater(destX, py, destZ);
        if (!srcWater && !swimming) return INF;
        if (!blocks.canSwimThrough(destX, py, destZ) && !blocks.isPassable(destX, py, destZ)) return INF;
        if (!blocks.canSwimThrough(destX, py + 1, destZ) && !blocks.isPassable(destX, py + 1, destZ)) return INF;
        boolean landing = !swimming && blocks.canWalkOn(destX, py - 1, destZ);
        if (!landing && !swimming) return INF;
        PathSettings settings = blocks.settings();
        return swimming
                ? settings.getHorizontalMoveCost() * settings.getSwimCostMultiplier()
                : settings.getHorizontalMoveCost();
    }

    private static double ascendCost(BlockCache blocks, int px, int py, int pz, int cx, int cz, int maxJump) {
        if (maxJump < 1) return INF;
        if (!blocks.isPassable(px, py + 2, pz)) return INF;
        if (!blocks.isPassable(cx, py + 1, cz)) return INF;
        if (!blocks.isPassable(cx, py + 2, cz)) return INF;
        if (!blocks.canWalkOn(cx, py, cz)) return INF;
        PathSettings settings = blocks.settings();
        return settings.getHorizontalMoveCost() + settings.getVerticalMoveCost() + JUMP_PENALTY;
    }

    private static double descendCost(BlockCache blocks, int py, int cx, int cy, int cz, int maxFall) {
        if (!blocks.isPassable(cx, py, cz)) return INF;
        if (!blocks.isPassable(cx, py + 1, cz)) return INF;

        PathSettings settings = blocks.settings();

        if (blocks.canWalkOn(cx, py - 2, cz) && blocks.isPassable(cx, py - 1, cz)) {
            return cy == py - 1
                    ? settings.getHorizontalMoveCost() + FALL_COST_PER_BLOCK
                    : INF;
        }

        if (!blocks.isPassable(cx, py - 1, cz)) return INF;

        for (int drop = 2; drop <= maxFall; drop++) {
            int landY = py - drop;
            if (blocks.canWalkOn(cx, landY - 1, cz)
                    && blocks.isPassable(cx, landY, cz)
                    && blocks.isPassable(cx, landY + 1, cz)) {
                return landY == cy
                        ? settings.getHorizontalMoveCost() + drop * FALL_COST_PER_BLOCK
                        : INF;
            }
            if (!blocks.isPassable(cx, landY, cz)) return INF;
        }
        return INF;
    }

    private static double diagonalCost(BlockCache blocks, int px, int py, int pz, int cx, int cz) {
        if (!blocks.isPassable(cx, py, cz)) return INF;
        if (!blocks.isPassable(cx, py + 1, cz)) return INF;
        if (!blocks.canWalkOn(cx, py - 1, cz)) return INF;
        if (!blocks.isPassable(cx, py, pz)) return INF;
        if (!blocks.isPassable(cx, py + 1, pz)) return INF;
        if (!blocks.isPassable(px, py, cz)) return INF;
        if (!blocks.isPassable(px, py + 1, cz)) return INF;
        PathSettings settings = blocks.settings();
        double cost = settings.getHorizontalMoveCost() * SQRT_2;
        if (settings.getMoveType() != PathSettings.MoveType.WALK) cost *= SPRINT_MULTIPLIER;
        return cost;
    }

    private static double parkourCost(BlockCache blocks, int px, int py, int pz, int cx, int cz, int dy, int dist) {
        PathSettings settings = blocks.settings();
        if (settings.getMoveType() == PathSettings.MoveType.WALK) return INF;

        int signX = Integer.compare(cx, px);
        int signZ = Integer.compare(cz, pz);

        if (!blocks.canWalkOn(px, py - 1, pz)) return INF;

        int adjX = px + signX;
        int adjZ = pz + signZ;
        if (blocks.canWalkOn(adjX, py - 1, adjZ)) return INF;
        if (!blocks.isPassable(adjX, py, adjZ)) return INF;
        if (!blocks.isPassable(adjX, py + 1, adjZ)) return INF;
        if (!blocks.isPassable(px, py + 2, pz)) return INF;

        if (dy == 0) {
            int maxDist = settings.getMoveType() == PathSettings.MoveType.SPRINT_JUMP ? 4 : 3;
            if (dist > maxDist) return INF;
            for (int d = 2; d < dist; d++) {
                int midX = px + signX * d;
                int midZ = pz + signZ * d;
                if (!blocks.isPassable(midX, py, midZ)) return INF;
                if (!blocks.isPassable(midX, py + 1, midZ)) return INF;
                if (!blocks.isPassable(midX, py + 2, midZ)) return INF;
            }
            if (!blocks.isPassable(cx, py + 2, cz)) return INF;
            if (!blocks.isPassable(cx, py, cz)) return INF;
            if (!blocks.isPassable(cx, py + 1, cz)) return INF;
            if (!blocks.canWalkOn(cx, py - 1, cz)) return INF;
            return settings.getHorizontalMoveCost() * SPRINT_MULTIPLIER + JUMP_PENALTY;
        }

        if (dist > 3) return INF;
        for (int d = 1; d < dist; d++) {
            int midX = px + signX * d;
            int midZ = pz + signZ * d;
            if (!blocks.isPassable(midX, py + 1, midZ)) return INF;
            if (!blocks.isPassable(midX, py + 2, midZ)) return INF;
        }
        if (!blocks.isPassable(cx, py + 1, cz)) return INF;
        if (!blocks.isPassable(cx, py + 2, cz)) return INF;
        if (!blocks.isPassable(cx, py + 3, cz)) return INF;
        if (!blocks.canWalkOn(cx, py, cz)) return INF;
        return settings.getHorizontalMoveCost() * SPRINT_MULTIPLIER + settings.getVerticalMoveCost() + JUMP_PENALTY;
    }

    private static final class Memo {
        BlockCache blocks;
        int px = Integer.MIN_VALUE, py, pz, cx, cy, cz;
        int maxJump, maxFall;
        double cost = INF;
    }
}
