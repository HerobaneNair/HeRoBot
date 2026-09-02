package hero.bane.herobot.mod.client.control;

import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.ActionType;
import hero.bane.herobot.common.bot.pathing.DebugChannel;
import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import hero.bane.herobot.common.bot.pathing.PathStats;
import hero.bane.herobot.mod.common.bot.pathing.placement.MovementHelper;
import hero.bane.herobot.mod.common.bot.pathing.placement.PathFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ClientPathing {
    private static final float VIEW_RATE = 15.0f;

    private final ClientPlayerController control = ClientPlayerController.INSTANCE;
    private final PathSettings settings;

    private float lookStartYaw, lookStartPitch;
    private float lookTargetYaw, lookTargetPitch;
    private long lookT0;
    private boolean lookInit;

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

    private final Entity targetEntity;
    private int recalcCd;
    private Vec3 pathedTarget;
    private Vec3 lastRecalcTarget;
    private BlockPos progressNode;
    private double closestNodeDist;
    private int noProgressTicks;
    private int jumpInputHoldTicks;
    private boolean jumpHeld;

    private enum PendingKind {INITIAL, FULL, SPLICE}

    private CompletableFuture<List<BlockPos>> pendingPath;
    private PendingKind pendingKind;
    private int pendingSpliceIdx;
    private List<BlockPos> pendingSplicePath;
    private Vec3 pendingTarget;
    private int failedRecalcs;
    private int recalcBackoffTicks;
    private boolean spliceFailedLast;
    private boolean awaitingInitialPath;

    private Set<BlockPos> debugger;

    public ClientPathing(Vec3 target, PathSettings settings) {
        this.path = List.of();
        this.target = target;
        this.settings = settings;
        this.targetEntity = null;
        this.lastPos = player().position();
        this.lastRecalcTarget = target;
        requestInitialPath();
    }

    public ClientPathing(Entity targetEntity, PathSettings settings) {
        this.targetEntity = targetEntity;
        this.settings = settings;
        this.target = computeEntityTarget(targetEntity, settings, level());
        this.lastPos = player().position();
        this.lastRecalcTarget = target;
        this.path = List.of();
        requestInitialPath();
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static Level level() {
        return Minecraft.getInstance().level;
    }

    private void requestInitialPath() {
        LocalPlayer p = player();
        awaitingInitialPath = true;
        pendingKind = PendingKind.INITIAL;
        pendingTarget = target;
        pendingPath = PathFinder.findPathAsync(level(),
                PathFinder.floorStart(level(), p.blockPosition(), settings),
                target, settings, p, 50000, new PathStats());
    }

    private boolean canRequestPath() {
        return !done && pendingPath == null && recalcBackoffTicks <= 0;
    }

    private boolean requestFullRecalc() {
        if (!canRequestPath()) return false;
        debugRecalc();
        LocalPlayer p = player();
        Vec3 currentTarget = targetEntity != null ? targetEntity.position() : target;
        pendingKind = PendingKind.FULL;
        pendingTarget = currentTarget;
        pendingPath = PathFinder.findPathAsync(level(), p.blockPosition(), currentTarget, settings, p, 50000, new PathStats());
        return true;
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
        pendingPath = PathFinder.findPathAsync(level(), spliceStart, newTarget, settings, player(), 50000, new PathStats());
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
                stop();
                message("Could not find a path");
            }
            return;
        }

        failedRecalcs = 0;
        spliceFailedLast = false;

        switch (kind) {
            case INITIAL -> {
                awaitingInitialPath = false;
                applyNewPath(result);
            }
            case FULL -> {
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
        this.currentIndex = entryIndex(newPath, player().position());
        this.stuckTime = 0;
        this.jumpInputHoldTicks = 0;
        initDebugNodes(newPath);
    }

    private static int entryIndex(List<BlockPos> nodes, Vec3 pos) {
        int nearest = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            double d = Vec3.atBottomCenterOf(nodes.get(i)).distanceToSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                nearest = i;
            }
        }

        if (nearest + 1 < nodes.size()) {
            Vec3 next = Vec3.atBottomCenterOf(nodes.get(nearest + 1));
            if (pos.distanceToSqr(next) < Vec3.atBottomCenterOf(nodes.get(nearest)).distanceToSqr(next)) {
                return nearest + 1;
            }
        }
        return nearest;
    }

    public void tick() {
        if (done) return;
        LocalPlayer p = player();
        if (p == null || level() == null) {
            stop();
            return;
        }

        if (recalcBackoffTicks > 0) recalcBackoffTicks--;
        pollPendingPath();
        if (done) return;

        Vec3 playerPos = p.position();

        if (!updateTarget(playerPos)) return;
        if (awaitingInitialPath) return;
        advanceWaypoints(playerPos);
        moveTowardGoal(playerPos);
        tickDebugParticles();
        tickNodeProgress(playerPos);
        tickStuck(playerPos);
    }

    private void tickNodeProgress(Vec3 playerPos) {
        if (currentIndex >= path.size()) {
            progressNode = null;
            return;
        }
        BlockPos wp = path.get(currentIndex);
        double dist = playerPos.distanceTo(Vec3.atBottomCenterOf(wp));
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
            progressNode = null;
            if (requestFullRecalc() && targetEntity != null) {
                pathedTarget = target;
                recalcCd = 4 + player().getRandom().nextInt(7);
            }
        }
    }

    private boolean updateTarget(Vec3 playerPos) {
        if (targetEntity != null) {
            if (targetEntity.isRemoved()) {
                stop();
                message("Lost target entity");
                return false;
            }
            target = computeEntityTarget(targetEntity, settings, level());
        }

        if (isWithinTarget(playerPos)) {
            if (targetEntity != null && !settings.isStopFollowing()) {
                control.setForward(0);
                control.setStrafing(0);
                control.setSprinting(false);
                smoothLook(targetEntity.getEyePosition(), targetEntity.getEyePosition());
                return false;
            }
            stop();
            message("Reached target" + (targetEntity != null ? " entity" : " position"));
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
        if (--recalcCd > 0) return;

        LocalPlayer p = player();
        boolean targetMoved = pathedTarget == null || pathedTarget.distanceToSqr(target) >= 1.0;
        if (!targetMoved && p.getRandom().nextFloat() >= 0.05f) {
            recalcCd = 4;
            return;
        }

        pathedTarget = target;
        recalcCd = 4 + p.getRandom().nextInt(7);

        double distSq = p.position().distanceToSqr(target);
        if (distSq > 1024.0) recalcCd += 10;
        else if (distSq > 256.0) recalcCd += 5;

        if (!requestFullRecalc()) recalcCd += 15;
    }

    private void tryRecalcPath() {
        if (lastRecalcTarget != null && lastRecalcTarget.distanceTo(target) <= 2.0) {
            return;
        }
        if (spliceFailedLast) requestFullRecalc();
        else requestSpliceRecalc(target);
    }

    private boolean hasOvershotNode(Vec3 playerPos, double hDist, double vDist) {
        if (currentIndex <= 0 || currentIndex >= path.size()) return false;
        if (hDist > 1.5 || vDist > 1.5) return false;
        BlockPos prev = path.get(currentIndex - 1);
        BlockPos cur = path.get(currentIndex);
        int segX = cur.getX() - prev.getX();
        int segZ = cur.getZ() - prev.getZ();
        if (segX == 0 && segZ == 0) return false;
        double relX = playerPos.x - (cur.getX() + 0.5);
        double relZ = playerPos.z - (cur.getZ() + 0.5);
        return relX * segX + relZ * segZ > 0.0;
    }

    private void advanceWaypoints(Vec3 playerPos) {
        while (currentIndex < path.size()) {
            BlockPos wp = path.get(currentIndex);
            double hDist = PathFinder.closestHDistToBlock(wp, playerPos);
            double vDist = Math.abs(playerPos.y - wp.getY());
            if (settings.isWithinNode(hDist, vDist) || hasOvershotNode(playerPos, hDist, vDist)) {
                spawnDebugReached(wp);
                currentIndex++;
            } else {
                break;
            }
        }

        if (currentIndex < path.size()) {
            for (int i = Math.min(path.size() - 1, currentIndex + 3); i > currentIndex; i--) {
                BlockPos futureWp = path.get(i);
                double hDist = PathFinder.closestHDistToBlock(futureWp, playerPos);
                double vDist = Math.abs(playerPos.y - futureWp.getY());
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
                if (candidate.getY() < playerPos.y - 0.5) {
                    double hDist = PathFinder.closestHDistToBlock(candidate, playerPos);
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

    private void moveTowardGoal(Vec3 playerPos) {
        if (currentIndex >= path.size()) {
            finalApproach(playerPos);
            return;
        }

        BlockPos waypoint = path.get(currentIndex);

        if (isFullySubmerged(player())) {
            swimToWaypoint(playerPos, waypoint);
        } else if (isWading(player())) {
            wadeToWaypoint(waypoint);
        } else {
            walkToWaypoint(playerPos, waypoint);
        }
    }

    private void swimToWaypoint(Vec3 playerPos, BlockPos waypoint) {
        Vec3 waypointMid = Vec3.atCenterOf(waypoint);
        smoothLook(waypointMid, waypointMid);
        control.setForward(1);
        control.setStrafing(0);
        control.setSprinting(true);

        double dy = waypointMid.y - playerPos.y;
        double hDist = Math.sqrt(horizontalDistanceSq(playerPos, waypointMid));
        boolean moreVertical = Math.abs(dy) > hDist;

        if (dy > 0.5) {
            setJumpHeld(true);
            if (moreVertical) control.setSneaking(false);
        } else if (dy < -0.5) {
            setJumpHeld(false);
            if (moreVertical) control.setSneaking(true);
        } else {
            setJumpHeld(false);
            control.setSneaking(false);
        }
    }

    private void wadeToWaypoint(BlockPos waypoint) {
        Vec3 waypointCenter = Vec3.atBottomCenterOf(waypoint);
        control.setSneaking(false);
        setJumpHeld(false);
        control.setForward(1);
        control.setStrafing(0);
        control.setSprinting(false);
        smoothLook(waypointCenter, waypointCenter);
    }

    private void walkToWaypoint(Vec3 playerPos, BlockPos waypoint) {
        LocalPlayer p = player();
        Vec3 waypointCenter = Vec3.atBottomCenterOf(waypoint);
        control.setSneaking(false);
        setJumpHeld(false);

        if (jumpInputHoldTicks > 0) {
            jumpInputHoldTicks--;
            control.setForward(0);
            control.setStrafing(0);
            control.setSprinting(false);
            smoothLook(waypointCenter, waypointCenter);
            return;
        }

        boolean ascending = waypoint.getY() > playerPos.y + 0.5;

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
        smoothLook(lookPoint, verticalTarget);
        debugLook(lookPoint);

        applyMoveType(false, playerPos, waypoint);

        double dx = waypointCenter.x - playerPos.x;
        double dz = waypointCenter.z - playerPos.z;
        double toWaypointAngle = Math.atan2(-dx, dz);
        double facingAngle = Math.toRadians(p.getYRot());
        double relativeAngle = toWaypointAngle - facingAngle;
        relativeAngle = Math.atan2(Math.sin(relativeAngle), Math.cos(relativeAngle));

        float forward = (float) Math.cos(relativeAngle);
        float strafe = (float) -Math.sin(relativeAngle);
        control.setForward(forward > 0.38f ? 1 : 0);
        control.setStrafing(Math.abs(strafe) > 0.38f ? Math.signum(strafe) : 0);

        if (p.onGround()) {
            if (isParkourJump(playerPos, waypoint)) {
                if (!canJumpTowards(playerPos, waypointCenter)) {
                    holdJumpInputs();
                    return;
                }
                control.setSprinting(true);
                p.jumpFromGround();
                debugJump();
            } else if (ascending) {
                if (horizontalDistanceSq(playerPos, waypointCenter) <= 1.3 * 1.3) {
                    if (!canJumpTowards(playerPos, waypointCenter)) {
                        holdJumpInputs();
                        return;
                    }
                    p.jumpFromGround();
                    debugJump();
                }
            } else if (waypoint.getY() < playerPos.y - 0.5
                    && settings.getMoveType() == PathSettings.MoveType.SPRINT_JUMP) {
                p.jumpFromGround();
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

    private void finalApproach(Vec3 playerPos) {
        Vec3 finalLook = targetEntity != null ? targetEntity.getEyePosition() : target;
        smoothLook(new Vec3(target.x, finalLook.y, target.z), finalLook);
        applyMoveType(true, playerPos, null);
        control.setStrafing(0);
    }

    private void tickStuck(Vec3 playerPos) {
        if (retrying && currentIndex > retryNextIndex) {
            retrying = false;
            retryTarget = null;
            stuckTime = 0;
            settings.setNodeHorizontalDistance(originalNodeDistance);
        }

        if (horizontalDistanceSq(playerPos, lastPos) < 0.001) {
            stuckTime++;
            if (stuckTime % 10 == 0 && settings.isDebugEnabled(DebugChannel.STUCK)) {
                LocalPlayer p = player();
                level().addParticle(ParticleTypes.SMOKE, p.getX(), p.getY() + 2.2, p.getZ(), 0, 0.01, 0);
            }
            if (stuckTime == 50) {
                control.start(ActionType.JUMP, Action.once());
            }
            if (stuckTime > 100) {
                debugStuckEvent();
                if (retrying) {
                    retryTarget = null;
                    settings.setNodeHorizontalDistance(originalNodeDistance);
                    stop();
                    message("Got stuck while pathing");
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
        lastPos = playerPos;
    }

    private boolean canJumpTowards(Vec3 playerPos, Vec3 waypointCenter) {
        Vec3 vel = player().getDeltaMovement();
        double speedSq = vel.x * vel.x + vel.z * vel.z;
        if (speedSq < 0.1 * 0.1) return true;
        double dx = waypointCenter.x - playerPos.x;
        double dz = waypointCenter.z - playerPos.z;
        return vel.x * dx + vel.z * dz >= 0.0;
    }

    private void holdJumpInputs() {
        jumpInputHoldTicks = 2;
        control.setForward(0);
        control.setStrafing(0);
        control.setSprinting(false);
    }

    private boolean isParkourJump(Vec3 playerPos, BlockPos waypoint) {
        int bx = (int) Math.floor(playerPos.x);
        int bz = (int) Math.floor(playerPos.z);
        int by = (int) Math.floor(playerPos.y);
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
        return !MovementHelper.canWalkOn(level(), bx + stepDx, by - 1, bz + stepDz, settings);
    }

    private static boolean isFullySubmerged(LocalPlayer p) {
        int feetX = (int) Math.floor(p.getX());
        int feetY = (int) Math.floor(p.getY());
        int feetZ = (int) Math.floor(p.getZ());
        int headY = (int) Math.floor(p.getY() + p.getEyeHeight());
        return isSwimmableBlock(feetX, feetY, feetZ)
                && isSwimmableBlock(feetX, headY, feetZ);
    }

    private static boolean isWading(LocalPlayer p) {
        int feetX = (int) Math.floor(p.getX());
        int feetY = (int) Math.floor(p.getY());
        int feetZ = (int) Math.floor(p.getZ());
        return isSwimmableBlock(feetX, feetY, feetZ);
    }

    private static boolean isSwimmableBlock(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        Level level = level();
        BlockState state = level.getBlockState(pos);
        if (MovementHelper.isWater(level, x, y, z)) return true;
        if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) return true;
        return !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    private void smoothLook(Vec3 yawPoint, Vec3 pitchPoint) {
        LocalPlayer p = player();
        Vec3 eye = p.getEyePosition();
        float targetYaw = Mth.wrapDegrees(
                (float) (Mth.atan2(yawPoint.z - eye.z, yawPoint.x - eye.x) * (180.0 / Math.PI)) - 90.0F);
        double pdx = pitchPoint.x - eye.x;
        double pdy = pitchPoint.y - eye.y;
        double pdz = pitchPoint.z - eye.z;
        double hDist = Math.sqrt(pdx * pdx + pdz * pdz);
        float targetPitch = (float) Math.toDegrees(-Math.atan2(pdy, hDist));

        float curYaw = p.getYRot();
        float curPitch = p.getXRot();
        float dYaw = Mth.wrapDegrees(targetYaw - curYaw);
        float dPitch = Mth.clamp(targetPitch, -90, 90) - curPitch;

        p.yRotO = curYaw;
        p.xRotO = curPitch;
        p.setYRot(curYaw + turnStep(dYaw));
        p.setXRot(Mth.clamp(curPitch + turnStep(dPitch), -90, 90));
        p.setYHeadRot(p.getYRot());
        rebaseView(p.getYRot(), p.getXRot());
    }

    private void rebaseView(float newYaw, float newPitch) {
        lookStartYaw = lookInit ? currentViewYaw() : newYaw;
        lookStartPitch = lookInit ? currentViewPitch() : newPitch;
        lookTargetYaw = newYaw;
        lookTargetPitch = newPitch;
        lookT0 = System.nanoTime();
        lookInit = true;
    }

    private float approach() {
        float elapsed = (System.nanoTime() - lookT0) / 1_000_000_000.0f;
        return 1.0f - (float) Math.exp(-VIEW_RATE * elapsed);
    }

    private float currentViewYaw() {
        return Mth.wrapDegrees(lookStartYaw + Mth.wrapDegrees(lookTargetYaw - lookStartYaw) * approach());
    }

    private float currentViewPitch() {
        return Mth.clamp(lookStartPitch + (lookTargetPitch - lookStartPitch) * approach(), -90.0f, 90.0f);
    }

    public Float viewYaw() {
        return lookInit ? currentViewYaw() : null;
    }

    public Float viewPitch() {
        return lookInit ? currentViewPitch() : null;
    }

    private static final float TURN_LERP = 0.25f;
    private static final float TURN_MAX_PER_TICK = 12.0f;

    private static float turnStep(float delta) {
        if (Math.abs(delta) <= 1.0f) return delta;
        return Mth.clamp(delta * TURN_LERP, -TURN_MAX_PER_TICK, TURN_MAX_PER_TICK);
    }

    private void setJumpHeld(boolean held) {
        if (jumpHeld == held) return;
        jumpHeld = held;
        Minecraft.getInstance().options.keyJump.setDown(held);
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

    private void applyMoveType(boolean finalApproach, Vec3 playerPos, BlockPos currentWaypoint) {
        if (finalApproach) {
            control.setForward(1);
            control.setSprinting(false);
            return;
        }

        double hDistSq = horizontalDistanceSq(playerPos, target);
        boolean nearTarget = hDistSq <= 25.0;
        boolean closeRange = hDistSq <= 100.0;
        boolean climbing = currentWaypoint != null
                && (currentWaypoint.getY() > playerPos.y + 0.5
                || (currentIndex + 1 < path.size()
                && path.get(currentIndex + 1).getY() != currentWaypoint.getY()));

        switch (settings.getMoveType()) {
            case WALK -> {
                control.setForward(1);
                control.setSprinting(false);
            }
            case SPRINT -> {
                control.setForward(1);
                control.setSprinting(!nearTarget && !climbing);
            }
            case SPRINT_JUMP -> {
                control.setForward(1);
                control.setSprinting(!nearTarget && !climbing);
                if (!closeRange && player().onGround() && shouldAllowJump(hDistSq, currentWaypoint)) {
                    player().jumpFromGround();
                    debugJump();
                }
            }
        }
    }

    private boolean isWithinTarget(Vec3 playerPos) {
        double hDist = Math.sqrt(horizontalDistanceSq(playerPos, target));
        double vDist = Math.abs(playerPos.y - target.y);
        return settings.isWithinTarget(hDist, vDist);
    }

    private static Vec3 computeEntityTarget(Entity entity, PathSettings settings, Level level) {
        if (settings.getMaxVerticalDistance() < 0) {
            BlockPos start = entity.blockPosition();
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

    private void message(String text) {
        LocalPlayer p = player();
        if (p != null) p.sendOverlayMessage(Component.literal(text));
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

    @SuppressWarnings("resource")
    private void tickDebugParticles() {
        Level level = level();
        if (debugger != null && !debugger.isEmpty() && settings.isDebugEnabled(DebugChannel.NODES)) {
            for (BlockPos pos : debugger) {
                level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, 0, 0, 0);
            }
        }
        if (retryTarget != null && settings.isDebugEnabled(DebugChannel.RETRY)) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, retryTarget.getX() + 0.5, retryTarget.getY() + 0.25, retryTarget.getZ() + 0.5, 0, 0, 0);
        }
        if (currentIndex < path.size() && settings.isDebugEnabled(DebugChannel.WAYPOINT)) {
            BlockPos wp = path.get(currentIndex);
            level.addParticle(ParticleTypes.END_ROD, wp.getX() + 0.5, wp.getY() + 0.6, wp.getZ() + 0.5, 0, 0, 0);
        }
    }

    private void spawnDebugReached(BlockPos pos) {
        if (debugger == null || !debugger.remove(pos)) return;
        if (!settings.isDebugEnabled(DebugChannel.REACHED)) return;
        level().addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0.2, 0);
    }

    private void debugLook(Vec3 point) {
        if (!settings.isDebugEnabled(DebugChannel.LOOK)) return;
        level().addParticle(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + 0.1, point.z, 0, 0, 0);
    }

    private void debugJump() {
        if (!settings.isDebugEnabled(DebugChannel.JUMP)) return;
        LocalPlayer p = player();
        level().addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 0, 0.05, 0);
    }

    private void debugRecalc() {
        if (!settings.isDebugEnabled(DebugChannel.RECALC)) return;
        LocalPlayer p = player();
        level().addParticle(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.0, p.getZ(), 0, 0.5, 0);
    }

    private void debugStuckEvent() {
        if (!settings.isDebugEnabled(DebugChannel.STUCK)) return;
        LocalPlayer p = player();
        level().addParticle(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2.2, p.getZ(), 0, 0, 0);
    }

    public void stop() {
        if (done) return;
        done = true;
        lookInit = false;
        pendingPath = null;
        pendingKind = null;
        pendingSplicePath = null;
        awaitingInitialPath = false;
        jumpInputHoldTicks = 0;
        if (debugger != null) debugger.clear();
        control.setForward(0);
        control.setStrafing(0);
        control.setSprinting(false);
        control.setSneaking(false);
        setJumpHeld(false);
    }

    public boolean isDone() {
        return done;
    }

    public Vec3 getTarget() {
        return target;
    }

    private static double horizontalDistanceSq(Vec3 playerPos, Vec3 target) {
        double dx = playerPos.x - target.x;
        double dz = playerPos.z - target.z;
        return dx * dx + dz * dz;
    }
}
