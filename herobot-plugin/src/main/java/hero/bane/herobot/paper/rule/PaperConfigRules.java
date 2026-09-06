package hero.bane.herobot.paper.rule;

import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.paper.HeroBot;
import io.papermc.paper.configuration.GlobalConfiguration;

public final class PaperConfigRules {

    private static Boolean originalSkipDamageTick;
    private static boolean unavailable;

    private PaperConfigRules() {
    }

    public static void apply() {
        if (unavailable) return;
        try {
            GlobalConfiguration.UnsupportedSettings settings = GlobalConfiguration.get().unsupportedSettings;
            if (originalSkipDamageTick == null) {
                originalSkipDamageTick = settings.skipVanillaDamageTickWhenShieldBlocked;
            }
            settings.skipVanillaDamageTickWhenShieldBlocked = HeroBotSettings.shieldStunning;
        } catch (Throwable t) {
            unavailable = true;
            HeroBot.LOGGER.warn("Could not mirror shieldStunning into the Paper global configuration", t);
        }
    }

    public static void restore() {
        if (unavailable || originalSkipDamageTick == null) return;
        try {
            GlobalConfiguration.get().unsupportedSettings.skipVanillaDamageTickWhenShieldBlocked = originalSkipDamageTick;
        } catch (Throwable t) {
            HeroBot.LOGGER.warn("Could not restore the Paper global configuration", t);
        }
        originalSkipDamageTick = null;
    }
}
