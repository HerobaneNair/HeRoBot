package hero.bane.herobot.mixin.carpet;

import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

/**
 * Removes Carpet rules whose backing mixins HeroBot disables (see
 * {@code hero.bane.herobot.carpet.CarpetCompat}) so they no longer appear in {@code /carpet}.
 * Without this they'd be dead toggles: their implementing Carpet mixins are gone, so e.g.
 * {@code /carpet explosionNoBlockDamage} would silently do nothing, and several duplicate features
 * HeroBot already owns through its own settings.
 *
 * <p>{@link Pseudo} targets a class not on our compile classpath and is skipped when Carpet is
 * absent. Carpet registers its rules in {@code parseSettingsClass}; we drop the dead ones from the
 * backing map at its tail.
 */
@Pseudo
@Mixin(targets = "carpet.api.settings.SettingsManager", remap = false)
public abstract class CarpetSettingsManagerMixin {
    @Shadow
    @Final
    private Map<String, ?> rules;

    @Unique
    private static final Set<String> HEROBOT_DISABLED_RULES = Set.of(
            "creativeNoClip",
            "creativeFlyDrag",
            "creativeFlySpeed",
            "xpNoCooldown",
            "optimizedTNT",
            "explosionNoBlockDamage"
    );

    @Inject(method = "parseSettingsClass", at = @At("TAIL"), remap = false)
    private void herobot$removeDeadRules(Class<?> settingsClass, CallbackInfo ci) {
        int before = rules.size();
        rules.keySet().removeAll(HEROBOT_DISABLED_RULES);
        int removed = before - rules.size();
        if (removed > 0) {
            LoggerFactory.getLogger("HeroBot/carpet-compat")
                    .info("removed {} dead Carpet rule(s) from /carpet: {}", removed, HEROBOT_DISABLED_RULES);
        }
    }
}
