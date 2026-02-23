package hero.bane.herobot.fakeplayer;

import hero.bane.herobot.fakeplayer.connection.ServerPlayerInterface;
import hero.bane.herobot.mixin.LivingEntityAccessor;
import hero.bane.herobot.util.RayTrace;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class FakePlayerActionPack {
    public final ServerPlayer player;

    private final Map<ActionType, Action> actions = new EnumMap<>(ActionType.class);

    private BlockPos currentBlock;
    private int blockHitDelay;
    private boolean isHittingBlock;
    private float curBlockDamageMP;

    private boolean sneaking;
    private boolean sprinting;
    private float forward;
    private float strafing;

    private int itemUseCooldown;

    private double fakeFallDistance;
    private double lastY;

    public FakePlayerActionPack(ServerPlayer playerIn) {
        player = playerIn;
        lastY = player.getY();
        stopAll();
    }

    public void copyFrom(FakePlayerActionPack other) {
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

    public FakePlayerActionPack start(ActionType type, Action action) {
        if (action.isContinuous) {
            Action curent = actions.get(type);
            if (curent != null) return this;
        }

        Action previous = actions.remove(type);
        if (previous != null) type.stop(player, previous);
        actions.put(type, action);
        type.start(player, action);
        return this;
    }

    public FakePlayerActionPack stop(ActionType type) {
        Action action = actions.remove(type);
        if (action != null) {
            type.stop(player, action);
        }
        if (type == ActionType.USE) {
            itemUseCooldown = 0;
            player.releaseUsingItem();
        }
        return this;
    }

    public FakePlayerActionPack setSneaking(boolean doSneak) {
        sneaking = doSneak;
        player.setShiftKeyDown(doSneak);
        return this;
    }

    public FakePlayerActionPack setSprinting(boolean doSprint) {
        sprinting = doSprint;
        player.setSprinting(doSprint);
        return this;
    }

    public FakePlayerActionPack setForward(float value) {
        forward = value;
        return this;
    }

    public FakePlayerActionPack setStrafing(float value) {
        strafing = value;
        return this;
    }

    public FakePlayerActionPack look(Direction direction) {
        return switch (direction) {
            case NORTH -> look(180, 0);
            case SOUTH -> look(0, 0);
            case EAST -> look(-90, 0);
            case WEST -> look(90, 0);
            case UP -> look(player.getYRot(), -90);
            case DOWN -> look(player.getYRot(), 90);
        };
    }

    public FakePlayerActionPack look(Vec2 rotation) {
        return look(rotation.y, rotation.x);
    }

    public FakePlayerActionPack look(float yaw, float pitch) {
        player.setYRot(yaw % 360);
        player.setXRot(Mth.clamp(pitch, -90, 90));
        return this;
    }

    public FakePlayerActionPack lookAt(Vec3 position) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, position);
        return this;
    }

    public FakePlayerActionPack turn(float yaw, float pitch) {
        return look(player.getYRot() + yaw, player.getXRot() + pitch);
    }

    public FakePlayerActionPack turn(Vec2 rotation) {
        return turn(rotation.x, rotation.y);
    }

    public FakePlayerActionPack stopMovement() {
        setSneaking(false);
        setSprinting(false);
        forward = 0.0F;
        strafing = 0.0F;
        return this;
    }

    public FakePlayerActionPack stopAll() {
        for (ActionType type : actions.keySet()) type.stop(player, actions.get(type));
        actions.clear();
        return stopMovement();
    }

    public void onUpdate() {

        double y = player.getY();
        double dy = y - lastY;

        if (player.onGround()
                || player.isInWater()
                || player.onClimbable()
                || player.isPassenger()) {
            fakeFallDistance = 0;
        } else if (player.isFallFlying()) {
            Vec3 motion = player.getDeltaMovement();
            Vec3 look = player.getLookAngle();

            float f = player.getXRot() * ((float) Math.PI / 180F);
            double d = Math.sqrt(look.x * look.x + look.z * look.z);
            double e = motion.horizontalDistance();
            double g = ((LivingEntityAccessor) player).invokeGetEffectiveGravity();
            double h = Mth.square(Math.cos(f));

            double predictedY = motion.y + g * (-1.0 + h * 0.75);

            if (predictedY < 0.0 && d > 0.0) {
                double i = predictedY * -0.1 * h;
                predictedY += i;
            }

            if (f < 0.0F && d > 0.0) {
                double i = e * (-Mth.sin(f)) * 0.04;
                predictedY += i * 3.2;
            }

            if (predictedY < 0) {
                fakeFallDistance -= predictedY;
            } else {
                fakeFallDistance = Math.max(0, fakeFallDistance - predictedY);
            }
        } else if (dy < 0) {
            fakeFallDistance -= dy;
        } else {
            fakeFallDistance = 0;
        }

        lastY = y;
        player.fallDistance = (float) fakeFallDistance;

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

        float vel = sneaking ? 0.3F : 1.0F;
        vel *= (player.isUsingItem() &&
                !player.getMainHandItem().has(DataComponents.KINETIC_WEAPON))
                ? 0.20F : 1.0F;

        if (forward != 0.0F || player instanceof FakePlayer) {
            player.zza = forward * vel;
        }
        if (strafing != 0.0F || player instanceof FakePlayer) {
            player.xxa = strafing * vel;
        }
    }

    static HitResult getTarget(ServerPlayer player) {
        double blockReach = player.gameMode.isCreative() ? 5 : 4.5;
        double entityReach = player.gameMode.isCreative() ? 5 : 3;

        HitResult hit = RayTrace.rayTrace(player, 1, blockReach, false);

        if (hit.getType() == HitResult.Type.BLOCK) return hit;
        return RayTrace.rayTrace(player, 1, entityReach, false);
    }

    static HitResult getSpearTarget(ServerPlayer player) {
        double blockReach = player.gameMode.isCreative() ? 5 : 4.5;
        double minEntityReach = 2.0;
        double maxEntityReach = player.gameMode.isCreative() ? 6.5 : 4.5;

        HitResult blockHit = RayTrace.rayTrace(player, 1, blockReach, false);
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);

        Vec3 start = eye.add(look.scale(minEntityReach));
        Vec3 end = eye.add(look.scale(maxEntityReach));

        EntityHitResult entityHit = RayTrace.rayTraceEntities(
                player,
                start,
                end,
                player.getBoundingBox()
                        .expandTowards(look.scale(maxEntityReach))
                        .inflate(1),
                e -> !e.isSpectator() && e.isPickable(),
                maxEntityReach * maxEntityReach
        );

        return entityHit == null
                ? BlockHitResult.miss(end, player.getDirection(), BlockPos.containing(end))
                : entityHit;
    }

    public void setSlot(int slot) {
        player.getInventory().setSelectedSlot(slot - 1);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot - 1));
    }

    private static void applyKineticDamage(ServerPlayer player,
                                           ItemStack stack,
                                           KineticWeapon kinetic) {

        int remaining = player.getUseItemRemainingTicks();
        int j = stack.getUseDuration(player) - remaining;

        if (j < kinetic.delayTicks()) {
            return;
        }

        j -= kinetic.delayTicks();

        Vec3 look = player.getLookAngle();
        double attackerSpeed = look.dot(KineticWeapon.getMotion(player));
        float speedScale = 1.0F;

        double baseAttack = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);

        EquipmentSlot slot =
                player.getUsedItemHand() == InteractionHand.OFF_HAND
                        ? EquipmentSlot.OFFHAND
                        : EquipmentSlot.MAINHAND;

        boolean hitAny = false;

        var result = ProjectileUtil.getHitEntitiesAlong(
                player,
                player.entityAttackRange(),
                e -> PiercingWeapon.canHitEntity(player, e),
                ClipContext.Block.COLLIDER
        );

        if (result.right().isEmpty()) {
            return;
        }

        for (EntityHitResult ehr : result.right().get()) {
            Entity target = ehr.getEntity();

            if (target instanceof EnderDragonPart part) {
                target = part.parentMob;
            }

            if (player.wasRecentlyStabbed(target, kinetic.contactCooldownTicks())) {
                continue;
            }

            player.rememberStabbedEntity(target);

            double targetSpeed = look.dot(KineticWeapon.getMotion(target));
            double relative = Math.max(0.0, attackerSpeed - targetSpeed);

            boolean doDismount =
                    kinetic.dismountConditions().isPresent() &&
                            kinetic.dismountConditions().get()
                                    .test(j, attackerSpeed, relative, speedScale);

            boolean doKnockback =
                    kinetic.knockbackConditions().isPresent() &&
                            kinetic.knockbackConditions().get()
                                    .test(j, attackerSpeed, relative, speedScale);

            boolean doDamage =
                    kinetic.damageConditions().isPresent() &&
                            kinetic.damageConditions().get()
                                    .test(j, attackerSpeed, relative, speedScale);

            if (!doDismount && !doKnockback && !doDamage) {
                continue;
            }

            float finalDamage =
                    (float) baseAttack +
                            (float) Mth.floor(relative * kinetic.damageMultiplier());

            boolean applied =
                    player.stabAttack(slot, target, finalDamage, doDamage, doKnockback, doDismount);

            hitAny |= applied;
        }

        if (hitAny) {
            player.level().broadcastEntityEvent(player, (byte) 2);

            CriteriaTriggers.SPEAR_MOBS_TRIGGER.trigger(
                    player,
                    player.stabbedEntities(e -> e instanceof LivingEntity)
            );
        }
    }

    public enum ActionType {
        USE(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                FakePlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                if (ap.itemUseCooldown > 0) {
                    ap.itemUseCooldown--;
                    return true;
                }
                if (player.isUsingItem()) {
                    return true;
                }
                HitResult hit = getTarget(player);
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
                                    if (success.swingSource() == InteractionResult.SwingSource.SERVER)
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
                            // fix for SS itemframe always returns CONSUME even if no action is performed
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
            @Override
            void inactiveTick(ServerPlayer player, Action action) {
                FakePlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
                ap.itemUseCooldown = 0;
                player.releaseUsingItem();
            }
        },
        ATTACK(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                ItemStack stack = player.getMainHandItem();
                boolean isSpear = stack.has(DataComponents.KINETIC_WEAPON);
                HitResult hit = isSpear ? getSpearTarget(player) : getTarget(player);
                switch (hit.getType()) {
                    case ENTITY: {
                        if (isSpear) {
                            if (player.getAttackStrengthScale(0.5F) >= 1.0F) {
                                player.connection.handlePlayerAction(
                                        new ServerboundPlayerActionPacket(
                                                ServerboundPlayerActionPacket.Action.STAB,
                                                player.blockPosition(),
                                                player.getDirection()
                                        )
                                );
                                player.resetLastActionTime();
                                return true;
                            } else {
                                return false;
                            }
                        }
                        EntityHitResult entityHit = (EntityHitResult) hit;
                        if (!action.isContinuous) {
                            player.fallDistance = (float) ((ServerPlayerInterface) player)
                                    .getActionPack()
                                    .fakeFallDistance;
                            player.attack(entityHit.getEntity());
                            player.swing(InteractionHand.MAIN_HAND);
                        }
                        player.resetAttackStrengthTicker();
                        player.resetLastActionTime();
                        return true;
                    }
                    case BLOCK: {
                        FakePlayerActionPack ap = ((ServerPlayerInterface) player).getActionPack();
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
                if (action.limit == 1) {
                    if (player.onGround()) player.jumpFromGround();

                    if (!player.onGround())
                        player.tryToStartFallFlying();

                } else {
                    if (!player.onGround())
                        player.tryToStartFallFlying();

                    player.setJumping(true);
                }
                return false;
            }

            @Override
            void inactiveTick(ServerPlayer player, Action action) {
                player.setJumping(false);
            }
        },
        DROP_ITEM(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                player.drop(false);

                return false;
            }
        },
        DROP_STACK(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                player.drop(true);

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

        public final boolean preventSpectator;

        ActionType(boolean preventSpectator) {
            this.preventSpectator = preventSpectator;
        }

        void start(ServerPlayer player, Action action) {
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
        private int count;
        private int next;
        private final boolean isContinuous;

        private Action(int limit, int interval, int offset, boolean continuous, InteractionHand hand) {
            this.limit = limit;
            this.interval = interval;
            this.offset = offset;
            this.hand = hand;
            next = interval + offset;
            isContinuous = continuous;
        }

        public static Action once() {
            return new Action(1, 1, 0, false, null);
        }

        public static Action continuous() {
            return new Action(-1, 1, 0, true, null);
        }

        public static Action interval(int interval) {
            return new Action(-1, interval, 0, false, null);
        }

        Boolean tick(FakePlayerActionPack actionPack, ActionType type) {
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

        void retry(FakePlayerActionPack actionPack, ActionType type) {
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

