package hero.bane.herobot.util.delayer;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DelayedQueue {

    public enum ExecutorType {
        ENTITY,
        COMMAND_BLOCK,
        CONSOLE
    }

    public static final class ExecutorData {
        public final ExecutorType type;
        public final UUID entity;
        public final ResourceKey<Level> dimension;
        public final BlockPos pos;

        private ExecutorData(ExecutorType type, UUID entity,
                             ResourceKey<Level> dimension, BlockPos pos) {
            this.type = type;
            this.entity = entity;
            this.dimension = dimension;
            this.pos = pos;
        }

        public static ExecutorData entity(UUID uuid) {
            return new ExecutorData(ExecutorType.ENTITY, uuid, null, null);
        }

        public static ExecutorData commandBlock(ResourceKey<Level> dimension, BlockPos pos) {
            return new ExecutorData(ExecutorType.COMMAND_BLOCK, null, dimension, pos);
        }

        public static ExecutorData console() {
            return new ExecutorData(ExecutorType.CONSOLE, null, null, null);
        }
    }

    public record Entry(String id, ExecutorData executor, String payload, boolean isFunction, long executeAt) {

        public long remainingTicks(ServerLevel level) {
                return Math.max(0L, executeAt - level.getGameTime());
            }

            public void execute(MinecraftServer server) {

                CommandSourceStack source;

                if (executor.type == ExecutorType.ENTITY) {

                    Entity entity = null;

                    for (ServerLevel level : server.getAllLevels()) {
                        entity = level.getEntity(executor.entity);
                        if (entity != null) {
                            break;
                        }
                    }

                    if (entity == null) return;

                    source = new CommandSourceStack(
                            server,
                            entity.position(),
                            entity.getRotationVector(),
                            (ServerLevel) entity.level(),
                            PermissionSet.ALL_PERMISSIONS,
                            entity.getName().getString(),
                            entity.getDisplayName(),
                            server,
                            entity
                    ).withCallback(CommandResultCallback.EMPTY);

                } else if (executor.type == ExecutorType.COMMAND_BLOCK) {

                    ServerLevel level = server.getLevel(executor.dimension);
                    if (level == null) return;

                    source = new CommandSourceStack(
                            server,
                            executor.pos.getCenter(),
                            Vec2.ZERO,
                            level,
                            PermissionSet.ALL_PERMISSIONS,
                            "@",
                            Component.literal("@"),
                            server,
                            null
                    ).withCallback(CommandResultCallback.EMPTY);

                } else {

                    source = server.createCommandSourceStack()
                            .withCallback(CommandResultCallback.EMPTY);
                }

                if (isFunction) {
                    server.getCommands().performPrefixedCommand(
                            source,
                            "function " + payload
                    );
                } else {
                    server.getCommands().performPrefixedCommand(
                            source,
                            payload
                    );
                }
            }
        }

    private static final List<Entry> QUEUE = new ArrayList<>();

    public static synchronized void add(Entry entry) {
        QUEUE.add(entry);
    }

    public static synchronized List<Entry> snapshot() {
        return List.copyOf(QUEUE);
    }

    public static synchronized List<Entry> snapshotPlayer(UUID uuid) {
        return QUEUE.stream()
                .filter(e -> e.executor.type == ExecutorType.ENTITY
                        && e.executor.entity.equals(uuid))
                .toList();
    }

    public static synchronized Entry remove(int index) {
        return QUEUE.remove(index);
    }

    public static synchronized int clearAndReturnCount() {
        int size = QUEUE.size();
        QUEUE.clear();
        return size;
    }

    public static void tick(MinecraftServer server) {

        ServerLevel overworld = server.overworld();
        long time = overworld.getGameTime();

        List<Entry> toExecute = new ArrayList<>();

        synchronized (DelayedQueue.class) {
            for (Entry e : QUEUE) {
                if (time >= e.executeAt) {
                    toExecute.add(e);
                }
            }
            QUEUE.removeAll(toExecute);
        }

        for (Entry e : toExecute) {
            e.execute(server);
        }
    }
}
