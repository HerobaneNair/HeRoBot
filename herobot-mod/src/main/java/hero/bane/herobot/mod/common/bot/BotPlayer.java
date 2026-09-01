package hero.bane.herobot.mod.common.bot;

import hero.bane.herobot.common.ping.PingDelayOptions;
import hero.bane.herobot.common.ping.PingDelays;
import com.mojang.authlib.GameProfile;
import hero.bane.herobot.mod.common.HeroBot;
import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.mod.common.bot.connection.BotClientConnection;
import hero.bane.herobot.mod.common.bot.connection.ServerPlayerInterface;
import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import hero.bane.herobot.mod.common.bot.pathing.traversal.BotPathing;
import hero.bane.herobot.mod.common.mixin.LivingEntityAccessor;
import hero.bane.herobot.mod.common.mixin.ServerCommonPacketListenerImplAccessor;
import hero.bane.herobot.mod.common.mixin.ServerPlayerAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("EntityConstructor")
public class BotPlayer extends ServerPlayer {
    private static final Set<String> spawning = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger SPAWN_THREAD_COUNTER = new AtomicInteger();
    private static final ExecutorService SPAWN_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "HeroBot Player Spawner #" + SPAWN_THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    public int ping = 0;

    public void applyPing() {
        if (this.connection == null) return;
        ((ServerCommonPacketListenerImplAccessor) this.connection).setLatency(this.ping);
        MinecraftServer server = this.level().getServer();
        if (server == null) return;
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, this));
    }

    public void setPing(int value) {
        this.ping = value;
        applyPing();
    }

    private record DelayedKnockback(long tick, double strength, double x, double z, double horizontalScale) {
    }

    private final List<DelayedKnockback> pendingKnockbacks = new ArrayList<>();

    private record DelayedExplosionKB(long tick, Vec3 explosionKB) {
    }

    private final List<DelayedExplosionKB> pendingExplosionKB = new ArrayList<>();

    private long shieldDisabledTick = -1;

    public Runnable fixStartingPosition = () -> {
    };
    public boolean isAShadow;

    public Vec3 spawnPos;
    public double spawnYaw;

    private boolean inventoryScreenOpen;

    private BotPathing pathFollower;
    private final PathSettings pathSettings = new PathSettings();

    private String assignedScriptName;

    private int selectedTradeIndex = -1;

    public int getSelectedTradeIndex() {
        return selectedTradeIndex;
    }

    public void setSelectedTradeIndex(int index) {
        this.selectedTradeIndex = index;
    }

    public String getAssignedScriptName() {
        return assignedScriptName;
    }

    public void setAssignedScriptName(String name) {
        this.assignedScriptName = name;
    }

    public PathSettings getPathSettings() {
        return pathSettings;
    }

    public void setPathFollower(BotPathing follower) {
        if (this.pathFollower != null) {
            this.pathFollower.stop();
        }
        this.pathFollower = follower;
    }

    public BotPathing getPathFollower() {
        return pathFollower;
    }

    public void clearPathFollower() {
        if (this.pathFollower != null) {
            this.pathFollower.stop();
            this.pathFollower = null;
        }
    }

    public boolean isScreenOpen() {
        return inventoryScreenOpen || containerMenu != inventoryMenu;
    }

    public boolean isContainerOpen() {
        return containerMenu != inventoryMenu;
    }

    public void openInventoryScreen() {
        this.inventoryScreenOpen = true;
    }

    public void closeScreen() {
        if (containerMenu != inventoryMenu) {
            this.closeContainer();
        }
        this.inventoryScreenOpen = false;
        this.selectedTradeIndex = -1;
    }

    public net.minecraft.world.inventory.AbstractContainerMenu getActiveMenu() {
        if (containerMenu != inventoryMenu) return containerMenu;
        if (inventoryScreenOpen) return inventoryMenu;
        return null;
    }

    public static int createFake(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying, boolean bypassWhitelist) {
        ServerLevel worldIn = server.getLevel(dimensionId);
        if (worldIn == null) {
            return 0;
        }
        if (!spawning.add(username)) {
            return 0;
        }

        try {
            SPAWN_EXECUTOR.execute(() -> resolveAndPlace(username, server, worldIn, pos, yaw, pitch, dimensionId, gamemode, flying, bypassWhitelist));
        } catch (RejectedExecutionException e) {
            spawning.remove(username);
            return 0;
        }
        return 1;
    }

    private static void resolveAndPlace(String username, MinecraftServer server, ServerLevel worldIn, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying, boolean bypassWhitelist) {
        boolean scheduled = false;
        try {
            UUID uuid = resolveId(server, username);
            if (uuid == null) {
                return;
            }

            GameProfile fallback = new GameProfile(uuid, username);
            fetchGameProfile(server, uuid).whenCompleteAsync((p, t) -> {
                try {
                    if (t != null) {
                        HeroBot.LOGGER.error("Failed to fetch profile for bot '{}'", username, t);
                        return;
                    }
                    GameProfile current = p.name().isEmpty() ? fallback : p;
                    placeBot(server, worldIn, current, pos, yaw, pitch, dimensionId, gamemode, flying, bypassWhitelist);
                } catch (Throwable e) {
                    HeroBot.LOGGER.error("Failed to spawn bot '{}'", username, e);
                } finally {
                    spawning.remove(username);
                }
            }, server);
            scheduled = true;
        } catch (Throwable t) {
            HeroBot.LOGGER.error("Failed to resolve profile for bot '{}'", username, t);
        } finally {
            if (!scheduled) {
                spawning.remove(username);
            }
        }
    }

    static UUID resolveId(MinecraftServer server, String username) {
        server.services().nameToIdCache().resolveOfflineUsers(false);
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, username);
        if (uuid == null && HeroBotSettings.allowSpawningOfflinePlayers) {
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(username);
        }
        return uuid;
    }

    private static void placeBot(MinecraftServer server, ServerLevel worldIn, GameProfile profile, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying, boolean bypassWhitelist) {
        PlayerList playerList = server.getPlayerList();
        if (playerList.getPlayerByName(profile.name()) != null) {
            return;
        }

        NameAndId nameAndId = server.services().nameToIdCache().get(profile.id()).orElse(null);
        if (nameAndId != null) {
            if (playerList.getBans().isBanned(nameAndId)) {
                return;
            }
            if (!bypassWhitelist && playerList.isUsingWhitelist() && !playerList.isWhiteListed(nameAndId)) {
                return;
            }
        }

        BotPlayer instance = new BotPlayer(server, worldIn, profile, ClientInformation.createDefault(), false);
        instance.fixStartingPosition = () -> instance.snapTo(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);
        playerList.placeNewPlayer(new BotClientConnection(PacketFlow.SERVERBOUND), instance, new CommonListenerCookie(profile, 0, instance.clientInformation(), false));
        loadPlayerData(instance);
        instance.stopRiding();
        instance.teleportTo(worldIn, pos.x, pos.y, pos.z, Set.of(), (float) yaw, (float) pitch, true);
        instance.setHealth(20.0F);
        instance.unsetRemoved();
        Objects.requireNonNull(instance.getAttribute(Attributes.STEP_HEIGHT)).setBaseValue(0.6F);
        instance.gameMode.changeGameModeForPlayer(gamemode);
        instance.spawnPos = pos;
        instance.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(
                                dimensionId,
                                BlockPos.containing(pos),
                                (float) yaw,
                                (float) pitch
                        ),
                        true
                ),
                false
        );
        instance.applyPing();
        instance.spawnYaw = yaw;
        instance.setYRot((float) yaw);
        instance.setXRot((float) pitch);
        instance.setYHeadRot((float) yaw);
        instance.yRotO = (float) yaw;
        playerList.broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), dimensionId);
        playerList.broadcastAll(ClientboundEntityPositionSyncPacket.of(instance), dimensionId);
        instance.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);
        instance.getAbilities().flying = flying;
        instance.broadcastTabListState();
        BotRegistry.fireSpawn(instance);
    }

    private static CompletableFuture<GameProfile> fetchGameProfile(MinecraftServer server, final UUID name) {
        final ResolvableProfile resolvableProfile = ResolvableProfile.createUnresolved(name);
        return resolvableProfile.resolveProfile(server.services().profileResolver());
    }

    private static void loadPlayerData(BotPlayer player) {
        player.level().getServer().getPlayerList()
                .loadPlayerData(player.nameAndId())
                .map(tag -> TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        player.registryAccess(),
                        tag
                ))
                .ifPresent(valueInput -> {
                    player.load(valueInput);
                    player.loadAndSpawnEnderPearls(valueInput);
                    player.loadAndSpawnParentVehicle(valueInput);
                });
    }

    public void copycat(ServerPlayer otherPlayer) {
        if (!(otherPlayer instanceof ServerPlayerInterface src)) {
            return;
        }

        if (!(this instanceof ServerPlayerInterface dst)) {
            return;
        }

        dst.getActionPack().copyFrom(src.getActionPack());

        this.getInventory().clearContent();
        for (int i = 0; i < otherPlayer.getInventory().getContainerSize(); i++) {
            this.getInventory().setItem(i, otherPlayer.getInventory().getItem(i).copy());
        }

        this.inventoryMenu.sendAllDataToRemote();
    }

    public static BotPlayer respawnFake(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation cli) {
        return new BotPlayer(server, level, profile, cli, false);
    }

    public static boolean isSpawningPlayer(String username) {
        return spawning.contains(username);
    }

    private BotPlayer(MinecraftServer server, ServerLevel worldIn, GameProfile profile, ClientInformation cli, boolean shadow) {
        super(server, worldIn, profile, cli);
        this.isAShadow = shadow;
    }

    public static final byte SKIN_CAPE = 0x01;
    public static final byte SKIN_JACKET = 0x02;
    public static final byte SKIN_LEFT_SLEEVE = 0x04;
    public static final byte SKIN_RIGHT_SLEEVE = 0x08;
    public static final byte SKIN_LEFT_PANT = 0x10;
    public static final byte SKIN_RIGHT_PANT = 0x20;
    public static final byte SKIN_HAT = 0x40;

    public void toggleSkinPart(byte mask) {
        byte current = this.entityData.get(DATA_PLAYER_MODE_CUSTOMISATION);
        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) (current ^ mask));
        broadcastTabListState();
    }

    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> TAB_LIST_STATE = EnumSet.of(
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT,
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);

    public void broadcastTabListState() {
        MinecraftServer server = this.level().getServer();
        if (server == null) return;
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(TAB_LIST_STATE, List.of(this)));
    }

    public boolean isSkinPartEnabled(byte mask) {
        return (this.entityData.get(DATA_PLAYER_MODE_CUSTOMISATION) & mask) != 0;
    }

    public CompletableFuture<Boolean> forceLoadSkin() {
        return SkinForcer.forceLoadSkin(this, this.getUUID());
    }

    public CompletableFuture<Boolean> forceLoadSkin(String name) {
        return SkinForcer.forceLoadSkin(this, name);
    }

    public CompletableFuture<Boolean> forceLoadSkin(UUID skinUUID) {
        return SkinForcer.forceLoadSkin(this, skinUUID);
    }

    public void setMainHand(HumanoidArm arm) {
        this.entityData.set(DATA_PLAYER_MAIN_HAND, arm);
    }

    @Override
    public @NonNull Vec3 getLastClientMoveIntent() {
        BotPlayerActionPack ap = ((ServerPlayerInterface) this).getActionPack();
        float forward = ap.getForward();
        float strafing = ap.getStrafing();
        if (forward == 0 && strafing == 0) return Vec3.ZERO;
        float yawRad = getYRot() * (float) (Math.PI / 180.0);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        return new Vec3(strafing * cos - forward * sin, 0, forward * cos + strafing * sin);
    }

    @Override
    public void move(@NonNull MoverType moverType, @NonNull Vec3 vec3) {
        double oldX = this.getX();
        double oldZ = this.getZ();
        super.move(moverType, vec3);
        float movedX = (float) (this.getX() - oldX);
        float movedZ = (float) (this.getZ() - oldZ);
        ((ServerPlayerInterface) this).getActionPack().updateAutoJump(movedX, movedZ);
    }

    @Override
    public void onEquipItem(final @NonNull EquipmentSlot slot, final @NonNull ItemStack previous, final @NonNull ItemStack stack) {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    @Override
    public void kill(@NonNull ServerLevel level) {
        kill(Component.literal("Killed"));
    }

    public void botPlayerDisconnect(Component reason) {
        this.level().getServer().schedule(new TickTask(this.level().getServer().getTickCount(), () ->
                this.connection.onDisconnect(new DisconnectionDetails(reason))
        ));
    }

    public void botPlayerDisconnectNow(Component reason) {
        this.connection.onDisconnect(new DisconnectionDetails(reason));
    }

    public void kill(Component reason) {
        shakeOff();

        if (reason.getContents() instanceof TranslatableContents text
                && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.connection.onDisconnect(new DisconnectionDetails(reason));
            return;
        }
        this.hurtServer(this.level(), this.level().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
    }

    @Override
    public void tick() {
        if (this.level().getServer().getTickCount() % 10 == 0) {
            this.connection.resetPosition();
            this.level().getChunkSource().move(this);
            if (this.connection.latency() != this.ping) applyPing();
        }
        try {
            double startX = this.getX();
            double startY = this.getY();
            double startZ = this.getZ();

            super.tick();

            if (!this.noPhysics) {
                this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
            }

            this.doTick();

            processPendingKBs();

            if (pathFollower != null) {
                pathFollower.tick();
                if (pathFollower.isDone()) {
                    pathFollower = null;
                }
            }

            Vec3 movement = new Vec3(this.getX() - startX, this.getY() - startY, this.getZ() - startZ);
            this.setKnownMovement(movement);
            if (movement.lengthSqr() > 0.00001F) {
                this.resetLastActionTime();
            }
        } catch (NullPointerException ignored) {
        }
    }

    private void moveTowardsClosestSpace(double x, double z) {
        BlockPos pos = BlockPos.containing(x, this.getY(), z);
        if (this.suffocatesAt(pos)) {
            double xd = x - pos.getX();
            double zd = z - pos.getZ();
            Direction dir = null;
            double closest = Double.MAX_VALUE;
            for (Direction direction : new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
                double axisDistance = direction.getAxis().choose(xd, 0.0, zd);
                double distanceToEdge = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - axisDistance : axisDistance;
                if (distanceToEdge < closest && !this.suffocatesAt(pos.relative(direction))) {
                    closest = distanceToEdge;
                    dir = direction;
                }
            }
            if (dir != null) {
                Vec3 oldMovement = this.getDeltaMovement();
                if (dir.getAxis() == Direction.Axis.X) {
                    this.setDeltaMovement(0.1 * dir.getStepX(), oldMovement.y, oldMovement.z);
                } else {
                    this.setDeltaMovement(oldMovement.x, oldMovement.y, 0.1 * dir.getStepZ());
                }
            }
        }
    }

    private boolean suffocatesAt(final BlockPos pos) {
        AABB boundingBox = this.getBoundingBox();
        AABB testArea = new AABB(pos.getX(), boundingBox.minY, pos.getZ(), pos.getX() + 1.0, boundingBox.maxY, pos.getZ() + 1.0).deflate(1.0E-7);
        return this.level().collidesWithSuffocatingBlock(this, testArea);
    }

    private void processPendingKBs() {
        long currentTick = this.level().getServer().getTickCount();
        if (!pendingKnockbacks.isEmpty()) {
            pendingKnockbacks.removeIf(kb -> {
                if (currentTick >= kb.tick()) {
                    applyKnockbackWithScale(kb.strength(), kb.x(), kb.z(), kb.horizontalScale());
                    return true;
                }
                return false;
            });
        }
        if (!pendingExplosionKB.isEmpty()) {
            pendingExplosionKB.removeIf(dp -> {
                if (currentTick >= dp.tick()) {
                    super.push(dp.explosionKB());
                    return true;
                }
                return false;
            });
        }
    }

    public void delayedExplosionKB(Vec3 vec3) {
        int delayTicks = knockbackDelayTicks();
        if (delayTicks <= 0) {
            super.push(vec3);
        } else {
            long executeAt = this.level().getServer().getTickCount() + delayTicks;
            pendingExplosionKB.add(new DelayedExplosionKB(executeAt, vec3));
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (this.getAbilities().invulnerable) return;
        scaledKnockback(strength, x, z, 1.0);
    }

    private void scaledKnockback(double strength, double x, double z, double horizontalScale) {
        int delayTicks = knockbackDelayTicks();
        if (delayTicks <= 0) {
            applyKnockbackWithScale(strength, x, z, horizontalScale);
        } else {
            long executeAt = this.level().getServer().getTickCount() + delayTicks;
            pendingKnockbacks.add(new DelayedKnockback(executeAt, strength, x, z, horizontalScale));
        }
        if (pathFollower != null && !pathFollower.isDone()) {
            pathFollower.recalcPath();
        }
    }

    private void applyKnockbackWithScale(double strength, double x, double z, double horizontalScale) {
        if (horizontalScale >= 1.0) {
            super.knockback(strength, x, z);
            return;
        }
        double d = strength * (1.0 - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (d <= 0.0) return;
        this.needsSync = true;
        while (x * x + z * z < (double) 1.0E-5f) {
            x = (this.random.nextDouble() - this.random.nextDouble()) * 0.01;
            z = (this.random.nextDouble() - this.random.nextDouble()) * 0.01;
        }
        Vec3 dir = new Vec3(x, 0.0, z).normalize().scale(d * horizontalScale);
        Vec3 vel = this.getDeltaMovement();
        double verticalVel = HeroBotSettings.kbScaling ? vel.y : 0.0;
        boolean grounded = this.onGround() || !HeroBotSettings.kbScaling;
        this.setDeltaMovement(vel.x / 2.0 - dir.x, grounded ? Math.min(0.4, verticalVel / 2.0 + d) : verticalVel, vel.z / 2.0 - dir.z);
    }

    private int knockbackDelayTicks() {
        return PingDelays.enabled(this.getUUID(), PingDelayOptions.Category.KNOCKBACK) ? delayTicks() : 0;
    }

    public boolean deferByPing(PingDelayOptions.Category category, Runnable action) {
        if (!PingDelays.enabled(this.getUUID(), category)) return false;
        int delay = delayTicks();
        if (delay <= 0) return false;
        ((ServerPlayerInterface) this).getActionPack().scheduleDelayed(delay, action);
        return true;
    }

    public int delayTicks() {
        int pingToTicks = HeroBotSettings.botPingToTicks;
        if (pingToTicks <= 0) return 0;
        int whole = ping / pingToTicks;
        int remainder = ping % pingToTicks;
        if (remainder == 0) return whole;
        return ThreadLocalRandom.current().nextInt(pingToTicks) < remainder ? whole + 1 : whole;
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel serverLevel, @NonNull DamageSource damageSource, float finalDamage) {
        if (
                (this.gameMode.getGameModeForPlayer() == GameType.CREATIVE || this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) &&
                        (damageSource != this.level().damageSources().fellOutOfWorld())
        ) {
            return false;
        }
        if (damageSource.getDirectEntity() instanceof ThrowableItemProjectile
                && !damageSource.is(DamageTypes.MAGIC)
                && !damageSource.is(DamageTypes.INDIRECT_MAGIC)) {
            return false;
        }
        if (this.isInvulnerableTo(serverLevel, damageSource)) {
            return false;
        } else if (this.isDeadOrDying()) {
            return false;
        } else if (damageSource.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        } else if (damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
            return super.hurtServer(serverLevel, damageSource, finalDamage);
        } else {
            if (this.isSleeping()) {
                this.stopSleeping();
            }

            this.noActionTime = 0;
            if (finalDamage < 0.0F) {
                finalDamage = 0.0F;
            }

            float originalDamage = finalDamage;
            float blockedDamage = this.applyItemBlocking(serverLevel, damageSource, finalDamage);
            finalDamage -= blockedDamage;
            boolean blocked = blockedDamage > 0.0F;

            if (blocked && HeroBotSettings.shieldStunning) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(this, damageSource, originalDamage, finalDamage, true);
                if (blockedDamage < Float.MAX_VALUE) {
                    this.awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(blockedDamage * 10.0F));
                }
                Entity attackingEntity = damageSource.getEntity();
                if (attackingEntity instanceof ServerPlayer serverPlayer) {
                    CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverPlayer, this, damageSource, originalDamage, finalDamage, true);
                }

                BlocksAttacks blocksAttacks = this.getUseItem().get(DataComponents.BLOCKS_ATTACKS);
                if (blocksAttacks != null) {
                    blocksAttacks.onBlocked(serverLevel, this);
                }
                return false;
            }

            if (damageSource.is(DamageTypeTags.IS_FREEZING) && this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
                finalDamage *= 5.0F;
            }

            if (damageSource.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                this.hurtHelmet(damageSource, finalDamage);
                finalDamage *= 0.75F;
            }

            if (Float.isNaN(finalDamage) || Float.isInfinite(finalDamage)) {
                finalDamage = Float.MAX_VALUE;
            }

            boolean cleanHit = true;
            if ((float) this.invulnerableTime > (float) HeroBotSettings.damageTicks && !damageSource.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                if (finalDamage <= this.lastHurt) {
                    return false;
                }

                this.actuallyHurt(serverLevel, damageSource, finalDamage - this.lastHurt);
                this.lastHurt = finalDamage;
                cleanHit = false;
            } else {
                this.lastHurt = finalDamage;
                this.invulnerableTime = HeroBotSettings.damageInvulnerableTicks();
                this.actuallyHurt(serverLevel, damageSource, finalDamage);
                if (!blocked) {
                    this.hurtDuration = HeroBotSettings.damageTicks;
                    this.hurtTime = this.hurtDuration;
                }
            }

            this.resolveMobResponsibleForDamage(damageSource);
            this.resolvePlayerResponsibleForDamage(damageSource);
            if (cleanHit) {
                if (blocked) {
                    BlocksAttacks blocksAttacks = this.getUseItem().get(DataComponents.BLOCKS_ATTACKS);
                    if (blocksAttacks != null) {
                        blocksAttacks.onBlocked(serverLevel, this);
                    }
                } else {
                    serverLevel.broadcastDamageEvent(this, damageSource);
                }

                if (!blocked && !damageSource.is(DamageTypeTags.NO_IMPACT)) {
                    this.markHurt();
                }

                if (!damageSource.is(DamageTypeTags.NO_KNOCKBACK)) {
                    double kb_x = 0.0d;
                    double kb_z = 0.0d;
                    Entity directEntity = damageSource.getDirectEntity();
                    if (directEntity instanceof Projectile projectile) {
                        DoubleDoubleImmutablePair kbVector = projectile.calculateHorizontalHurtKnockbackDirection(this, damageSource);
                        kb_x = -kbVector.leftDouble();
                        kb_z = -kbVector.rightDouble();
                    } else if (damageSource.getSourcePosition() != null) {
                        kb_x = damageSource.getSourcePosition().x() - this.getX();
                        kb_z = damageSource.getSourcePosition().z() - this.getZ();
                    }

                    if (!blocked) {
                        long currentTick = this.level().getServer().getTickCount();
                        boolean recentShieldDisable = HeroBotSettings.shieldStunning
                                && HeroBotSettings.shieldStunningWindow > 0
                                && shieldDisabledTick >= 0
                                && (currentTick - shieldDisabledTick) <= HeroBotSettings.shieldStunningWindow;
                        double horizontalScale = recentShieldDisable ? 0.4d : 1.0d;
                        this.scaledKnockback(0.4d, kb_x, kb_z, horizontalScale);
                        this.indicateDamage(kb_x, kb_z);
                    }
                }
            }

            if (this.isDeadOrDying()) {
                try {
                    if (!((LivingEntityAccessor) this).invokeCheckTotemDeathProtection(damageSource)) {
                        if (cleanHit) {
                            this.makeSound(this.getDeathSound());
                            ((LivingEntityAccessor) this).invokePlaySecondaryHurtSound(damageSource);
                        }

                        this.die(damageSource);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            } else if (cleanHit) {
                this.playHurtSound(damageSource);
                ((LivingEntityAccessor) this).invokePlaySecondaryHurtSound(damageSource);
            }

            ((LivingEntityAccessor) this).setLastDamageSource(damageSource);
            ((LivingEntityAccessor) this).setLastDamageStamp(this.level().getGameTime());

            for (MobEffectInstance mobEffectInstance : this.getActiveEffects()) {
                mobEffectInstance.onMobHurt(serverLevel, this, damageSource, finalDamage);
            }

            ServerPlayer serverPlayer1 = this;
            CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(serverPlayer1, damageSource, originalDamage, finalDamage, blocked);

            Entity attackingEntity = damageSource.getEntity();
            if (attackingEntity instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverPlayer, this, damageSource, originalDamage, finalDamage, blocked);
            }

            if (pathFollower != null) {
                pathFollower.requestRecalc();
            }

            if (!blocked) {
                hero.bane.herobot.mod.common.ai.AiScriptRegistry.markDamaged(this);
            }

            return !blocked;
        }
    }

    private void shakeOff() {
        if (getVehicle() instanceof Player) stopRiding();
        for (Entity passenger : getIndirectPassengers()) {
            if (passenger instanceof Player) passenger.stopRiding();
        }
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        shakeOff();
        super.die(cause);

        MinecraftServer server = ((ServerPlayerAccessor) this).getServer();

        if (HeroBotSettings.botLeaveOnDeath) {
            botPlayerDisconnect(Component.literal("Died"));
            return;
        }

        server.execute(() -> {
            this.connection.handleClientCommand(
                    new ServerboundClientCommandPacket(
                            ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                    )
            );

            ServerPlayer p = this.connection.player;
            if (p instanceof BotPlayer bot) {
                bot.setHealth(20.0F);
                bot.foodData = new FoodData();
                bot.setExperienceLevels(0);
                bot.setExperiencePoints(0);
            }
        });
    }

    @Override
    public @NonNull String getIpAddress() {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        return HeroBotSettings.allowListingBotPlayers;
    }

    @Override
    public boolean isInvulnerableTo(@NonNull ServerLevel serverLevel, @NonNull DamageSource damageSource) {
        return super.isInvulnerableTo(serverLevel, damageSource)
                || this.isChangingDimension() && !damageSource.is(DamageTypes.ENDER_PEARL);
    }

    @Override
    public ServerPlayer teleport(@NonNull TeleportTransition serverLevel) {
        super.teleport(serverLevel);
        if (wonGame) {
            ServerboundClientCommandPacket p = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
            connection.handleClientCommand(p);
        }

        if (connection.player.isChangingDimension()) {
            connection.player.hasChangedDimension();
        }
        return connection.player;
    }

    @Override
    public void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("botPlayerPing", this.ping);
    }

    @Override
    public void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.ping = input.getIntOr("botPlayerPing", 0);
    }

    @Override
    protected void blockUsingItem(@NonNull ServerLevel serverLevel, LivingEntity livingEntity) {
        ItemStack itemStack = this.getItemBlockingWith();
        BlocksAttacks blocksAttacks = itemStack != null ? itemStack.get(DataComponents.BLOCKS_ATTACKS) : null;
        float f = livingEntity.getSecondsToDisableBlocking();
        if (f > 0.0F && blocksAttacks != null) {
            blocksAttacks.disable(serverLevel, this, f, itemStack);
            this.shieldDisabledTick = serverLevel.getServer().getTickCount();
        }
    }
}
