package hero.bane.herobot.paper.bot;

import hero.bane.herobot.common.ping.PingDelayOptions;
import hero.bane.herobot.common.ping.PingDelays;
import hero.bane.herobot.paper.util.RayTrace;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BotVision {

    private static final int MAX_HISTORY_TICKS = 60;
    private static final double CAPTURE_RADIUS = 8.0;

    private static final Map<UUID, Ring> RINGS = new ConcurrentHashMap<>();

    private BotVision() {}

    private static final class Ring {
        final List<Map<Integer, AABB>> frames =
                new ArrayList<>(Collections.nCopies(MAX_HISTORY_TICKS, null));
        int head = -1;
        int stored;

        void push(Map<Integer, AABB> boxes) {
            head = (head + 1) % MAX_HISTORY_TICKS;
            frames.set(head, boxes);
            if (stored < MAX_HISTORY_TICKS) stored++;
        }

        Map<Integer, AABB> ago(int ticksAgo) {
            if (ticksAgo <= 0 || ticksAgo >= stored) return null;
            int index = ((head - ticksAgo) % MAX_HISTORY_TICKS + MAX_HISTORY_TICKS) % MAX_HISTORY_TICKS;
            return frames.get(index);
        }
    }

    public static void tickBot(BotPlayer bot) {
        if (bot.ping <= 0 || !looksLate(bot)) {
            RINGS.remove(bot.getUUID());
            return;
        }

        Map<Integer, AABB> boxes = new HashMap<>();
        AABB region = bot.getBoundingBox().inflate(CAPTURE_RADIUS);
        for (Entity entity : bot.level().getEntities(bot, region, e -> !e.isSpectator() && e.isPickable())) {
            boxes.putIfAbsent(entity.getId(), RayTrace.LIVE.boxOf(entity));
        }
        RINGS.computeIfAbsent(bot.getUUID(), key -> new Ring()).push(boxes);
    }

    public static RayTrace.EntityView viewFor(Entity source) {
        if (!(source instanceof BotPlayer bot) || bot.ping <= 0 || !looksLate(bot)) return RayTrace.LIVE;

        Ring ring = RINGS.get(bot.getUUID());
        if (ring == null) return RayTrace.LIVE;

        Map<Integer, AABB> past = ring.ago(bot.delayTicks());
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

    public static void forget(UUID id) {
        RINGS.remove(id);
    }

    public static void reset() {
        RINGS.clear();
    }

    private static boolean looksLate(BotPlayer bot) {
        return PingDelays.enabled(bot.getUUID(), PingDelayOptions.Category.LOOK);
    }
}
