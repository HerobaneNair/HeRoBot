package hero.bane.herobot.paper.api;

import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.common.bot.PlayerLogouts;
import hero.bane.herobot.paper.ai.AiScriptRegistry;
import hero.bane.herobot.paper.bot.BotChat;
import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.bot.BotPlayerActionPack;
import hero.bane.herobot.paper.bot.BotRegistry;
import hero.bane.herobot.paper.bot.SavedLogouts;
import hero.bane.herobot.paper.bot.pathing.PathSettings;
import hero.bane.herobot.paper.bot.pathing.traversal.BotPathing;
import hero.bane.herobot.paper.control.PlayerController;
import hero.bane.herobot.paper.control.PlayerControllers;
import hero.bane.herobot.paper.ping.PingBoosters;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class HeroBotApi {

    private static final long SPAWN_TIMEOUT_SECONDS = 30L;

    private HeroBotApi() {
    }

    public static CompletableFuture<BotPlayer> spawn(MinecraftServer server, ServerLevel level, String username,
                                                     Vec3 pos, float yaw, float pitch, GameType gameType) {
        CompletableFuture<BotPlayer> future = new CompletableFuture<>();
        String key = username.toLowerCase(Locale.ROOT);

        BotRegistry.Listener listener = new BotRegistry.Listener() {
            @Override
            public void onBotSpawn(BotPlayer bot) {
                if (bot.getGameProfile().name().toLowerCase(Locale.ROOT).equals(key)) future.complete(bot);
            }
        };
        BotRegistry.addListener(listener);

        if (!BotPlayer.spawn(server, level, username, pos, yaw, pitch, gameType)) {
            BotRegistry.removeListener(listener);
            future.completeExceptionally(new IllegalStateException("Already spawning a bot named " + username));
            return future;
        }

        return future.orTimeout(SPAWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((bot, error) -> BotRegistry.removeListener(listener));
    }

    public static CompletableFuture<BotPlayer> spawn(MinecraftServer server, ServerLevel level,
                                                     String username, Vec3 pos) {
        return spawn(server, level, username, pos, 0.0f, 0.0f, GameType.SURVIVAL);
    }

    public static CompletableFuture<BotPlayer> spawnAtLogout(MinecraftServer server, String username) {
        return spawnAtLogout(server, username, GameType.SURVIVAL);
    }

    public static CompletableFuture<BotPlayer> spawnAtLogout(MinecraftServer server, String username,
                                                             GameType gameType) {
        PlayerLogouts.Logout logout = PlayerLogouts.of(username);
        if (logout != null) return spawnAt(server, username, logout, gameType);

        return CompletableFuture.supplyAsync(() -> SavedLogouts.resolve(server, username))
                .thenApplyAsync(id -> SavedLogouts.read(server, id, username), server)
                .thenCompose(saved -> saved == null
                        ? CompletableFuture.<BotPlayer>failedFuture(
                                new IllegalStateException("No recorded logout position for " + username))
                        : spawnAt(server, username, saved, gameType));
    }

    private static CompletableFuture<BotPlayer> spawnAt(MinecraftServer server, String username,
                                                        PlayerLogouts.Logout logout, GameType gameType) {
        ServerLevel level = levelOf(server, logout.dimension());
        if (level == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Unloaded dimension " + logout.dimension() + " for " + username));
        }
        return spawn(server, level, logout.name(), new Vec3(logout.x(), logout.y(), logout.z()),
                logout.yaw(), logout.pitch(), gameType);
    }

    public static PlayerLogouts.Logout logout(String username) {
        return PlayerLogouts.of(username);
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) return level;
        }
        return null;
    }

    public static BotPlayer bot(String name) {
        return BotRegistry.get(name);
    }

    public static Collection<BotPlayer> bots() {
        return BotRegistry.all();
    }

    public static boolean isBot(ServerPlayer player) {
        return BotRegistry.isBot(player);
    }

    public static void despawn(BotPlayer bot) {
        BotRegistry.despawn(bot);
    }

    public static boolean despawn(String name) {
        BotPlayer bot = BotRegistry.get(name);
        if (bot == null) return false;
        BotRegistry.despawn(bot);
        return true;
    }

    public static void despawnAll() {
        BotRegistry.despawnAll();
    }

    public static void kill(BotPlayer bot) {
        bot.kill(bot.level());
    }

    public static void addListener(BotRegistry.Listener listener) {
        BotRegistry.addListener(listener);
    }

    public static boolean removeListener(BotRegistry.Listener listener) {
        return BotRegistry.removeListener(listener);
    }

    public static BotPlayerActionPack actions(BotPlayer bot) {
        return bot.getActionPack();
    }

    public static PlayerController controller(ServerPlayer player) {
        return PlayerControllers.of(player);
    }

    public static void chat(BotPlayer bot, String message) {
        BotChat.send(bot, message, false);
    }

    public static void chat(BotPlayer bot, String message, boolean asOperator) {
        BotChat.send(bot, message, asOperator);
    }

    public static boolean setPing(ServerPlayer player, int millis) {
        return PingBoosters.set(player, millis);
    }

    public static void clearPing(ServerPlayer player) {
        PingBoosters.clear(player);
    }

    public static BotPathing pathTo(BotPlayer bot, Vec3 target) {
        return pathTo(bot, target, bot.getPathSettings(), null);
    }

    public static BotPathing pathTo(BotPlayer bot, Vec3 target, PathSettings settings, CommandSourceStack source) {
        BotPathing pathing = new BotPathing(bot, target, source, settings);
        bot.setPathFollower(pathing);
        return pathing;
    }

    public static BotPathing pathTo(BotPlayer bot, Entity target) {
        return pathTo(bot, target, bot.getPathSettings(), null);
    }

    public static BotPathing pathTo(BotPlayer bot, Entity target, PathSettings settings, CommandSourceStack source) {
        BotPathing pathing = new BotPathing(bot, target, source, settings);
        bot.setPathFollower(pathing);
        return pathing;
    }

    public static void stopPath(BotPlayer bot) {
        bot.clearPathFollower();
    }

    public static boolean isPathing(BotPlayer bot) {
        BotPathing pathing = bot.getPathFollower();
        return pathing != null && !pathing.isDone();
    }

    public static PathSettings pathSettings(BotPlayer bot) {
        return bot.getPathSettings();
    }

    public static List<String> scripts(MinecraftServer server) {
        return AiScriptRegistry.list(server);
    }

    public static AiScript script(MinecraftServer server, String name) throws IOException {
        return AiScriptRegistry.load(server, name);
    }

    public static void runScript(MinecraftServer server, ServerPlayer bot, String name) throws IOException {
        AiScriptRegistry.assign(bot, name, AiScriptRegistry.load(server, name));
        AiScriptRegistry.fireStart(bot);
    }

    public static void runScript(ServerPlayer bot, String name, AiScript script) {
        AiScriptRegistry.assign(bot, name, script);
        AiScriptRegistry.fireStart(bot);
    }

    public static void stopScript(ServerPlayer bot) {
        AiScriptRegistry.stopRunning(bot);
    }

    public static void clearScript(ServerPlayer bot) {
        AiScriptRegistry.clear(bot);
    }

    public static void pauseScript(ServerPlayer bot) {
        AiScriptRegistry.pause(bot);
    }

    public static void resumeScript(ServerPlayer bot) {
        AiScriptRegistry.resume(bot);
    }

    public static String assignedScript(BotPlayer bot) {
        return bot.getAssignedScriptName();
    }
}
