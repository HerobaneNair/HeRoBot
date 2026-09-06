package hero.bane.herobot.paper;

import hero.bane.herobot.paper.ai.AiScriptRegistry;
import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.bot.BotRegistry;
import hero.bane.herobot.common.bot.Shadows;
import hero.bane.herobot.paper.bot.ShadowSpawner;
import hero.bane.herobot.paper.bot.BotVision;
import hero.bane.herobot.paper.command.HeroBotCommand;
import hero.bane.herobot.paper.command.HeroBotSelectorOptions;
import hero.bane.herobot.paper.command.PlayerCommand;
import hero.bane.herobot.paper.command.PlayerSpawnCommand;
import hero.bane.herobot.paper.command.SourceAwareSelectorNodes;
import hero.bane.herobot.paper.config.BotNameSuggestions;
import hero.bane.herobot.paper.control.RemotePathSettings;
import hero.bane.herobot.paper.control.RemotePathState;
import hero.bane.herobot.paper.networking.HeroBotNetwork;
import hero.bane.herobot.paper.ping.PingBoosters;
import hero.bane.herobot.paper.ping.TabListPing;
import hero.bane.herobot.common.ping.PingDelays;
import hero.bane.herobot.common.bot.PlayerLogouts;
import hero.bane.herobot.paper.rule.RuleConfigIO;
import hero.bane.herobot.paper.sched.PlayerTicker;
import hero.bane.herobot.paper.sched.Sched;
import hero.bane.herobot.paper.util.BlockBreakTasks;
import hero.bane.herobot.paper.voice.PluginVoice;
import hero.bane.herobot.paper.voice.VoiceOps;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import hero.bane.herobot.paper.rule.CombatRules;
import hero.bane.herobot.paper.rule.ExplosionRules;
import hero.bane.herobot.paper.rule.PaperRules;
import hero.bane.herobot.paper.rule.RuleEffects;
import hero.bane.herobot.paper.rule.TickRateRules;
import hero.bane.herobot.paper.rule.WorldRules;

public final class HeroBotPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        MinecraftServer server = craftServer.getServer();

        Sched.init(this);

        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create the plugin data folder; bot name suggestions will not persist");
        }
        BotNameSuggestions.init(getDataFolder());

        PaperRules.init(server);
        HeroBotCommand.setPluginVersion(getPluginMeta().getVersion());
        HeroBotSelectorOptions.register();

        PluginVoice.init(this, server);

        HeroBotNetwork.init(this, server);
        RuleConfigIO.onSettingsChanged = () -> {
            PaperRules.apply();
            HeroBotNetwork.sendSettingsToAll();
        };

        CommandBuildContext buildContext = Commands.createValidationContext(server.registryAccess());
        PlayerCommand.register(server.getCommands().getDispatcher(), buildContext);
        PlayerSpawnCommand.register(server.getCommands().getDispatcher());
        HeroBotCommand.register(server.getCommands().getDispatcher(), buildContext);
        SourceAwareSelectorNodes.apply(server.getCommands().getDispatcher());
        craftServer.syncCommands();

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new RuleEffects(), this);
        Bukkit.getPluginManager().registerEvents(new CombatRules(), this);
        Bukkit.getPluginManager().registerEvents(new ExplosionRules(), this);
        Bukkit.getPluginManager().registerEvents(new WorldRules(), this);

        BotRegistry.addListener(new BotRegistry.Listener() {
            @Override
            public void onBotSpawn(BotPlayer bot) {
                PlayerTicker.start(bot);
            }

            @Override
            public void onBotDespawn(BotPlayer bot) {
                PlayerTicker.stop(bot.getUUID());
                BotVision.forget(bot.getUUID());
            }
        });

        Sched.globalTimer(() -> {
            TabListPing.tick(server);
            PluginVoice.tick();
            TickRateRules.tick(server);
        }, 1L, 1L);

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            PlayerTicker.start(online);
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        repatchSelectors();
    }

    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        repatchSelectors();
    }

    private void repatchSelectors() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        SourceAwareSelectorNodes.apply(server.getCommands().getDispatcher());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        BotRegistry.despawnMatching(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        ServerPlayer joining = ((CraftPlayer) event.getPlayer()).getHandle();
        if (joining instanceof BotPlayer) return;
        PlayerTicker.start(joining);
        BotRegistry.despawnMatching(joining.getUUID(), joining.getGameProfile().name());
        Shadows.disarm(joining.getUUID());
        ShadowSpawner.forget(joining.getGameProfile().name());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ServerPlayer leaving = ((CraftPlayer) event.getPlayer()).getHandle();
        PlayerLogouts.record(leaving.getUUID(), leaving.getGameProfile().name(),
                leaving.level().dimension().identifier().toString(),
                leaving.getX(), leaving.getY(), leaving.getZ(), leaving.getYRot(), leaving.getXRot());
        ShadowSpawner.onLogout(leaving.level().getServer(), leaving);
        java.util.UUID id = event.getPlayer().getUniqueId();
        PlayerTicker.stop(id);
        AiScriptRegistry.forget(id);
        BotVision.forget(id);
        BotRegistry.forget(id);
        HeroBotNetwork.forget(id);
        RemotePathState.clear(id);
        RemotePathSettings.clear(id);
        BlockBreakTasks.clear(id);
        CombatRules.forget(id);
        TickRateRules.forget(id);
        VoiceOps.forget(id);
        PingBoosters.forget(id);
        PingDelays.forget(id);
    }

    @Override
    public void onDisable() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        PlayerTicker.stopAll();
        BotVision.reset();
        TickRateRules.restore(server);
        PingBoosters.shutdown(server);
        RuleConfigIO.onSettingsChanged = null;
        HeroBotNetwork.shutdown(this);
        AiScriptRegistry.reset();
        PluginVoice.shutdown();
        if (!Bukkit.isStopping()) BotRegistry.despawnAll();
        RuleConfigIO.clearWorld();
        PaperRules.shutdown();
        Sched.shutdown();
    }
}
