package hero.bane.herobot.control;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RemotePathState {
    private RemotePathState() {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        int seq;
        boolean active;
    }

    public static int begin(ServerPlayer player) {
        State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        synchronized (s) {
            s.seq++;
            s.active = true;
            return s.seq;
        }
    }

    public static void finish(ServerPlayer player, int seq) {
        State s = STATES.get(player.getUUID());
        if (s == null) return;
        synchronized (s) {
            if (s.seq == seq) s.active = false;
        }
    }

    public static void deactivate(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null) return;
        synchronized (s) {
            s.active = false;
        }
    }

    public static boolean isActive(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null) return false;
        synchronized (s) {
            return s.active;
        }
    }

    public static void clear(UUID id) {
        STATES.remove(id);
    }
}
