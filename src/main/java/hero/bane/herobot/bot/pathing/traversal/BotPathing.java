package hero.bane.herobot.bot.pathing.traversal;

import hero.bane.herobot.bot.BotPlayer;
import hero.bane.herobot.bot.BotPlayerActionPack;
import hero.bane.herobot.bot.connection.ServerPlayerInterface;
import hero.bane.herobot.bot.pathing.DebugChannel;
import hero.bane.herobot.bot.pathing.placement.MovementHelper;
import hero.bane.herobot.bot.pathing.placement.PathFinder;
import hero.bane.herobot.bot.pathing.PathSettings;
import hero.bane.herobot.bot.pathing.PathStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BotPathing {
    private final BotPlayer bot;
    private final BotPlayerActionPack actionPack;
    private final CommandSourceStack source;
    private final PathSettings settings;

    private List<BlockPos> path;
    private Vec3 target;
    private int currentIndex;
    private int stuckTime;
    private Vec3 lastPos;
    private boolean done;
    private boolean retrying;
    private BlockPos retryTarget;
    private int retryNextIndex;
    private double originalNodeDistance;
    private boolean pendingRecalc;

    private final Entity targetEntity;
    private int recalcCd;
    private Vec3 lastRecalcTarget;
    private BlockPos progressNode;
    private double closestNodeDist;
    private int noProgressTicks;
    private int jumpInputHoldTicks;

    private enum PendingKind { INITIAL, FULL, SPLICE }

    private CompletableFuture<List<BlockPos>> pendingPath;
    private PendingKind pendingKind;
    private int pendingSpliceIdx;
    private List<BlockPos> pendingSplicePath;
    private Vec3 pendingRequestPos;
    private Vec3 pendingTarget;
    private PathStats pendingStats;
    private int failedRecalcs;
    private int recalcBackoffTicks;
    private boolean spliceFailedLast;
    private boolean awaitingInitialPath;

    private Set<BlockPos> debugger;

    private final PathStats lastPathStats = new PathStats();

    public BotPathing(BotPlayer bot, Vec3 target,
                      CommandSourceStack source, PathSettings settings) {
        this.bot = bot;
        this.actionPack = ((ServerPlayerInterface) bot).getActionPack();
        this.path = List.of();
        this.target = target;
        this.source = source;
        this.settings = settings;
        this.targetEntity = null;
        this.currentIndex = 0;
        this.stuckTime = 0;
        this.lastPos = bot.position();
        this.lastRecalcTarget = target;
        requestInitialPath();
    }

    public BotPathing(BotPlayer bot, Entity targetEntity,
                      CommandSourceStack source, PathSettings settings) {
        this.bot = bot;
        this.actionPack = ((ServerPlayerInterface) bot).getActionPack();
        this.targetEntity = targetEntity;
        this.target = computeEntityTarget(targetEntity, settings, bot);
        this.source = source;
        this.settings = settings;
        this.currentIndex = 0;
        this.stuckTime = 0;
        this.lastPos = bot.position();
        this.lastRecalcTarget = target;
        this.path = List.of();
        requestInitialPath();
    }

    private void requestInitialPath() {
        awaitingInitialPath = true;
        pendingKind = PendingKind.INITIAL;
        pendingTarget = target;
        pendingStats = new PathStats();
        pendingPath = PathFinder.findPathAsync(bot.level(),
                PathFinder.floorStart(bot.level(), bot.blockPosition(), settings),
                target, settings, bot, 50000, pendingStats);
    }

    public void recalcPath() {
        if (done) return;
        Vec3 currentTarget = targetEntity != null ? targetEntity.position() : target;
        if (spliceFailedLast) requestFullRecalc();
        else requestSpliceRecalc(currentTarget);
    }

    private boolean canRequestPath() {
        return !done && pendingPath == null && recalcBackoffTicks <= 0;
    }

    private void requestFullRecalc() {
        if (!canRequestPath()) return;
        debugRecalc();
        Vec3 currentTarget = targetEntity != null ? targetEntity.position() : target;
        pendingKind = PendingKind.FULL;
        pendingRequestPos = bot.position();
        pendingTarget = currentTarget;
        pendingStats = new PathStats();
        pendingPath = PathFinder.findPathAsync(bot.level(), bot.blockPosition(), currentTarget, settings, bot, 50000, pendingStats);
    }

    private void requestSpliceRecalc(Vec3 newTarget) {
        if (!canRequestPath()) return;
        if (path == null || path.isEmpty() || currentIndex >= path.size()) {
            requestFullRecalc();
            return;
        }

        int nearest = currentIndex;
        double best = Double.MAX_VALUE;
        for (int i = currentIndex; i < path.size(); i++) {
            double d = Vec3.atBottomCenterOf(path.get(i)).distanceToSqr(newTarget);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }

        int spliceIdx = Math.max(currentIndex, nearest - 1);
        BlockPos spliceStart = path.get(spliceIdx);
        debugRecalc();
        pendingKind = PendingKind.SPLICE;
        pendingSpliceIdx = spliceIdx;
        pendingSplicePath = path;
        pendingTarget = newTarget;
        pendingStats = new PathStats();
        pendingPath = PathFinder.findPathAsync(bot.level(), spliceStart, newTarget, settings, bot, 50000, pendingStats);
    }

    private void pollPendingPath() {
        if (pendingPath == null || !pendingPath.isDone()) return;
        List<BlockPos> result;
        try {
            result = pendingPath.join();
        } catch (RuntimeException e) {
            result = null;
        }
        PendingKind kind = pendingKind;
        List<BlockPos> splicePath = pendingSplicePath;
        pendingPath = null;
        pendingKind = null;
        pendingSplicePath = null;

        if (result == null || result.isEmpty()) {
            failedRecalcs++;
            recalcBackoffTicks = Math.min(160, 10 << Math.min(failedRecalcs - 1, 4));
            if (kind == PendingKind.SPLICE) spliceFailedLast = true;
            if (kind == PendingKind.INITIAL) {
                awaitingInitialPath = false;
                finish(false);
                source.sendFailure(Component.literal(bot.getGameProfile().name() + " could not find a path"));
            }
            return;
        }

        failedRecalcs = 0;
        spliceFailedLast = false;
        lastPathStats.copyFrom(pendingStats);

        switch (kind) {
            case INITIAL -> {
                awaitingInitialPath = false;
                applyNewPath(result);
            }
            case FULL -> {
                if (bot.position().distanceTo(pendingRequestPos) > 3.0) return;
                applyNewPath(result);
                lastRecalcTarget = pendingTarget;
            }
            case SPLICE -> {
                if (path != splicePath) return;
                if (currentIndex > pendingSpliceIdx || pendingSpliceIdx >= path.size()) return;
                BlockPos spliceStart = path.get(pendingSpliceIdx);
                List<BlockPos> merged = new ArrayList<>(path.subList(0, pendingSpliceIdx));
                if (!result.getFirst().equals(spliceStart)) merged.add(spliceStart);
                merged.addAll(result);
                this.path = merged;
                this.stuckTime = 0;
                this.lastRecalcTarget = pendingTarget;
                initDebugNodes(merged);
            }
        }
    }

    private void applyNewPath(List<BlockPos> newPath) {
        this.path = newPath;
        this.currentIndex = 0;
        this.stuckTime = 0;
        this.jumpInputHoldTicks = 0;
        initDebugNodes(newPath);
    }

    public void requestRecalc() {
        if (!done) {
            pendingRecalc = true;
        }
    }

    public void tick() {
        if (done) return;

        if (recalcBackoffTicks > 0) recalcBackoffTicks--;
        pollPendingPath();
        if (done) return;

        if (pendingRecalc && bot.onGround()) {
            pendingRecalc = false;
            recalcPath();
        }

        Vec3 botPos = bot.position();

        if (!updateTarget(botPos)) return;
        if (awaitingInitialPath) return;
        advanceWaypoints(botPos);
        moveTowardGoal(botPos);
        tickDebugParticles();
        tickNodeProgress(botPos);
        tickStuck(botPos);
    }

    private void tickNodeProgress(Vec3 botPos) {
        if (currentIndex >= path.size()) {
            progressNode = null;
            return;
        }
        BlockPos wp = path.get(currentIndex);
        double dist = botPos.distanceTo(Vec3.atBottomCenterOf(wp));
        if (!wp.equals(progressNode)) {
            progressNode = wp;
            closestNodeDist = dist;
            noProgressTicks = 0;
            return;
        }
        if (dist < closestNodeDist - 1.0E-4) {
            closestNodeDist = dist;
            noProgressTicks = 0;
            return;
        }
        if (++noProgressTicks >= 10) {
            if (targetEntity != null && recalcCd > 0) return;
            progressNode = null;
            requestFullRecalc();
            if (targetEntity != null) recalcCd = 10;
        }
    }

    private boolean updateTarget(Vec3 botPos) {
        if (targetEntity != null) {
            if (targetEntity.isRemoved()) {
                finish(false);
                source.sendFailure(Component.literal(bot.getGameProfile().name() + " lost target entity"));
                return false;
            }
            target = computeEntityTarget(targetEntity, settings, bot);
        }

        if (isWithinTarget(botPos)) {
            if (targetEntity != null && !settings.isStopFollowing()) {
                actionPack.setForward(0);
                actionPack.setStrafing(0);
                actionPack.setSprinting(false);
                Vec3 eyePos = targetEntity.getEyePosition();
                actionPack.lookAt(eyePos);
                setVerticalLook(eyePos);
                return false;
            }
            finish(true);
            String msg = bot.getGameProfile().name() + " reached target"
                    + (targetEntity != null ? " entity" : " position");
            source.sendSuccess(() -> Component.literal(msg), false);
            return false;
        }

        if (targetEntity != null) {
            tickEntityRecalc();
        } else if (currentIndex >= path.size()) {
            tryRecalcPath();
        }

        return true;
    }

    private void tickEntityRecalc() {
        recalcCd--;
        if (recalcCd <= 0) {
            tryRecalcPath();
            recalcCd = 10;
        }
    }

    private void tryRecalcPath() {
        if (lastRecalcTarget != null && lastRecalcTarget.distanceTo(target) <= 2.0) {
            return;
        }
        if (spliceFailedLast) requestFullRecalc();
        else requestSpliceRecalc(target);
    }

    private boolean hasOvershotNode(Vec3 botPos, double hDist, double vDist) {
        if (currentIndex <= 0 || currentIndex >= path.size()) return false;
        if (hDist > 1.5 || vDist > 1.5) return false;
        BlockPos prev = path.get(currentIndex - 1);
        BlockPos cur = path.get(currentIndex);
        int segX = cur.getX() - prev.getX();
        int segZ = cur.getZ() - prev.getZ();
        if (segX == 0 && segZ == 0) return false;
        double relX = botPos.x - (cur.getX() + 0.5);
        double relZ = botPos.z - (cur.getZ() + 0.5);
        return relX * segX + relZ * segZ > 0.0;
    }

    private void advanceWaypoints(Vec3 botPos) {
        while (currentIndex < path.size()) {
            BlockPos wp = path.get(currentIndex);
            double hDist = PathFinder.closestHDistToBlock(wp, botPos);
            double vDist = Math.abs(botPos.y - wp.getY());
            if (settings.isWithinNode(hDist, vDist) || hasOvershotNode(botPos, hDist, vDist)) {
                spawnDebugReached(wp);
                currentIndex++;
            } else {
                break;
            }
        }

        if (currentIndex < path.size()) {
            for (int i = Math.min(path.size() - 1, currentIndex + 3); i > currentIndex; i--) {
                BlockPos futureWp = path.get(i);
                double hDist = PathFinder.closestHDistToBlock(futureWp, botPos);
                double vDist = Math.abs(botPos.y - futureWp.getY());
                if (settings.isWithinNode(hDist, vDist)) {
                    for (int j = currentIndex; j <= i; j++) {
                        spawnDebugReached(path.get(j));
                    }
                    currentIndex = i + 1;
                    break;
                }
            }
        }

        if (currentIndex < path.size()) {
            for (int i = currentIndex + 1; i < path.size() && (i - currentIndex) <= 2; i++) {
                BlockPos candidate = path.get(i);
                if (candidate.getY() < botPos.y - 0.5) {
                    double hDist = PathFinder.closestHDistToBlock(candidate, botPos);
                    if (hDist < 1.5) {
                        for (int j = currentIndex; j < i; j++) {
                            spawnDebugReached(path.get(j));
                        }
                        currentIndex = i;
                    }
                    break;
                }
            }
        }
    }

    private void moveTowardGoal(Vec3 botPos) {
        if (currentIndex >= path.size()) {
            finalApproach(botPos);
            return;
        }

        BlockPos waypoint = path.get(currentIndex);

        if (isFullySubmerged(bot)) {
            swimToWaypoint(botPos, waypoint);
        } else if (isWading(bot)) {
            wadeToWaypoint(waypoint);
        } else {
            walkToWaypoint(botPos, waypoint);
        }
    }

    private void swimToWaypoint(Vec3 botPos, BlockPos waypoint) {
        Vec3 waypointMid = Vec3.atCenterOf(waypoint);
        actionPack.lookAt(waypointMid);
        setVerticalLook(waypointMid);
        actionPack.setForward(1);
        actionPack.setStrafing(0);
        actionPack.setSprinting(true);

        double dy = waypointMid.y - botPos.y;
        double hDist = Math.sqrt(horizontalDistanceSq(botPos, waypointMid));
        boolean moreVertical = Math.abs(dy) > hDist;

        if (dy > 0.5) {
            bot.setJumping(true);
            if (moreVertical) actionPack.setSneaking(false);
        } else if (dy < -0.5) {
            bot.setJumping(false);
            if (moreVertical) actionPack.setSneaking(true);
        } else {
            bot.setJumping(false);
            actionPack.setSneaking(false);
        }
    }

    private void wadeToWaypoint(BlockPos waypoint) {
        Vec3 waypointCenter = Vec3.atBottomCenterOf(waypoint);
        actionPack.setSneaking(false);
        bot.setJumping(false);
        actionPack.setForward(1);
        actionPack.setStrafing(0);
        actionPack.setSprinting(false);
        actionPack.lookAt(waypointCenter);
        setVerticalLook(waypointCenter);
    }

    private void walkToWaypoint(Vec3 botPos, BlockPos waypoint) {
        Vec3 waypointCenter = Vec3.atBottomCenterOf(waypoint);
        actionPack.setSneaking(false);
        bot.setJumping(false);

        if (jumpInputHoldTicks > 0) {
            jumpInputHoldTicks--;
            actionPack.setForward(0);
            actionPack.setStrafing(0);
            actionPack.setSprinting(false);
            actionPack.lookAt(waypointCenter);
            setVerticalLook(waypointCenter);
            return;
        }

        boolean ascending = waypoint.getY() > botPos.y + 0.5;

        Vec3 lookHorizontal;
        if (ascending) {
            lookHorizontal = waypointCenter;
        } else if (currentIndex + 1 < path.size()) {
            lookHorizontal = cornerAim(waypoint, path.get(currentIndex + 1));
        } else {
            lookHorizontal = targetEntity != null ? targetEntity.position() : target;
        }

        Vec3 verticalTarget = targetEntity != null ? targetEntity.getEyePosition() : waypointCenter;
        Vec3 lookPoint = new Vec3(lookHorizontal.x, verticalTarget.y, lookHorizontal.z);
        actionPack.lookAt(lookPoint);
        setVerticalLook(verticalTarget);
        debugLook(lookPoint);

        applyMoveType(false, botPos, waypoint);

        double dx = waypointCenter.x - botPos.x;
        double dz = waypointCenter.z - botPos.z;
        double toWaypointAngle = Math.atan2(-dx, dz);
        double facingAngle = Math.toRadians(bot.getYRot());
        double relativeAngle = toWaypointAngle - facingAngle;
        relativeAngle = Math.atan2(Math.sin(relativeAngle), Math.cos(relativeAngle));

        float forward = (float) Math.cos(relativeAngle);
        float strafe = (float) -Math.sin(relativeAngle);
        actionPack.setForward(forward > 0 ? forward : 0);
        actionPack.setStrafing(strafe);

        if (bot.onGround()) {
            if (isParkourJump(botPos, waypoint)) {
                if (!canJumpTowards(botPos, waypointCenter)) {
                    holdJumpInputs();
                    return;
                }
                actionPack.setSprinting(true);
                bot.jumpFromGround();
                debugJump();
            } else if (ascending) {
                if (horizontalDistanceSq(botPos, waypointCenter) <= 1.3 * 1.3) {
                    if (!canJumpTowards(botPos, waypointCenter)) {
                        holdJumpInputs();
                        return;
                    }
                    bot.jumpFromGround();
                    debugJump();
                }
            } else if (waypoint.getY() < botPos.y - 0.5
                    && settings.getMoveType() == PathSettings.MoveType.SPRINT_JUMP) {
                bot.jumpFromGround();
                debugJump();
            }
        }
    }

    private Vec3 cornerAim(BlockPos cur, BlockPos next) {
        Vec3 aim = Vec3.atBottomCenterOf(next);
        if (currentIndex + 2 >= path.size()) return aim;
        BlockPos next2 = path.get(currentIndex + 2);

        int inX = Integer.compare(next.getX() - cur.getX(), 0);
        int inZ = Integer.compare(next.getZ() - cur.getZ(), 0);
        int outX = Integer.compare(next2.getX() - next.getX(), 0);
        int outZ = Integer.compare(next2.getZ() - next.getZ(), 0);
        if (inX == outX && inZ == outZ) return aim;

        double ax = next2.getX() - next.getX();
        double az = next2.getZ() - next.getZ();
        double len = Math.sqrt(ax * ax + az * az);
        if (len < 1.0e-6) return aim;

        double off = 0.3;
        double x = Math.clamp(aim.x - ax / len * off, next.getX() + 0.15, next.getX() + 0.85);
        double z = Math.clamp(aim.z - az / len * off, next.getZ() + 0.15, next.getZ() + 0.85);
        return new Vec3(x, aim.y, z);
    }

    private void finalApproach(Vec3 botPos) {
        Vec3 finalLook = targetEntity != null ? targetEntity.getEyePosition() : target;
        actionPack.lookAt(new Vec3(target.x, finalLook.y, target.z));
        setVerticalLook(finalLook);
        applyMoveType(true, botPos, null);
        actionPack.setStrafing(0);
    }

    private void tickStuck(Vec3 botPos) {
        if (retrying && currentIndex > retryNextIndex) {
            retrying = false;
            retryTarget = null;
            stuckTime = 0;
            settings.setNodeHorizontalDistance(originalNodeDistance);
        }

        if (horizontalDistanceSq(botPos, lastPos) < 0.001) {
            stuckTime++;
            if (stuckTime % 10 == 0 && settings.isDebugEnabled(DebugChannel.STUCK)) {
                ServerLevel serverLevel = bot.level();
                serverLevel.sendParticles(ParticleTypes.SMOKE, bot.getX(), bot.getY() + 2.2, bot.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
            }
            if (stuckTime == 50) {
                actionPack.start(BotPlayerActionPack.ActionType.JUMP, BotPlayerActionPack.Action.once());
            }
            if (stuckTime > 100) {
                debugStuckEvent();
                if (retrying) {
                    retryTarget = null;
                    settings.setNodeHorizontalDistance(originalNodeDistance);
                    finish(false);
                    source.sendFailure(Component.literal(bot.getGameProfile().name() + " got stuck while pathing"));
                    return;
                }
                int prevIndex = Math.max(0, currentIndex - 1);
                retryTarget = path.get(prevIndex);
                retryNextIndex = currentIndex;
                currentIndex = prevIndex;
                retrying = true;
                stuckTime = 0;
                originalNodeDistance = settings.getNodeHorizontalDistance();
                settings.setNodeHorizontalDistance(originalNodeDistance / 2.0);
            }
        } else {
            stuckTime = 0;
        }
        lastPos = botPos;
    }

    private boolean canJumpTowards(Vec3 botPos, Vec3 waypointCenter) {
        Vec3 vel = bot.getDeltaMovement();
        double speedSq = vel.x * vel.x + vel.z * vel.z;
        if (speedSq < 0.1 * 0.1) return true;
        double dx = waypointCenter.x - botPos.x;
        double dz = waypointCenter.z - botPos.z;
        return vel.x * dx + vel.z * dz >= 0.0;
    }

    private void holdJumpInputs() {
        jumpInputHoldTicks = 2;
        actionPack.setForward(0);
        actionPack.setStrafing(0);
        actionPack.setSprinting(false);
    }

    private boolean isParkourJump(Vec3 botPos, BlockPos waypoint) {
        int bx = (int) Math.floor(botPos.x);
        int bz = (int) Math.floor(botPos.z);
        int by = (int) Math.floor(botPos.y);
        int wx = waypoint.getX();
        int wz = waypoint.getZ();
        int wy = waypoint.getY();

        if (wy < by) return false;

        int dx = wx - bx;
        int dz = wz - bz;
        int dist = Math.abs(dx) + Math.abs(dz);

        if (dist < 2 || dist > 4) return false;
        if (dx != 0 && dz != 0) return false;

        int stepDx = Integer.compare(dx, 0);
        int stepDz = Integer.compare(dz, 0);
        return !MovementHelper.canWalkOn(bot.level(), bx + stepDx, by - 1, bz + stepDz, settings);
    }

    private static boolean isFullySubmerged(BotPlayer bot) {
        int feetX = (int) Math.floor(bot.getX());
        int feetY = (int) Math.floor(bot.getY());
        int feetZ = (int) Math.floor(bot.getZ());
        int headY = (int) Math.floor(bot.getY() + bot.getEyeHeight());
        return isSwimmableBlock(bot, feetX, feetY, feetZ)
                && isSwimmableBlock(bot, feetX, headY, feetZ);
    }

    private static boolean isWading(BotPlayer bot) {
        int feetX = (int) Math.floor(bot.getX());
        int feetY = (int) Math.floor(bot.getY());
        int feetZ = (int) Math.floor(bot.getZ());
        return isSwimmableBlock(bot, feetX, feetY, feetZ);
    }

    private static boolean isSwimmableBlock(BotPlayer bot, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = bot.level().getBlockState(pos);
        if (MovementHelper.isWater(bot.level(), x, y, z)) return true;
        if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) return true;
        return !state.getFluidState().isEmpty() && state.getCollisionShape(bot.level(), pos).isEmpty();
    }

    private void setVerticalLook(Vec3 lookTarget) {
        Vec3 botEye = bot.getEyePosition();
        double dx = lookTarget.x - botEye.x;
        double dy = lookTarget.y - botEye.y;
        double dz = lookTarget.z - botEye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));
        bot.setXRot(pitch);
    }

    private boolean shouldAllowJump(double hDistSqToTarget, BlockPos currentWaypoint) {
        if (hDistSqToTarget <= 100.0) {
            return false;
        }

        if (currentWaypoint != null && currentIndex + 1 < path.size()) {
            BlockPos nextWaypoint = path.get(currentIndex + 1);
            return nextWaypoint.getY() == currentWaypoint.getY();
        }

        return true;
    }

    private void applyMoveType(boolean finalApproach, Vec3 botPos, BlockPos currentWaypoint) {
        if (finalApproach) {
            actionPack.setForward(1);
            actionPack.setSprinting(false);
            return;
        }

        double hDistSq = horizontalDistanceSq(botPos, target);
        boolean nearTarget = hDistSq <= 25.0;
        boolean closeRange = hDistSq <= 100.0;
        boolean climbing = currentWaypoint != null
                && (currentWaypoint.getY() > botPos.y + 0.5
                        || (currentIndex + 1 < path.size()
                                && path.get(currentIndex + 1).getY() != currentWaypoint.getY()));

        switch (settings.getMoveType()) {
            case WALK -> {
                actionPack.setForward(1);
                actionPack.setSprinting(false);
                actionPack.autoJump = true;
            }
            case SPRINT -> {
                actionPack.setForward(1);
                actionPack.setSprinting(!nearTarget && !climbing);
                actionPack.autoJump = true;
            }
            case SPRINT_JUMP -> {
                actionPack.setForward(1);
                actionPack.setSprinting(!nearTarget && !climbing);
                if (closeRange) {
                    actionPack.autoJump = true;
                } else {
                    actionPack.autoJump = false;
                    if (bot.onGround() && shouldAllowJump(hDistSq, currentWaypoint)) {
                        bot.jumpFromGround();
                        debugJump();
                    }
                }
            }
        }
    }

    private boolean isWithinTarget(Vec3 botPos) {
        double hDist = Math.sqrt(horizontalDistanceSq(botPos, target));
        double vDist = Math.abs(botPos.y - target.y);
        return settings.isWithinTarget(hDist, vDist);
    }

    private static Vec3 computeEntityTarget(Entity entity, PathSettings settings, BotPlayer bot) {
        if (settings.getMaxVerticalDistance() < 0) {
            BlockPos start = entity.blockPosition();
            var level = bot.level();
            for (int dy = 0; dy <= 64; dy++) {
                BlockPos check = start.below(dy);
                BlockState state = level.getBlockState(check);
                if (!state.getCollisionShape(level, check).isEmpty()) {
                    return Vec3.atBottomCenterOf(check.above());
                }
            }
        }
        return entity.position();
    }

    private void initDebugNodes(List<BlockPos> pathNodes) {
        if (!settings.isDebug()) {
            debugger = null;
            return;
        }
        if (debugger == null) {
            debugger = new LinkedHashSet<>();
        } else {
            debugger.clear();
        }
        if (pathNodes != null) {
            debugger.addAll(pathNodes);
        }
    }

    private void tickDebugParticles() {
        ServerLevel serverLevel = bot.level();
        if (debugger != null && !debugger.isEmpty() && settings.isDebugEnabled(DebugChannel.NODES)) {
            for (BlockPos pos : debugger) {
                serverLevel.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
        }
        if (retryTarget != null && settings.isDebugEnabled(DebugChannel.RETRY)) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, retryTarget.getX() + 0.5, retryTarget.getY() + 0.25, retryTarget.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
        if (currentIndex < path.size() && settings.isDebugEnabled(DebugChannel.WAYPOINT)) {
            BlockPos wp = path.get(currentIndex);
            serverLevel.sendParticles(ParticleTypes.END_ROD, wp.getX() + 0.5, wp.getY() + 0.6, wp.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    private void spawnDebugReached(BlockPos pos) {
        if (debugger == null || !debugger.remove(pos)) return;
        if (!settings.isDebugEnabled(DebugChannel.REACHED)) return;
        ServerLevel serverLevel = bot.level();
        serverLevel.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.2, 0.5, 0.2, 0);
    }

    private void debugLook(Vec3 point) {
        if (!settings.isDebugEnabled(DebugChannel.LOOK)) return;
        ServerLevel serverLevel = bot.level();
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + 0.1, point.z, 1, 0, 0, 0, 0);
    }

    private void debugJump() {
        if (!settings.isDebugEnabled(DebugChannel.JUMP)) return;
        ServerLevel serverLevel = bot.level();
        serverLevel.sendParticles(ParticleTypes.CLOUD, bot.getX(), bot.getY() + 0.1, bot.getZ(), 3, 0.1, 0.05, 0.1, 0);
    }

    private void debugRecalc() {
        if (!settings.isDebugEnabled(DebugChannel.RECALC)) return;
        ServerLevel serverLevel = bot.level();
        serverLevel.sendParticles(ParticleTypes.ENCHANT, bot.getX(), bot.getY() + 1.0, bot.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
    }

    private void debugStuckEvent() {
        if (!settings.isDebugEnabled(DebugChannel.STUCK)) return;
        ServerLevel serverLevel = bot.level();
        serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, bot.getX(), bot.getY() + 2.2, bot.getZ(), 3, 0.2, 0.2, 0.2, 0);
    }

    private void finish(boolean reached) {
        if (done) return;
        if (reached) hero.bane.herobot.ai.AiScriptRegistry.markPathReached(bot);
        else hero.bane.herobot.ai.AiScriptRegistry.markPathFailed(bot);
        stop();
    }

    public void stop() {
        if (done) return;
        done = true;
        pendingPath = null;
        pendingKind = null;
        pendingSplicePath = null;
        awaitingInitialPath = false;
        jumpInputHoldTicks = 0;
        if (debugger != null) debugger.clear();
        actionPack.setForward(0);
        actionPack.setStrafing(0);
        actionPack.setSprinting(false);
        actionPack.setSneaking(false);
        actionPack.autoJump = false;
        bot.setJumping(false);
    }

    public boolean isDone() {
        return done;
    }

    public Entity getTargetEntity() {
        return targetEntity;
    }

    public Vec3 getTarget() {
        return target;
    }

    public boolean isEntityMode() {
        return targetEntity != null;
    }

    public PathStats getLastPathStats() {
        return lastPathStats;
    }

    private static double horizontalDistanceSq(Vec3 botPos, Vec3 target) {
        double dx = botPos.x - target.x;
        double dz = botPos.z - target.z;
        return dx * dx + dz * dz;
    }
}
