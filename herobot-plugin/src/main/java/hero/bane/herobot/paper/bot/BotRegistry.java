package hero.bane.herobot.paper.bot;

import hero.bane.herobot.paper.HeroBot;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BotRegistry {

    public interface Listener {
        default void onBotSpawn(BotPlayer bot) {
        }

        default void onBotDespawn(BotPlayer bot) {
        }
    }

    private static final Map<String, BotPlayer> BOTS = new LinkedHashMap<>();
    private static final Set<String> REMOVING = new HashSet<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private BotRegistry() {
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static boolean removeListener(Listener listener) {
        return LISTENERS.remove(listener);
    }

    static void add(BotPlayer bot) {
        BOTS.put(key(bot), bot);
        fire(bot, true);
    }

    public static BotPlayer get(String name) {
        return BOTS.get(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isBot(ServerPlayer player) {
        return player instanceof BotPlayer;
    }

    public static Collection<BotPlayer> all() {
        return new ArrayList<>(BOTS.values());
    }

    public static void despawn(BotPlayer bot) {
        String key = key(bot);
        if (!REMOVING.add(key)) return;
        try {
            bot.level().getServer().getPlayerList().remove(bot);
        } finally {
            BOTS.remove(key);
            REMOVING.remove(key);
        }
        fire(bot, false);
    }

    public static void forget(UUID id) {
        List<BotPlayer> removed = new ArrayList<>();
        BOTS.values().removeIf(bot -> {
            if (!bot.getUUID().equals(id) || REMOVING.contains(key(bot))) return false;
            removed.add(bot);
            return true;
        });
        for (BotPlayer bot : removed) fire(bot, false);
    }

    public static void despawnAll() {
        List<BotPlayer> bots = new ArrayList<>(BOTS.values());
        for (BotPlayer bot : bots) despawn(bot);
    }

    public static int despawnMatching(UUID id, String name) {
        String key = name == null ? null : name.toLowerCase(Locale.ROOT);
        int removed = 0;
        for (BotPlayer bot : all()) {
            boolean matches = (id != null && bot.getUUID().equals(id))
                    || (key != null && key(bot).equals(key));
            if (!matches) continue;
            despawn(bot);
            removed++;
        }
        return removed;
    }

    private static void fire(BotPlayer bot, boolean spawned) {
        for (Listener listener : LISTENERS) {
            try {
                if (spawned) listener.onBotSpawn(bot);
                else listener.onBotDespawn(bot);
            } catch (Throwable t) {
                HeroBot.LOGGER.warn("HeroBot registry listener failed", t);
            }
        }
    }

    private static String key(BotPlayer bot) {
        return bot.getGameProfile().name().toLowerCase(Locale.ROOT);
    }
}
