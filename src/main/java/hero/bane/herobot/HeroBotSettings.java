package hero.bane.herobot;

import hero.bane.herobot.rule.Bounds;
import hero.bane.herobot.rule.Rule;
import hero.bane.herobot.rule.RuleConfigIO;
import hero.bane.herobot.rule.RuleRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.io.File;

public final class HeroBotSettings {
    private HeroBotSettings() {
    }

    public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "herobot.json");

    public static boolean serverHasHeroBot = true;

    public static void init() {
        RuleRegistry.register(HeroBotSettings.class);
        RuleConfigIO.initClient(CONFIG_FILE);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RuleConfigIO.clearWorld());
        ServerWorldEvents.LOAD.register((server, world) -> RuleConfigIO.initWorld(server));
    }

    @Rule(desc = "Creative No Clip, allows to client player to phase through blocks")
    public static boolean creativeNoClip = false;

    @Rule(desc = "Changes creative flying speed multiplier (Default 1.0), how quickly the client flies")
    @Bounds(min = 0.0)
    public static double creativeFlySpeed = 1.0;

    @Rule(desc = "Changes creative air drag (Default 0.09), how quickly the air stops the client while flying")
    @Bounds(min = 0.0, max = 1.0)
    public static double creativeFlyDrag = 0.09;

    public static boolean isCreativeNoClipFlying(Entity entity) {
        return serverHasHeroBot && creativeNoClip && entity instanceof Player player && player.isCreative() && player.getAbilities().flying;
    }

    @Rule(desc = "Spawn offline players in online mode if online-mode player with specified name does not exist")
    public static boolean allowSpawningOfflinePlayers = true;

    @Rule(desc = "Allows listing bot players on the multiplayer screen")
    public static boolean allowListingBotPlayers = true;

    @Rule(desc = "Change the ping to tick conversion for Bot Players (default 25)")
    @Bounds(min = 1)
    public static int botPingToTicks = 25;

    @Rule(desc = "[Experimental] Makes left clicks (attack) delayed by the bot's ping")
    public static boolean botLagAttacks = false;

    @Rule(desc = "[Experimental] Makes right clicks (use) delayed by the bot's ping")
    public static boolean botLagUses = false;

    @Rule(desc = "Bots disconnect on death rather than respawning")
    public static boolean botLeaveOnDeath = false;

    @Rule(desc = "Enables shield stunning, where the shielding player can be damaged immediately after the shield is disabled")
    public static boolean shieldStunning = false;

    @Rule(desc = "Makes shield stuns use decreased [paper] kb during the window, default 5")
    @Bounds(min = 0)
    public static int shieldStunningWindow = 3;

    @Rule(desc = "Change the delay of bringing up the shield")
    @Bounds(min = 0)
    public static int shieldDelayTicks = 5;

    public enum ExplosionNoDmgMode {
        TRUE, FALSE, MOST;

        public boolean enabled() {
            return this != FALSE;
        }
    }

    @Rule(desc = "Explosions won't destroy blocks")
    public static ExplosionNoDmgMode explosionNoBlockDamage = ExplosionNoDmgMode.FALSE;

    @Rule(desc = "Allows intentional game design explosions (from beds and respawn anchors) to not explode with fire")
    public static boolean explosionNoFire = false;

    @Rule(desc = "Wind Charges won't activate redstone blocks")
    public static boolean windChargeNoTrigger = false;

    @Rule(desc = "Enables editing player nbt, so you can directly edit values within a player's data")
    public static boolean editablePlayerNbt = false;

    @Rule(desc = "If true, makes client players ignore slower tick rates")
    public static boolean clientsIgnoreSlowTickRate = false;

    @Rule(desc = "Players absorb XP instantly, without delay")
    public static boolean xpNoCooldown = false;

    @Rule(desc = "Removes randomness from projectiles while true")
    public static boolean noProjectileRandom = false;

    @Rule(desc = "Chunk Resetting deletes entities within that chunk")
    public static boolean deleteChunkEntities = false;

    @Rule(desc = "Disable moving piston blocks block rain from falling down")
    public static boolean rainThroughMovingPiston = true;

    @Rule(desc = "Shulker Boxes will always drop, regardless if the gamerule noTileDrops is on")
    public static boolean shulkerBoxAlwaysDrops = false;

    @Rule(desc = "Remove the experimental world setting (if on client disables all, if on world, only disables it on this world)")
    public static boolean disableExperimentalScreen = false;
}