package hero.bane.herobot.paper.bot;

import hero.bane.herobot.common.ping.PingDelayOptions;
import hero.bane.herobot.common.ping.PingDelays;
import hero.bane.herobot.paper.util.RayTrace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BotVision {

    private static final int MAX_HISTORY_TICKS = 60;
    private static final double CAPTURE_RADIUS = 8.0;

    private static final List<Map<Integer, AABB>> ring =
            new ArrayList<>(Collections.nCopies(MAX_HISTORY_TICKS, null));
    private static int head = -1;
    private static int stored;

    private BotVision() {}

    public static void tick(MinecraftServer server) {
        List<BotPlayer> watchers = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot && bot.ping > 0 && looksLate(bot)) {
                if (watchers == null) watchers = new ArrayList<>();
                watchers.add(bot);
            }
        }
        if (watchers == null) {
            reset();
            return;
        }

        Map<Integer, AABB> boxes = new HashMap<>();
        for (BotPlayer bot : watchers) {
            AABB region = bot.getBoundingBox().inflate(CAPTURE_RADIUS);
            for (Entity entity : bot.level().getEntities(bot, region, e -> !e.isSpectator() && e.isPickable())) {
                boxes.putIfAbsent(entity.getId(), RayTrace.LIVE.boxOf(entity));
            }
        }
        push(boxes);
    }

    public static RayTrace.EntityView viewFor(Entity source) {
        if (!(source instanceof BotPlayer bot) || bot.ping <= 0 || !looksLate(bot)) return RayTrace.LIVE;

        Map<Integer, AABB> past = snapshotAgo(bot.delayTicks());
        if (past == null) return RayTrace.LIVE;

        return new RayTrace.EntityView() {
            @Override
            public AABB boxOf(Entity entity) {
                AABB box = past.get(entity.getId());
                return box != null ? box : RayTrace.LIVE.boxOf(entity);
            }

            @Override
            public double searchMargin() {
                return CAPTURE_RADIUS;
            }
        };
    }

    private static boolean looksLate(BotPlayer bot) {
        return PingDelays.enabled(bot.getUUID(), PingDelayOptions.Category.LOOK);
    }

    private static void push(Map<Integer, AABB> boxes) {
        head = (head + 1) % MAX_HISTORY_TICKS;
        ring.set(head, boxes);
        if (stored < MAX_HISTORY_TICKS) stored++;
    }

    private static Map<Integer, AABB> snapshotAgo(int ticksAgo) {
        if (ticksAgo <= 0 || ticksAgo >= stored) return null;
        int index = ((head - ticksAgo) % MAX_HISTORY_TICKS + MAX_HISTORY_TICKS) % MAX_HISTORY_TICKS;
        return ring.get(index);
    }

    private static void reset() {
        if (stored == 0) return;
        Collections.fill(ring, null);
        head = -1;
        stored = 0;
    }
}
