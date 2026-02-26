package hero.bane.herobot;

import hero.bane.herobot.command.*;
import hero.bane.herobot.util.delayer.DelayedQueue;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeroBot implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("HeroBot");
    public static MinecraftServer currentServer = null;

    @Override
    public void onInitialize() {
        HeroBotSettings.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
        {
            PlayerCommand.register(dispatcher, registryAccess);
            PlayerSpawnCommand.register(dispatcher, registryAccess);
            DistanceCommand.register(dispatcher, registryAccess);
            HeroBotCommand.register(dispatcher, registryAccess);
            DelayedCommand.register(dispatcher, registryAccess);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);

        ServerTickEvents.END_SERVER_TICK.register(DelayedQueue::tick);
    }
}
