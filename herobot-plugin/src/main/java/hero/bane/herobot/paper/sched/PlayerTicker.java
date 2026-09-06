package hero.bane.herobot.paper.sched;

import hero.bane.herobot.paper.ai.AiScriptRegistry;
import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.bot.BotVision;
import hero.bane.herobot.paper.ping.PingBoosters;
import hero.bane.herobot.paper.rule.CombatRules;
import hero.bane.herobot.paper.rule.RuleEffects;
import hero.bane.herobot.paper.util.BlockBreakTasks;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerTicker {

    private static final Map<UUID, ScheduledTask> TASKS = new ConcurrentHashMap<>();

    private PlayerTicker() {
    }

    public static void start(ServerPlayer player) {
        Player bukkit = player.getBukkitEntity();
        UUID id = bukkit.getUniqueId();
        stop(id);

        ScheduledTask task = Sched.entityTimer(player, ignored -> tick(bukkit), () -> TASKS.remove(id), 1L, 1L);
        if (task != null) TASKS.put(id, task);
    }

    public static void stop(UUID id) {
        ScheduledTask task = TASKS.remove(id);
        if (task != null) task.cancel();
    }

    public static void stopAll() {
        for (UUID id : TASKS.keySet()) stop(id);
    }

    private static void tick(Player bukkit) {
        ServerPlayer player = ((CraftPlayer) bukkit).getHandle();
        if (player.isRemoved() && !(player instanceof BotPlayer)) return;

        BlockBreakTasks.tickPlayer(player);
        AiScriptRegistry.tickPlayer(player);
        PingBoosters.tickPlayer(player);
        if (player instanceof BotPlayer bot) BotVision.tickBot(bot);
        RuleEffects.tickPlayer(player);
        CombatRules.tickPlayer(player);
    }
}
