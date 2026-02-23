package hero.bane.herobot;

import hero.bane.herobot.command.*;
import hero.bane.herobot.rule.RuleConfigIO;
import hero.bane.herobot.util.delayer.DelayedQueue;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeRoBot implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeRoBot");

    @Override
    public void onInitialize() {
        HeRoBotSettings.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
        {
            PlayerCommand.register(dispatcher, registryAccess);
            PlayerSpawnCommand.register(dispatcher, registryAccess);
            DistanceCommand.register(dispatcher, registryAccess);
            HeRoBotCommand.register(dispatcher, registryAccess);
            DelayedCommand.register(dispatcher, registryAccess);
        });

        ServerTickEvents.END_SERVER_TICK.register(DelayedQueue::tick);
    }
}
