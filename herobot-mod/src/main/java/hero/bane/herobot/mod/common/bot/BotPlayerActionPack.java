package hero.bane.herobot.mod.common.bot;

import hero.bane.herobot.mod.common.HeroBotSettings;
import hero.bane.herobot.mod.common.bot.connection.ServerPlayerInterface;
import hero.bane.herobot.mod.common.control.PlayerController;
import hero.bane.herobot.mod.common.util.RayTrace;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.stream.StreamSupport;

@SuppressWarnings("UnusedReturnValue")
public class BotPlayerActionPack implements PlayerController {
    public final ServerPlayer player;

    private final Map<ActionType, Action> actions = new EnumMap<>(ActionType.class);

    private BlockPos currentBlock;
    private int blockHitDelay;
    private boolean isHittingBlock;
    private float curBlockDamageMP;
    public boolean autoJump = false;
    private int autoJumpTime;

    private boolean sneaking;
    private boolean sprinting;
    private float forward;
    private float strafing;

    private long lastJumpOnceTick = -100;
    private boolean jumping;
    private int vehicleJumpOverrideTicks = 0;
    private boolean clearJumpNextTick = false;
    private int jumpChargeAmount = 0;

    private int itemUseCooldown;

    private record DelayedAction(long tick, Runnable action) {
    }

    private final List<DelayedAction> pendingActions = new ArrayList<>();

    private LookInterpolation lookInterpolation;

    private static class LookInterpolation {
        float targetYaw;
        float targetPitch;
        float deltaYaw;
        float deltaPitch;
        int ticksRemaining;

        LookInterpolation(float targetYaw, float targetPitch, float deltaYaw, float deltaPitch, int ticks) {
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.ticksRemaining = ticks;
        }
    }

    public BotPlayerActionPack(ServerPlayer playerIn) {
        player = playerIn;
        stopAll();
    }

    public void copyFrom(BotPlayerActionPack other) {
        actions.putAll(other.actions);
        currentBlock = other.currentBlock;
        blockHitDelay = other.blockHitDelay;
        isHittingBlock = other.isHittingBlock;
        curBlockDamageMP = other.curBlockDamageMP;

        sneaking = other.sneaking;
        sprinting = other.sprinting;
        forward = other.forward;
        strafing = other.strafing;

        itemUseCooldown = other.itemUseCooldown;
    }

    public BotPlayerActionPack start(ActionType type, Action action) {
        if (action.isContinuous) {
            Action curent = actions.get(type);
            if (curent != null) return this;
        }

        Action previous = actions.remove(type);
        if (previous != null) type.stop(player, previous);
        actions.put(type, action);
        return this;
    }

    public BotPlayerActionPack stop(ActionType type) {
        Action action = actions.remove(type);
        if (action != null) {
            action.ticksRemaining = -1;
            type.stop(player, action);
        }
        if (type == ActionType.USE) {
            itemUseCooldown = 0;
            player.releaseUsingItem();
        }
        return this;
    }

    public BotPlayerActionPack setSneaking(boolean doSneak) {
        sneaking = doSneak;
        player.setShiftKeyDown(doSneak);
        return this;
    }

    public BotPlayerActionPack setSprinting(boolean doSprint) {
        sprinting = doSprint;
        player.setSprinting(doSprint);
        return this;
    }

    public BotPlayerActionPack setForward(float value) {
        forward = value;
        return this;
    }

    public BotPlayerActionPack setStrafing(float value) {
        strafing = value;
        return this;
    }

    public BotPlayerActionPack setAutoJump(boolean value) {
        autoJump = value;
        return this;
    }

    public float getForward() {
        return forward;
    }

    public float getStrafing() {
        return strafing;
    }

    public boolean isActive(ActionType type) {
        return actions.containsKey(type);
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public BotPlayerActionPack look(Direction direction) {
        return switch (direction) {
            case NORTH -> look(180, 0);
            case SOUTH -> look(0, 0);
            case EAST -> look(-90, 0);
            case WEST -> look(90, 0);
            case UP -> look(player.getYRot(), -90);
            case DOWN -> look(player.getYRot(), 90);
        };
    }

    public BotPlayerActionPack look(Vec2 rotation) {
        return look(rotation.y, rotation.x);
    }

    public BotPlayerActionPack look(float yaw, float pitch) {
        player.setYRot(yaw % 360);
        player.setXRot(Mth.clamp(pitch, -90, 90));
        return this;
    }

    public BotPlayerActionPack lookAt(Vec3 position) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, position);
        return this;
    }

    public BotPlayerActionPack turn(float yaw, float pitch) {
        return look(player.getYRot() + yaw, player.getXRot() + pitch);
    }

    public BotPlayerActionPack lookInterpolated(float targetYaw, float targetPitch, int ticks) {
        if (ticks <= 0) return look(targetYaw, targetPitch);
        float clampedPitch = Mth.clamp(targetPitch, -90, 90);
        lookInterpolation = new LookInterpolation(
                targetYaw,
                clampedPitch,
                Mth.wrapDegrees(targetYaw - player.getYRot()) / ticks,
                (clampedPitch - player.getXRot()) / ticks,
                ticks
        );
        return this;
    }

    public BotPlayerActionPack look(Direction direction, int ticks) {
        float targetYaw, targetPitch;
        switch (direction) {
            case NORTH -> {
                targetYaw = 180;
                targetPitch = 0;
            }
            case SOUTH -> {
                targetYaw = 0;
                targetPitch = 0;
            }
            case EAST -> {
                targetYaw = -90;
                targetPitch = 0;
            }
            case WEST -> {
                targetYaw = 90;
                targetPitch = 0;
            }
            case UP -> {
                targetYaw = player.getYRot();
                targetPitch = -90;
            }
            case DOWN -> {
                targetYaw = player.getYRot();
                targetPitch = 90;
            }
            default -> {
                return this;
            }
        }
        return lookInterpolated(targetYaw, targetPitch, ticks);
    }

    public BotPlayerActionPack look(Vec2 rotation, int ticks) {
        return lookInterpolated(rotation.y, rotation.x, ticks);
    }

    public BotPlayerActionPack turn(float yaw, float pitch, int ticks) {
        return lookInterpolated(player.getYRot() + yaw, player.getXRot() + pitch, ticks);
    }

    public BotPlayerActionPack lookAt(Vec3 position, int ticks) {
        if (ticks <= 0) return lookAt(position);
        Vec3 eye = player.getEyePosition();
        double dx = position.x - eye.x;
        double dy = position.y - eye.y;
        double dz = position.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, dist) * (180.0 / Math.PI))));
        return lookInterpolated(yaw, pitch, ticks);
    }

    public void stopInterpolation() {
        lookInterpolation = null;
    }

    public BotPlayerActionPack stopMovement() {
        setSneaking(false);
        setSprinting(false);
        forward = 0.0F;
        strafing = 0.0F;
        return this;
    }

    public BotPlayerActionPack startOrExtender(ActionType type, int ticks) {
        Action current = actions.get(type);
        if (current != null && current.isContinuous) {
            current.ticksRemaining = ticks;
            return this;
        }
        Action previous = actions.remove(type);
        if (previous != null) type.stop(player, previous);
        actions.put(type, Action.continuous(ticks));
        return this;
    }

    public BotPlayerActionPack stopAll() {
        for (ActionType type : actions.keySet()) type.stop(player, actions.get(type));
        actions.clear();
        stopInterpolation();
        if (player instanceof BotPlayer bot) bot.clearPathFollower();
        return stopMovement();
    }

    public void onUpdate() {
        if (autoJumpTime > 0) {
            --autoJumpTime;
            start(ActionType.JUMP, Action.once());
        }

        if (lookInterpolation != null) {
            lookInterpolation.ticksRemaining--;
            if (lookInterpolation.ticksRemaining == 0) {
                look(lookInterpolation.targetYaw, lookInterpolation.targetPitch);
                lookInterpolation = null;
            } else {
                player.setYRot(Mth.wrapDegrees(player.getYRot() + lookInterpolation.deltaYaw));
                player.setXRot(Mth.clamp(player.getXRot() + lookInterpolation.deltaPitch, -90, 90));
            }
        }

        if (!pendingActions.isEmpty()) {
            long currentTick = player.level().getServer().getTickCount();
            var it = pendingActions.iterator();
            while (it.hasNext()) {
                DelayedAction da = it.next();
                if (currentTick >= da.tick()) {
                    da.action().run();
                    it.remove();
                }
            }
        }

        Map<ActionType, Boolean> actionAttempts = new HashMap<>();
        actions.values().removeIf(e -> e.done);
        for (Map.Entry<ActionType, Action> e : actions.entrySet()) {
            ActionType type = e.getKey();
            Action action = e.getValue();
            if (!(actionAttempts.getOrDefault(ActionType.USE, false) && type == ActionType.ATTACK)) {
                Boolean actionStatus = action.tick(this, type);
                if (actionStatus != null)
                    actionAttempts.put(type, actionStatus);
            }
            if (type == ActionType.ATTACK
                    && actionAttempts.getOrDefault(ActionType.ATTACK, false)
                    && !actionAttempts.getOrDefault(ActionType.USE, true)) {
                Action using = actions.get(ActionType.USE);
                if (using != null) {
                    using.retry(this, ActionType.USE);
                }
            }
        }

        if (vehicleJumpOverrideTicks > 0) {
            player.setJumping(true);
            vehicleJumpOverrideTicks--;
            if (vehicleJumpOverrideTicks == 0) clearJumpNextTick = true;
        } else if (clearJumpNextTick) {
            player.setJumping(false);
            clearJumpNextTick = false;
        }

        if ((forward != 0.0F || strafing != 0.0F) && player.getFoodData().getFoodLevel() < 3) {
            player.setSprinting(false);
        } else if (sprinting) {
            player.setSprinting(true);
        }

        float vel = sneaking ? 0.3F : 1.0F;
        vel *= (player.isUsingItem() &&
                !player.getMainHandItem().has(DataComponents.KINETIC_WEAPON))
                ? 0.20F : 1.0F;

        if (forward != 0.0F || player instanceof BotPlayer) {
            player.zza = forward * vel;
        }
        if (strafing != 0.0F || player instanceof BotPlayer) {
            player.xxa = strafing * vel;
        }

        if (player.getAbilities().flying && player instanceof BotPlayer) {
            if (sneaking && player.onGround() && !player.isSpectator()) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            } else {
                double verticalSpeed = 0.05 * 3.0;
                Vec3 dm = player.getDeltaMovement();
                if (jumping && !sneaking) {
                    player.setDeltaMovement(dm.add(0, verticalSpeed, 0));
                } else if (sneaking && !jumping) {
                    player.setDeltaMovement(dm.add(0, -verticalSpeed, 0));
                }
            }
            if (jumping && actions.get(ActionType.JUMP) == null) {
                jumping = false;
            }
        }
    }

    public void updateAutoJump(float f, float g) {
        if (!canAutoJump()) return;

        Vec3 startPos = player.position();
        Vec3 endPos = startPos.add(f, 0.0, g);
        Vec3 movement = new Vec3(f, 0.0, g);
        float speed = player.getSpeed();
        float movementLenSqr = (float) movement.lengthSqr();

        if (movementLenSqr <= 0.001F) {
            Vec2 moveVector = new Vec2(strafing, forward);
            float xInput = speed * moveVector.x;
            float zInput = speed * moveVector.y;
            float sin = Mth.sin(player.getYRot() * ((float) Math.PI / 180F));
            float cos = Mth.cos(player.getYRot() * ((float) Math.PI / 180F));
            movement = new Vec3(xInput * cos - zInput * sin, movement.y, zInput * cos + xInput * sin);
            movementLenSqr = (float) movement.lengthSqr();
            if (movementLenSqr <= 0.001F) {
                return;
            }
        }

        float invLen = Mth.invSqrt(movementLenSqr);
        Vec3 movementDir = movement.scale(invLen);
        Vec3 playerForward = player.getForward();
        float forwardDot = (float) (playerForward.x * movementDir.x + playerForward.z * movementDir.z);
        if (forwardDot < -0.15F) return;

        CollisionContext collisionContext = CollisionContext.of(player);
        BlockPos blockPos = BlockPos.containing(player.getX(), player.getBoundingBox().maxY, player.getZ());
        if (!player.level().getBlockState(blockPos).getCollisionShape(player.level(), blockPos, collisionContext).isEmpty())
            return;

        blockPos = blockPos.above();
        if (!player.level().getBlockState(blockPos).getCollisionShape(player.level(), blockPos, collisionContext).isEmpty())
            return;

        float maxStepHeight = 1.2F;
        if (player.hasEffect(MobEffects.JUMP_BOOST)) {
            maxStepHeight += (float) (Objects.requireNonNull(player.getEffect(MobEffects.JUMP_BOOST)).getAmplifier() + 1) * 0.75F;
        }

        float probeDistance = Math.max(speed * 7.0F, 1.0F / invLen);
        Vec3 probeEnd = endPos.add(movementDir.scale(probeDistance));
        float bbWidth = player.getBbWidth();
        float bbHeight = player.getBbHeight();

        AABB searchBox = (new AABB(startPos, probeEnd.add(0.0, bbHeight, 0.0))).inflate(bbWidth, 0.0, bbWidth);

        Vec3 raisedStart = startPos.add(0.0, 0.51, 0.0);
        Vec3 raisedEnd = probeEnd.add(0.0, 0.51, 0.0);

        Vec3 side = movementDir.cross(new Vec3(0.0, 1.0, 0.0));
        Vec3 halfWidth = side.scale(bbWidth * 0.5F);

        Vec3 leftStart = raisedStart.subtract(halfWidth);
        Vec3 leftEnd = raisedEnd.subtract(halfWidth);
        Vec3 rightStart = raisedStart.add(halfWidth);
        Vec3 rightEnd = raisedEnd.add(halfWidth);

        Iterable<VoxelShape> collisions = player.level().getCollisions(player, searchBox);
        Iterator<AABB> iterator = StreamSupport.stream(collisions.spliterator(), false)
                .flatMap(shape -> shape.toAabbs().stream())
                .iterator();

        float obstacleTopY = Float.MIN_VALUE;

        while (iterator.hasNext()) {
            AABB hitBox = iterator.next();
            if (hitBox.intersects(leftStart, leftEnd) || hitBox.intersects(rightStart, rightEnd)) {
                obstacleTopY = (float) hitBox.maxY;
                Vec3 center = hitBox.getCenter();
                BlockPos obstaclePos = BlockPos.containing(center);

                for (int u = 1; (float) u < maxStepHeight; ++u) {
                    BlockPos abovePos = obstaclePos.above(u);
                    VoxelShape aboveShape = player.level().getBlockState(abovePos).getCollisionShape(player.level(), abovePos, collisionContext);
                    if (!aboveShape.isEmpty()) {
                        obstacleTopY = (float) aboveShape.max(Direction.Axis.Y) + (float) abovePos.getY();
                        if ((double) obstacleTopY - player.getY() > (double) maxStepHeight) {
                            return;
                        }
                    }

                    if (u > 1) {
                        blockPos = blockPos.above();
                        if (!player.level().getBlockState(blockPos).getCollisionShape(player.level(), blockPos, collisionContext).isEmpty()) {
                            return;
                        }
                    }
                }
                break;
            }
        }

        if (obstacleTopY != Float.MIN_VALUE) {
            float stepHeight = (float) ((double) obstacleTopY - player.getY());
            if (stepHeight > 0.5F && stepHeight <= maxStepHeight) {
                autoJumpTime = 1;
            }
        }
    }

    public void attemptAutoJump() {
        boolean wasAutoJump = autoJump;
        autoJump = true;
        updateAutoJump(0, 0);
        autoJump = wasAutoJump;
    }

    private boolean canAutoJump() {
        return autoJump
                && autoJumpTime <= 0
                && player.onGround()
                && !player.isPassenger()
                && (forward != 0 || strafing != 0);
    }

    private static void dropAndSwing(ServerPlayer player, boolean dropStack) {
        boolean dropped = !player.getInventory().getSelectedItem().isEmpty();
        player.drop(dropStack);
        if (dropped) player.swing(InteractionHand.MAIN_HAND);
    }

    static HitResult getTarget(ServerPlayer player) {
        double blockReach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        double entityReach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);

        HitResult hit = RayTrace.rayTrace(player, 1, blockReach, false);

        if (hit.getType() == HitResult.Type.BLOCK) return hit;
        return RayTrace.rayTrace(player, 1, entityReach, false);
    }

    public void setSlot(int slot) {
        player.getInventory().setSelectedSlot(slot - 1);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot - 1));
    }

    public void pickBlock(boolean includeData) {
        // Copying block entity data is a creative-only ability; vanilla enforces this too.
        boolean data = includeData && player.hasInfiniteMaterials();
        HitResult hit = getTarget(player);
        switch (hit.getType()) {
            case BLOCK -> player.connection.handlePickItemFromBlock(
                    new ServerboundPickItemFromBlockPacket(((BlockHitResult) hit).getBlockPos(), data));
            case ENTITY -> player.connection.handlePickItemFromEntity(
                    new ServerboundPickItemFromEntityPacket(((EntityHitResult) hit).getEntity().getId(), data));
            case MISS -> { }
        }
    }

    private static void handleSpearStab(ServerPlayer player) {
        if (player.getAttackStrengthScale(0.5F) < 1.0F) return;
        player.connection.handlePlayerAction(
                new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STAB,
                        player.blockPosition(),
                        player.getDirection()
                )
        );
        player.resetLastActionTime();
    }

    public enum ActionType {
        USE(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                if (ap.itemUseCooldown > 0) {
                    ap.itemUseCooldown--;
                    return true;
                }
                if (player.isUsingItem()) {
                    return true;
                }
                HitResult hit = getTarget(player);
                int uses = action.hits;

                if (player instanceof BotPlayer bot && HeroBotSettings.botLagUses) {
                    int delay = bot.delayTicks(1);
                    if (delay > 0) {
                        long executeAt = player.level().getServer().getTickCount() + delay;
                        ap.pendingActions.add(new DelayedAction(executeAt, () -> repeatUse(player, hit, uses)));
                        ap.itemUseCooldown = delay;
                        return true;
                    }
                }

                return repeatUse(player, hit, uses);
            }

            @Override
            void inactiveTick(ServerPlayer player, Action action) {
                BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                ap.itemUseCooldown = 0;
                player.releaseUsingItem();
            }
        },
        ATTACK(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                ItemStack stack = player.getMainHandItem();
                boolean isSpear = stack.has(DataComponents.KINETIC_WEAPON);

                if (isSpear) {
                    if (player.getAttackStrengthScale(0.5F) < 1.0F) return false;

                    if (player instanceof BotPlayer bot && HeroBotSettings.botLagAttacks) {
                        int delay = bot.delayTicks(1);
                        if (delay > 0) {
                            BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                            long executeAt = player.level().getServer().getTickCount() + delay;
                            ap.pendingActions.add(new DelayedAction(executeAt, () -> handleSpearStab(player)));
                            return true;
                        }
                    }
                    handleSpearStab(player);
                    return true;
                }

                HitResult hit = getTarget(player);
                switch (hit.getType()) {
                    case ENTITY: {
                        Entity target = ((EntityHitResult) hit).getEntity();
                        boolean continuous = action.isContinuous;

                        if (player instanceof BotPlayer bot && HeroBotSettings.botLagAttacks) {
                            int delay = bot.delayTicks(1);
                            if (delay > 0) {
                                boolean wasSprinting = player.isSprinting();
                                double savedFallDistance = player.fallDistance;
                                boolean wasOnGround = player.onGround();

                                BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                                long executeAt = player.level().getServer().getTickCount() + delay;
                                ap.pendingActions.add(new DelayedAction(executeAt, () -> {
                                    boolean currentSprinting = player.isSprinting();
                                    double currentFallDistance = player.fallDistance;
                                    boolean currentOnGround = player.onGround();

                                    player.setSprinting(wasSprinting);
                                    player.fallDistance = savedFallDistance;
                                    player.setOnGround(wasOnGround);

                                    if (!continuous) {
                                        for (int i = 0; i < action.hits; i++) {
                                            player.attack(target);
                                            player.swing(InteractionHand.MAIN_HAND);
                                            player.resetAttackStrengthTicker();
                                        }
                                    } else {
                                        player.resetAttackStrengthTicker();
                                    }
                                    player.resetLastActionTime();

                                    player.setSprinting(currentSprinting);
                                    player.fallDistance = currentFallDistance;
                                    player.setOnGround(currentOnGround);
                                }));
                                return true;
                            }
                        }

                        if (!continuous) {
                            for (int i = 0; i < action.hits; i++) {
                                player.attack(target);
                                player.swing(InteractionHand.MAIN_HAND);
                                player.resetAttackStrengthTicker();
                            }
                        } else {
                            player.resetAttackStrengthTicker();
                        }
                        player.resetLastActionTime();
                        return true;
                    }
                    case BLOCK: {
                        BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                        if (ap.blockHitDelay > 0) {
                            ap.blockHitDelay--;
                            return false;
                        }
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        BlockPos pos = blockHit.getBlockPos();
                        Direction side = blockHit.getDirection();
                        if (player.blockActionRestricted(player.level(), pos, player.gameMode.getGameModeForPlayer()))
                            return false;
                        if (ap.currentBlock != null && player.level().getBlockState(ap.currentBlock).isAir()) {
                            ap.currentBlock = null;
                            return false;
                        }
                        BlockState state = player.level().getBlockState(pos);
                        boolean blockBroken = false;
                        if (player.gameMode.getGameModeForPlayer().isCreative()) {
                            player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                            ap.blockHitDelay = 5;
                            blockBroken = true;
                        } else if (ap.currentBlock == null || !ap.currentBlock.equals(pos)) {
                            if (ap.currentBlock != null) {
                                player.gameMode.handleBlockBreakAction(ap.currentBlock, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                            }
                            player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                            boolean notAir = !state.isAir();
                            if (notAir && ap.curBlockDamageMP == 0) {
                                state.attack(player.level(), pos, player);
                            }
                            if (notAir && state.getDestroyProgress(player, player.level(), pos) >= 1) {
                                ap.currentBlock = null;
                                blockBroken = true;
                            } else {
                                ap.currentBlock = pos;
                                ap.curBlockDamageMP = 0;
                            }
                        } else {
                            ap.curBlockDamageMP += state.getDestroyProgress(player, player.level(), pos);
                            if (ap.curBlockDamageMP >= 1) {
                                player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                                ap.currentBlock = null;
                                ap.blockHitDelay = 5;
                                blockBroken = true;
                            }
                            player.level().destroyBlockProgress(-1, pos, (int) (ap.curBlockDamageMP * 10));
                        }
                        player.resetLastActionTime();
                        player.swing(InteractionHand.MAIN_HAND);
                        if (action.resetMineCheck() && ap.currentBlock != null) {
                            player.gameMode.handleBlockBreakAction(ap.currentBlock, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                            player.level().destroyBlockProgress(-1, ap.currentBlock, -1);
                            ap.currentBlock = null;
                            ap.curBlockDamageMP = 0;
                        }
                        return blockBroken;
                    }
                }
                if (!action.isContinuous) player.swing(InteractionHand.MAIN_HAND);
                player.resetAttackStrengthTicker();
                player.resetLastActionTime();
                return false;
            }
        },
        JUMP(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                long currentTick = player.level().getServer().getTickCount();

                if (action.limit == 1) {
                    if (player.isPassenger()) {
                        if (player.getVehicle() instanceof PlayerRideableJumping jumpVehicle) {
                            jumpVehicle.handleStartJump(1);
                            jumpVehicle.handleStopJump();
                            ap.jumpChargeAmount = 0;
                        } else {
                            ap.vehicleJumpOverrideTicks = 2;
                            player.setJumping(true);
                        }
                    } else {
                        boolean pathing = player instanceof BotPlayer bot
                                && bot.getPathFollower() != null
                                && !bot.getPathFollower().isDone();
                        if (!pathing) {
                            if (player.getAbilities().mayfly && (currentTick - ap.lastJumpOnceTick) <= 7) {
                                player.getAbilities().flying = !player.getAbilities().flying;
                                player.onUpdateAbilities();
                                ap.lastJumpOnceTick = -100;
                                return false;
                            }
                            ap.lastJumpOnceTick = currentTick;
                        }

                        if (player.getAbilities().flying) {
                            ap.jumping = true;
                        } else if (player.onGround()) {
                            player.jumpFromGround();
                        } else if (!player.onClimbable() && !player.getAbilities().flying) {
                            player.tryToStartFallFlying();
                        }
                    }
                } else {
                    if (player.isPassenger()) {
                        if (player.getVehicle() instanceof PlayerRideableJumping jumpVehicle) {
                            ap.jumpChargeAmount = Math.min(ap.jumpChargeAmount + 1, 100);
                            jumpVehicle.handleStartJump(ap.jumpChargeAmount);
                        } else if (player.getAbilities().flying) {
                            ap.jumping = true;
                        } else {
                            player.setJumping(true);
                        }
                    } else if (player.getAbilities().flying) {
                        ap.jumping = true;
                    } else {
                        if (!player.onGround() && !player.onClimbable()) {
                            player.tryToStartFallFlying();
                        }
                        player.setJumping(true);
                    }
                }
                return false;
            }

            @Override
            void inactiveTick(ServerPlayer player, Action action) {
                BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                if (ap.vehicleJumpOverrideTicks <= 0) {
                    player.setJumping(false);
                }
                ap.jumping = false;
                if (player.getVehicle() instanceof PlayerRideableJumping jumpVehicle) {
                    jumpVehicle.handleStopJump();
                    ap.jumpChargeAmount = 0;
                }
            }
        },
        DROP_ITEM(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                dropAndSwing(player, false);

                return false;
            }
        },
        DROP_STACK(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                dropAndSwing(player, true);

                return false;
            }
        },
        SWAP_HANDS(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                ItemStack itemStack_1 = player.getItemInHand(InteractionHand.OFF_HAND);
                player.setItemInHand(InteractionHand.OFF_HAND, player.getItemInHand(InteractionHand.MAIN_HAND));
                player.setItemInHand(InteractionHand.MAIN_HAND, itemStack_1);
                return false;
            }
        },
        SWING(false) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                InteractionHand hand = action.hand == null ? InteractionHand.MAIN_HAND : action.hand;
                player.swing(hand);
                player.resetLastActionTime();
                return false;
            }
        };

private static boolean repeatUse(ServerPlayer player, HitResult hit, int uses) {
            boolean used = false;
            for (int i = 0; i < uses; i++) used |= executeUse(player, hit);
            return used;
        }

        private static boolean executeUse(ServerPlayer player, HitResult hit) {
            BotPlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
            for (InteractionHand hand : InteractionHand.values()) {
                switch (hit.getType()) {
                    case BLOCK: {
                        player.resetLastActionTime();
                        ServerLevel world = player.level();
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        BlockPos pos = blockHit.getBlockPos();
                        Direction side = blockHit.getDirection();
                        if (pos.getY() < player.level().getMaxY() - (side == Direction.UP ? 1 : 0) && world.mayInteract(player, pos)) {
                            InteractionResult result = player.gameMode.useItemOn(player, world, player.getItemInHand(hand), hand, blockHit);
                            if (result instanceof InteractionResult.Success success) {
                                if (success.swingSource() != InteractionResult.SwingSource.NONE)
                                    player.swing(hand);
                                ap.itemUseCooldown = 3;
                                return true;
                            }
                        }
                        break;
                    }
                    case ENTITY: {
                        player.resetLastActionTime();
                        EntityHitResult entityHit = (EntityHitResult) hit;
                        Entity entity = entityHit.getEntity();
                        boolean handWasEmpty = player.getItemInHand(hand).isEmpty();
                        boolean itemFrameEmpty = (entity instanceof ItemFrame) && ((ItemFrame) entity).getItem().isEmpty();
                        Vec3 relativeHitPos = entityHit.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());
                        if (entity.interactAt(player, relativeHitPos, hand).consumesAction()) {
                            ap.itemUseCooldown = 3;
                            return true;
                        }
                        if (player.interactOn(entity, hand).consumesAction() && !(handWasEmpty && itemFrameEmpty)) {
                            ap.itemUseCooldown = 3;
                            return true;
                        }
                        break;
                    }
                }
                ItemStack handItem = player.getItemInHand(hand);
                if (player.gameMode.useItem(player, player.level(), handItem, hand).consumesAction()) {
                    ap.itemUseCooldown = 3;
                    return true;
                }
            }
            return false;
        }

        public final boolean preventSpectator;

        ActionType(boolean preventSpectator) {
            this.preventSpectator = preventSpectator;
        }

        abstract boolean execute(ServerPlayer player, Action action);

        void inactiveTick(ServerPlayer player, Action action) {
        }

        void stop(ServerPlayer player, Action action) {
            inactiveTick(player, action);
        }
    }

    public static class Action {
        public boolean done = false;
        public final int limit;
        public final int interval;
        public final int offset;
        public final InteractionHand hand;
        public final int hits;
        private int count;
        private int next;
        private final boolean isContinuous;
        private int ticksRemaining;

        private Action(int limit, int interval, int offset, boolean continuous, InteractionHand hand, int ticksRemaining, int hits) {
            this.limit = limit;
            this.interval = interval;
            this.offset = offset;
            this.hand = hand;
            this.hits = hits;
            next = interval + offset;
            isContinuous = continuous;
            this.ticksRemaining = ticksRemaining;
        }

        public static Action once() {
            return new Action(1, 1, 0, false, null, -1, 1);
        }

        public static Action once(int hits) {
            return new Action(1, 1, 0, false, null, -1, Math.max(1, hits));
        }

        public static Action continuous() {
            return new Action(-1, 1, 0, true, null, -1, 1);
        }

        public static Action continuous(int ticks) {
            return new Action(-1, 1, 0, true, null, ticks, 1);
        }

        public static Action interval(int interval) {
            return new Action(-1, interval, 0, false, null, -1, 1);
        }

        public static Action interval(int interval, int ticks) {
            return new Action(-1, interval, 0, false, null, ticks, 1);
        }

        public boolean resetMineCheck() {
            return !isContinuous;
        }

        public boolean isContinuous() {
            return isContinuous;
        }

        public int ticksRemaining() {
            return ticksRemaining;
        }

        Boolean tick(BotPlayerActionPack actionPack, ActionType type) {
            if (ticksRemaining == 0) {
                type.stop(actionPack.player, this);
                done = true;
                return null;
            }
            if (ticksRemaining > 0) ticksRemaining--;

            next--;
            Boolean cancel = null;
            if (next <= 0) {
                if (interval == 1 && !isContinuous) {
                    if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                        type.inactiveTick(actionPack.player, this);
                    }
                }

                if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                    cancel = type.execute(actionPack.player, this);
                }
                count++;
                if (count == limit) {
                    type.stop(actionPack.player, null);
                    done = true;
                    return cancel;
                }
                next = interval;
            } else {
                if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                    type.inactiveTick(actionPack.player, this);
                }
            }
            return cancel;
        }

        @SuppressWarnings("SameParameterValue")
        void retry(BotPlayerActionPack actionPack, ActionType type) {
            if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                type.execute(actionPack.player, this);
            }
            count++;
            if (count == limit) {
                type.stop(actionPack.player, null);
                done = true;
            }
        }
    }
}

