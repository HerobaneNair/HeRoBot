package hero.bane.herobot.paper.bot;

import hero.bane.herobot.common.ping.PingDelayOptions;
import hero.bane.herobot.common.ping.PingDelays;
import com.mojang.authlib.GameProfile;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.paper.bot.connection.BotClientConnection;
import hero.bane.herobot.paper.bot.connection.BotPlayerNetHandler;
import hero.bane.herobot.paper.bot.connection.ServerPlayerInterface;
import hero.bane.herobot.paper.bot.pathing.PathSettings;
import hero.bane.herobot.paper.bot.pathing.traversal.BotPathing;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class BotPlayer extends ServerPlayer implements ServerPlayerInterface {

    private static final Field LATENCY_FIELD = findLatencyField();

    private final BotPlayerActionPack actionPack;

    public int ping = 0;

    private static final Set<String> SPAWNING = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger SPAWN_THREAD_COUNTER = new AtomicInteger();
    private static final ExecutorService SPAWN_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "HeroBot Player Spawner #" + SPAWN_THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private BotPathing pathFollower;
    private final PathSettings pathSettings = new PathSettings();

    private String assignedScriptName;
    private int selectedTradeIndex = -1;
    private boolean inventoryScreenOpen;

    public PathSettings getPathSettings() {
        return pathSettings;
    }

    public BotPathing getPathFollower() {
        return pathFollower;
    }

    public void setPathFollower(BotPathing follower) {
        if (this.pathFollower != null) this.pathFollower.stop();
        this.pathFollower = follower;
    }

    public void clearPathFollower() {
        if (this.pathFollower == null) return;
        this.pathFollower.stop();
        this.pathFollower = null;
    }

    public String getAssignedScriptName() {
        return assignedScriptName;
    }

    public void setAssignedScriptName(String name) {
        this.assignedScriptName = name;
    }

    public int getSelectedTradeIndex() {
        return selectedTradeIndex;
    }

    public void setSelectedTradeIndex(int index) {
        this.selectedTradeIndex = index;
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
        if (containerMenu != inventoryMenu) this.closeContainer();
        this.inventoryScreenOpen = false;
        this.selectedTradeIndex = -1;
    }

    public AbstractContainerMenu getActiveMenu() {
        if (containerMenu != inventoryMenu) return containerMenu;
        return inventoryScreenOpen ? inventoryMenu : null;
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
        return SkinForcer.forceLoadSkin(this, this.getGameProfile().name());
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

    public void copycat(ServerPlayer other) {
        if (!(other instanceof ServerPlayerInterface source)) return;

        this.actionPack.copyFrom(source.getActionPack());

        this.getInventory().clearContent();
        for (int i = 0; i < other.getInventory().getContainerSize(); i++) {
            this.getInventory().setItem(i, other.getInventory().getItem(i).copy());
        }
        this.inventoryMenu.sendAllDataToRemote();
    }

    public static boolean isSpawningPlayer(String username) {
        return SPAWNING.contains(username);
    }

    private BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);
        this.actionPack = new BotPlayerActionPack(this);
    }

    @Override
    public BotPlayerActionPack getActionPack() {
        return actionPack;
    }

    private static Field findLatencyField() {
        try {
            Field field = ServerCommonPacketListenerImpl.class.getDeclaredField("latency");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public void applyPing() {
        if (this.connection == null || LATENCY_FIELD == null) return;
        try {
            LATENCY_FIELD.setInt(this.connection, this.ping);
        } catch (IllegalAccessException e) {
            return;
        }
        MinecraftServer server = this.level().getServer();
        if (server == null) return;
        server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, this));
    }

    public void setPing(int value) {
        this.ping = value;
        applyPing();
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

    public static boolean spawn(MinecraftServer server, ServerLevel level, String username,
                                Vec3 pos, float yaw, float pitch, GameType gameType) {
        if (!SPAWNING.add(username)) return false;
        try {
            SPAWN_EXECUTOR.execute(() -> resolveAndPlace(server, level, username, pos, yaw, pitch, gameType));
        } catch (RejectedExecutionException e) {
            SPAWNING.remove(username);
            return false;
        }
        return true;
    }

    private static void resolveAndPlace(MinecraftServer server, ServerLevel level, String username,
                                        Vec3 pos, float yaw, float pitch, GameType gameType) {
        boolean scheduled = false;
        try {
            UUID id = resolveId(server, username);
            if (id == null) return;
            GameProfile fallback = new GameProfile(id, username);

            SkinForcer.skinProperties(server, username)
                    .whenCompleteAsync((properties, error) -> {
                        try {
                            if (error != null) {
                                HeroBot.LOGGER.error("Failed to fetch profile for bot '{}'", username, error);
                            }
                            GameProfile profile = error != null || properties == null || properties.isEmpty()
                                    ? fallback
                                    : new GameProfile(id, username, properties);
                            place(server, level, profile, pos, yaw, pitch, gameType);
                        } catch (Throwable t) {
                            HeroBot.LOGGER.error("Failed to spawn bot '{}'", username, t);
                        } finally {
                            SPAWNING.remove(username);
                        }
                    }, server);
            scheduled = true;
        } catch (Throwable t) {
            HeroBot.LOGGER.error("Failed to resolve profile for bot '{}'", username, t);
        } finally {
            if (!scheduled) SPAWNING.remove(username);
        }
    }

    static UUID resolveId(MinecraftServer server, String username) {
        server.services().nameToIdCache().resolveOfflineUsers(false);
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, username);
        if (uuid == null && HeroBotSettings.allowSpawningOfflinePlayers) {
            server.services().nameToIdCache()
                    .resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(username);
        }
        return uuid;
    }

    private static BotPlayer place(MinecraftServer server, ServerLevel level, GameProfile profile,
                                   Vec3 pos, float yaw, float pitch, GameType gameType) {
        if (server.getPlayerList().getPlayerByName(profile.name()) != null) return null;

        BotPlayer bot = new BotPlayer(server, level, profile, ClientInformation.createDefault());
        bot.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
        bot.setYHeadRot(yaw);

        BotClientConnection connection = new BotClientConnection(PacketFlow.SERVERBOUND);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        connection.setOwner(bot);
        server.getPlayerList().placeNewPlayer(connection, bot, cookie);
        bot.connection = new BotPlayerNetHandler(server, connection, bot, cookie);

        bot.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true);
        bot.setHealth(20.0F);
        bot.gameMode.changeGameModeForPlayer(gameType);
        bot.unsetRemoved();

        bot.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(level.dimension(), BlockPos.containing(pos), yaw, pitch),
                        true
                ),
                false
        );
        bot.applyPing();
        bot.setYRot(yaw);
        bot.setXRot(pitch);
        bot.setYHeadRot(yaw);
        bot.yRotO = yaw;
        bot.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);

        server.getPlayerList().broadcastAll(
                new ClientboundRotateHeadPacket(bot, (byte) (bot.yHeadRot * 256 / 360)), level.dimension());
        server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(bot), level.dimension());
        bot.broadcastTabListState();

        BotRegistry.add(bot);
        return bot;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float finalDamage) {
        GameType mode = this.gameMode.getGameModeForPlayer();
        if ((mode == GameType.CREATIVE || mode == GameType.SPECTATOR)
                && damageSource != this.damageSources().fellOutOfWorld()) {
            return false;
        }
        if (damageSource.getDirectEntity() instanceof ThrowableItemProjectile
                && !damageSource.is(DamageTypes.MAGIC)
                && !damageSource.is(DamageTypes.INDIRECT_MAGIC)) {
            return false;
        }

        boolean hurt = super.hurtServer(serverLevel, damageSource, finalDamage);
        if (hurt) {
            if (pathFollower != null) pathFollower.requestRecalc();
            BotEvents.damaged(this);
        }
        return hurt;
    }

    public void botPlayerDisconnect(Component reason) {
        MinecraftServer server = this.level().getServer();
        if (server == null) return;
        server.schedule(new TickTask(server.getTickCount(),
                () -> this.connection.onDisconnect(new DisconnectionDetails(reason))));
    }

    @Override
    public void die(DamageSource cause) {
        shakeOff();
        clearPathFollower();
        super.die(cause);
        BotEvents.died(this);

        if (HeroBotSettings.botLeaveOnDeath) {
            botPlayerDisconnect(Component.literal("Died"));
            return;
        }

        MinecraftServer server = this.level().getServer();
        if (server == null) return;
        server.execute(this::performRespawn);
    }

    private void shakeOff() {
        if (getVehicle() instanceof Player) stopRiding();
        for (Entity passenger : getIndirectPassengers()) {
            if (passenger instanceof Player) passenger.stopRiding();
        }
    }

    private void performRespawn() {
        if (BotRegistry.get(this.getGameProfile().name()) != this) return;
        if (this.connection.isDisconnected()) return;

        this.connection.handleClientCommand(
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));

        if (!(this.connection.player instanceof BotPlayer bot)) return;

        bot.setHealth(bot.getMaxHealth());
        bot.foodData = new FoodData();
        bot.setExperienceLevels(0);
        bot.setExperiencePoints(0);
        bot.setDeltaMovement(Vec3.ZERO);
        bot.actionPack.stopAll();
        bot.connection.resetPosition();
        bot.level().getChunkSource().move(bot);
        BotEvents.respawned(bot);
    }

    @Override
    public void kill(ServerLevel serverLevel) {
        this.hurtServer(serverLevel, this.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel serverLevel, DamageSource damageSource) {
        return super.isInvulnerableTo(serverLevel, damageSource)
                || this.isChangingDimension() && !damageSource.is(DamageTypes.ENDER_PEARL);
    }

    @Override
    public ServerPlayer teleport(TeleportTransition transition) {
        ServerPlayer teleported = super.teleport(transition);
        if (this.wonGame) {
            this.connection.handleClientCommand(
                    new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
        if (teleported != null && teleported.isChangingDimension()) {
            teleported.hasChangedDimension();
        }
        return teleported;
    }

    @Override
    public void onEquipItem(EquipmentSlot slot, ItemStack previous, ItemStack stack) {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    @Override
    public String getIpAddress() {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("botPlayerPing", this.ping);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.ping = input.getIntOr("botPlayerPing", 0);
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

            actionPack.onUpdate();
            super.tick();

            if (!this.noPhysics) {
                this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
            }

            this.doTick();

            if (pathFollower != null) {
                pathFollower.tick();
                if (pathFollower.isDone()) pathFollower = null;
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
        if (!this.suffocatesAt(pos)) return;

        double xd = x - pos.getX();
        double zd = z - pos.getZ();
        Direction dir = null;
        double closest = Double.MAX_VALUE;
        for (Direction direction : new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
            double axisDistance = direction.getAxis().choose(xd, 0.0, zd);
            double distanceToEdge = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? 1.0 - axisDistance
                    : axisDistance;
            if (distanceToEdge < closest && !this.suffocatesAt(pos.relative(direction))) {
                closest = distanceToEdge;
                dir = direction;
            }
        }
        if (dir == null) return;

        Vec3 oldMovement = this.getDeltaMovement();
        if (dir.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement(0.1 * dir.getStepX(), oldMovement.y, oldMovement.z);
        } else {
            this.setDeltaMovement(oldMovement.x, oldMovement.y, 0.1 * dir.getStepZ());
        }
    }

    private boolean suffocatesAt(BlockPos pos) {
        AABB boundingBox = this.getBoundingBox();
        AABB testArea = new AABB(pos.getX(), boundingBox.minY, pos.getZ(),
                pos.getX() + 1.0, boundingBox.maxY, pos.getZ() + 1.0).deflate(1.0E-7);
        return this.level().collidesWithSuffocatingBlock(this, testArea);
    }

    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    protected void markHurt() {
    }

    @Override
    public void knockback(double strength, double x, double z, DamageSource source, float damage, boolean extra,
                          Entity attacker, EntityKnockbackEvent.Cause cause) {
        if (this.getAbilities().invulnerable) return;
        super.knockback(strength, x, z, source, damage, extra, attacker, cause);
    }

    @Override
    public void move(@NonNull MoverType moverType, @NonNull Vec3 movement) {
        double oldX = this.getX();
        double oldZ = this.getZ();
        super.move(moverType, movement);
        actionPack.updateAutoJump((float) (this.getX() - oldX), (float) (this.getZ() - oldZ));
    }

    @Override
    public @NonNull Vec3 getLastClientMoveIntent() {
        float forward = actionPack.getForward();
        float strafing = actionPack.getStrafing();
        if (forward == 0 && strafing == 0) return Vec3.ZERO;
        float yawRad = getYRot() * (float) (Math.PI / 180.0);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        return new Vec3(strafing * cos - forward * sin, 0, forward * cos + strafing * sin);
    }
}
