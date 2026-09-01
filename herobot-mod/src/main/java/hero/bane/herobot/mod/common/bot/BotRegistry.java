package hero.bane.herobot.mod.common.bot;

import hero.bane.herobot.mod.common.HeroBot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lookup and lifecycle notifications for spawned bots.
 *
 * <p>Unlike a standalone map, this is a view over the vanilla player list, so it cannot drift out
 * of sync with the server: a bot exists here exactly as long as it is a connected player. The
 * listener hooks are fired from {@link BotPlayer} when a bot joins and from the mod disconnect
 * handler when one leaves.
 */
public final class BotRegistry {

    public interface Listener {
        default void onBotSpawn(BotPlayer bot) {
        }

        default void onBotDespawn(BotPlayer bot) {
        }
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private BotRegistry() {
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static boolean removeListener(Listener listener) {
        return LISTENERS.remove(listener);
    }

    /** Returns the bot with this name, or {@code null} if no such bot is connected. Case-insensitive. */
    public static BotPlayer get(String name) {
        return get(HeroBot.currentServer, name);
    }

    public static BotPlayer get(MinecraftServer server, String name) {
        if (server == null || name == null) return null;
        String key = name.toLowerCase(Locale.ROOT);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot
                    && bot.getGameProfile().name().toLowerCase(Locale.ROOT).equals(key)) {
                return bot;
            }
        }
        return null;
    }

    public static boolean isBot(ServerPlayer player) {
        return player instanceof BotPlayer;
    }

    /** Every currently connected bot. The returned list is a snapshot and safe to iterate. */
    public static Collection<BotPlayer> all() {
        return all(HeroBot.currentServer);
    }

    public static Collection<BotPlayer> all(MinecraftServer server) {
        List<BotPlayer> bots = new ArrayList<>();
        if (server == null) return bots;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot) bots.add(bot);
        }
        return bots;
    }

    public static void despawn(BotPlayer bot) {
        bot.botPlayerDisconnect(Component.literal("Removed"));
    }

    public static int despawnMatching(MinecraftServer server, UUID id, String name) {
        String key = name == null ? null : name.toLowerCase(Locale.ROOT);
        int removed = 0;
        for (BotPlayer bot : all(server)) {
            boolean matches = (id != null && bot.getUUID().equals(id))
                    || (key != null && bot.getGameProfile().name().toLowerCase(Locale.ROOT).equals(key));
            if (!matches) continue;
            bot.botPlayerDisconnectNow(Component.literal("Replaced by the real player"));
            removed++;
        }
        return removed;
    }

    public static void despawnAll() {
        despawnAll(HeroBot.currentServer);
    }

    public static void despawnAll(MinecraftServer server) {
        for (BotPlayer bot : all(server)) despawn(bot);
    }

    static void fireSpawn(BotPlayer bot) {
        fire(bot, true);
    }

    /** Called from the mod disconnect handler when a bot leaves. */
    public static void fireDespawn(BotPlayer bot) {
        fire(bot, false);
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
}
