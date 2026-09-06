package hero.bane.herobot.paper.bot;

import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.common.bot.Shadows;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.ai.AiScriptRegistry;
import hero.bane.herobot.paper.api.HeroBotApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.io.IOException;

public final class ShadowSpawner {

    private ShadowSpawner() {
    }

    public static void onLogout(MinecraftServer server, ServerPlayer leaving) {
        if (server == null || leaving instanceof BotPlayer) return;

        Shadows.Shadow shadow = Shadows.of(leaving.getUUID());
        if (shadow == null) return;

        String name = leaving.getGameProfile().name();
        GameType gamemode = leaving.gameMode.getGameModeForPlayer();

        server.execute(() -> spawn(server, name, shadow.scriptName(), gamemode));
    }

    private static void spawn(MinecraftServer server, String name, String scriptName, GameType gamemode) {
        HeroBotApi.spawnAtLogout(server, name, gamemode).whenComplete((bot, error) -> {
            if (error != null || bot == null) {
                HeroBot.LOGGER.warn("Could not spawn a shadow for {}: {}", name,
                        error == null ? "no bot was created" : error.toString());
                return;
            }
            if (scriptName == null) return;
            server.execute(() -> startScript(server, bot, scriptName));
        });
    }

    private static void startScript(MinecraftServer server, BotPlayer bot, String scriptName) {
        AiScript script;
        try {
            script = AiScriptRegistry.load(server, scriptName);
        } catch (IOException e) {
            HeroBot.LOGGER.warn("Could not load '{}' for {}'s shadow", scriptName, bot.getGameProfile().name(), e);
            return;
        }
        if (script == null) {
            HeroBot.LOGGER.warn("No script named '{}' for {}'s shadow", scriptName, bot.getGameProfile().name());
            return;
        }
        AiScriptRegistry.assign(bot, scriptName, script);
        AiScriptRegistry.fireStart(bot);
    }
}
