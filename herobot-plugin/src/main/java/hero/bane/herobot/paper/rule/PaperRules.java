package hero.bane.herobot.paper.rule;

import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.common.rule.RuleRegistry;
import net.minecraft.server.MinecraftServer;

public final class PaperRules {
    private PaperRules() {
    }

    public static void init(MinecraftServer server) {
        RuleRegistry.register(HeroBotSettings.class);
        RuleConfigIO.initWorld(server);
    }
}
