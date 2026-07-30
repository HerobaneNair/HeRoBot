package hero.bane.herobot.mod.common.mixin.carpet;

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
