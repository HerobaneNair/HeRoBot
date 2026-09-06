package hero.bane.herobot.paper.sched;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class Sched {

    private static volatile Plugin plugin;

    private Sched() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    public static void shutdown() {
        plugin = null;
    }

    private static Plugin plugin() {
        Plugin owner = plugin;
        if (owner == null) throw new IllegalStateException("HeroBot scheduler used before onEnable");
        return owner;
    }

    private static boolean accepting() {
        Plugin owner = plugin;
        return owner != null && owner.isEnabled();
    }

    public static void global(Runnable task) {
        if (Bukkit.isGlobalTickThread()) {
            task.run();
            return;
        }
        if (!accepting()) return;
        Bukkit.getGlobalRegionScheduler().execute(plugin(), task);
    }

    public static ScheduledTask globalTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin(), ignored -> task.run(), delayTicks, periodTicks);
    }

    public static Executor globalExecutor() {
        return Sched::global;
    }

    public static void region(ServerLevel level, double x, double z, Runnable task) {
        if (!accepting()) return;
        World world = level.getWorld();
        int chunkX = (int) Math.floor(x) >> 4;
        int chunkZ = (int) Math.floor(z) >> 4;
        Bukkit.getRegionScheduler().execute(plugin(), world, chunkX, chunkZ, task);
    }

    public static void region(ServerLevel level, Vec3 pos, Runnable task) {
        region(level, pos.x, pos.z, task);
    }

    public static Executor regionExecutor(ServerLevel level, Vec3 pos) {
        return task -> region(level, pos, task);
    }

    public static void entity(ServerPlayer player, Runnable task) {
        entity(player, task, null);
    }

    public static void entity(ServerPlayer player, Runnable task, Runnable retired) {
        if (!accepting()) return;
        Entity bukkit = player.getBukkitEntity();
        if (bukkit.getScheduler().run(plugin(), ignored -> task.run(), retired) == null && retired != null) {
            retired.run();
        }
    }

    public static void entityLater(ServerPlayer player, Runnable task, long delayTicks) {
        if (!accepting()) return;
        if (delayTicks <= 0) {
            entity(player, task);
            return;
        }
        player.getBukkitEntity().getScheduler()
                .runDelayed(plugin(), ignored -> task.run(), null, delayTicks);
    }

    public static ScheduledTask entityTimer(ServerPlayer player, Consumer<ScheduledTask> task,
                                            Runnable retired, long delayTicks, long periodTicks) {
        return player.getBukkitEntity().getScheduler()
                .runAtFixedRate(plugin(), task, retired, delayTicks, periodTicks);
    }

    public static Executor entityExecutor(ServerPlayer player) {
        return task -> entity(player, task);
    }
}
