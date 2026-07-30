package hero.bane.herobot.paper;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Placeholder entrypoint. Exists so the Paper dev server has something to load while the
 * platform-agnostic code is still being moved into the {@code common} module.
 */
public final class HeroBotPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("HeroBot placeholder enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("HeroBot placeholder disabled.");
    }
}
