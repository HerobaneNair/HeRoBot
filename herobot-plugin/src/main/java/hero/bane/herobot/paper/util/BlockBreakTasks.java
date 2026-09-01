package hero.bane.herobot.paper.util;

import hero.bane.herobot.paper.control.PlayerControllers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockBreakTasks {
    private BlockBreakTasks() {}

    private static final Map<UUID, Task> TASKS = new ConcurrentHashMap<>();

    private static final int BREAKER_ID = -1;

    private static final class Task {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        final Direction face;
        final boolean force;
        boolean started;
        float damage;

        Task(ResourceKey<Level> dimension, BlockPos pos, Direction face, boolean force) {
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.face = face;
            this.force = force;
        }
    }

    public static void begin(ServerPlayer player, BlockPos pos, Direction face, boolean force) {
        cancel(player);
        TASKS.put(player.getUUID(), new Task(player.level().dimension(), pos, face, force));
    }

    public static void cancel(ServerPlayer player) {
        Task task = TASKS.remove(player.getUUID());
        if (task != null && task.started) abort(player, task);
    }

    public static void clear(UUID id) {
        TASKS.remove(id);
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Task>> it = TASKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Task> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isRemoved() || player.isDeadOrDying()) {
                it.remove();
                continue;
            }
            if (!step(player, entry.getValue())) it.remove();
        }
    }

    private static boolean step(ServerPlayer player, Task task) {
        ServerLevel world = player.level();
        if (world.dimension() != task.dimension) return false;
        BlockState state = world.getBlockState(task.pos);
        if (state.isAir()) {
            world.destroyBlockProgress(BREAKER_ID, task.pos, -1);
            return false;
        }
        if (!player.isWithinBlockInteractionRange(task.pos, 1.0)) {
            abort(player, task);
            return false;
        }

        Vec3 hit = task.force
                ? FaceRaycast.faceCenter(task.pos, task.face)
                : FaceRaycast.visibleHitPoint(player, world, task.pos, task.face);
        if (hit == null) {
            abort(player, task);
            return false;
        }
        PlayerControllers.of(player).lookAt(hit);

        if (!task.started) {
            task.started = true;
            player.gameMode.handleBlockBreakAction(task.pos,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, task.face, world.getMaxY(), -1);
            player.swing(InteractionHand.MAIN_HAND, true);
            player.resetLastActionTime();
            return !world.getBlockState(task.pos).isAir();
        }

        task.damage += state.getDestroyProgress(player, world, task.pos);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.resetLastActionTime();
        if (task.damage >= 1.0f) {
            player.gameMode.handleBlockBreakAction(task.pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, task.face, world.getMaxY(), -1);
            world.destroyBlockProgress(BREAKER_ID, task.pos, -1);
            return false;
        }
        world.destroyBlockProgress(BREAKER_ID, task.pos, (int) (task.damage * 10.0f));
        return true;
    }

    private static void abort(ServerPlayer player, Task task) {
        ServerLevel world = player.level();
        if (world.dimension() != task.dimension) return;
        player.gameMode.handleBlockBreakAction(task.pos,
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, task.face, world.getMaxY(), -1);
        world.destroyBlockProgress(BREAKER_ID, task.pos, -1);
    }
}
