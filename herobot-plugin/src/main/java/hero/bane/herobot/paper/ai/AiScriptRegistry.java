package hero.bane.herobot.paper.ai;

import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.ai.runtime.ScriptRunner;
import hero.bane.herobot.paper.bot.BotPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import hero.bane.herobot.common.ai.AiScript;

public final class AiScriptRegistry {
    private static final Map<String, AiScript> CACHE = new HashMap<>();
    private static final Map<UUID, ScriptRunner> RUNNERS = new ConcurrentHashMap<>();

    private AiScriptRegistry() {}

    public static synchronized AiScript load(MinecraftServer server, String name) throws IOException {
        AiScript cached = CACHE.get(name);
        if (cached != null) return cached;
        AiScript s = AiScriptIO.loadByName(server, name);
        if (s != null) CACHE.put(name, s);
        return s;
    }

    public static synchronized void put(String name, AiScript script) {
        CACHE.put(name, script);
    }

    public static synchronized void invalidate(String name) {
        CACHE.remove(name);
    }

    public static List<String> list(MinecraftServer server) {
        return AiScriptIO.listScripts(server);
    }

    public static void assign(ServerPlayer player, String scriptName, AiScript script) {
        if (player instanceof BotPlayer bot) bot.setAssignedScriptName(scriptName);
        ScriptRunner old = RUNNERS.remove(player.getUUID());
        if (old != null) old.stop();
        if (script != null) {
            ScriptRunner runner = new ScriptRunner(player, script);
            RUNNERS.put(player.getUUID(), runner);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player instanceof BotPlayer bot) bot.setAssignedScriptName(null);
        ScriptRunner old = RUNNERS.remove(player.getUUID());
        if (old != null) old.stop();
    }

    public static void stopRunning(ServerPlayer player) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r == null) return;
        r.rebind(player);
        r.stop();
        if (player instanceof BotPlayer bot && bot.getAssignedScriptName() == null) {
            bot.setAssignedScriptName(r.script().name());
        }
    }

    public static void pause(ServerPlayer player) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r != null) r.pause();
    }

    public static void resume(ServerPlayer player) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r != null) r.resume();
    }

    public static synchronized void reset() {
        for (ScriptRunner r : RUNNERS.values()) {
            try { r.stop(); } catch (Throwable ignored) {}
        }
        RUNNERS.clear();
        CACHE.clear();
    }

    public static ScriptRunner runner(ServerPlayer player) {
        return RUNNERS.get(player.getUUID());
    }

    public static void fireStart(ServerPlayer player) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r != null) r.fireStart();
    }

    public static void markDamaged(ServerPlayer player) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r != null) r.markDamaged();
    }

    public static void onChatMessage(ServerPlayer player, String message) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r != null) r.enqueueChatMessage(message);
    }

    public static void markPathReached(ServerPlayer player) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r != null) r.markPathReached();
    }

    public static void markPathFailed(ServerPlayer player) {
        ScriptRunner r = RUNNERS.get(player.getUUID());
        if (r != null) r.markPathFailed();
    }

    public static void tickAll(MinecraftServer server) {
        if (RUNNERS.isEmpty()) return;
        var it = RUNNERS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                e.getValue().stop();
                it.remove();
                continue;
            }
            try {
                e.getValue().tick(p);
            } catch (Throwable t) {
                HeroBot.LOGGER.warn("ScriptRunner tick failed for {}: {}", p.getGameProfile().name(), t.toString());
            }
        }
    }

}
