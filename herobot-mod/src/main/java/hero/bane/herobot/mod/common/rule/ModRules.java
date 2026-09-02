package hero.bane.herobot.mod.common.rule;

import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.common.rule.Rule;
import hero.bane.herobot.common.rule.RuleRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.File;

public final class ModRules {
    private ModRules() {
    }

    public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "herobot.json");

    @Rule(desc = "Disable moving piston blocks block rain from falling down")
    public static boolean rainThroughMovingPiston = true;

    public static void init() {
        RuleRegistry.register(HeroBotSettings.class);
        RuleRegistry.register(ModRules.class);
        RuleConfigIO.initClient(CONFIG_FILE);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RuleConfigIO.clearWorld());
        ServerLevelEvents.LOAD.register((server, world) -> RuleConfigIO.initWorld(server));
    }

    public static boolean isCreativeNoClipFlying(Entity entity) {
        return HeroBotSettings.serverHasHeroBot
                && HeroBotSettings.creativeNoClip
                && entity instanceof Player player
                && player.isCreative()
                && player.getAbilities().flying;
    }
}
