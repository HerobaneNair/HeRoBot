package hero.bane.herobot.paper.bot;

import hero.bane.herobot.paper.ai.AiScriptRegistry;

public final class BotEvents {

    private BotEvents() {
    }

    public static void damaged(BotPlayer bot) {
        AiScriptRegistry.markDamaged(bot);
    }

    public static void died(BotPlayer bot) {
        AiScriptRegistry.stopRunning(bot);
    }

    public static void respawned(BotPlayer bot) {
    }

    public static void chat(BotPlayer bot, String line) {
        AiScriptRegistry.onChatMessage(bot, line);
    }
}
