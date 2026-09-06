package hero.bane.herobot.common.rule;

public final class HeroBotSettings {
    private HeroBotSettings() {
    }

    public static boolean serverHasHeroBot = true;

    @Rule(desc = "Creative No Clip, allows to client player to phase through blocks")
    public static boolean creativeNoClip = false;

    @Rule(desc = "Changes creative flying speed multiplier (Default 1.0), how quickly the client flies")
    @Bounds(min = 0.0)
    public static double creativeFlySpeed = 1.0;

    @Rule(desc = "Changes creative air drag (Default 0.09), how quickly the air stops the client while flying")
    @Bounds(min = 0.0, max = 1.0)
    public static double creativeFlyDrag = 0.09;

    @Rule(desc = "Spawn offline players in online mode if online-mode player with specified name does not exist")
    public static boolean allowSpawningOfflinePlayers = true;

    @Rule(desc = "Allows listing bot players on the multiplayer screen")
    public static boolean allowListingBotPlayers = true;

    @Rule(desc = "Change the ping to tick conversion for Bot Players (default 25)")
    @Bounds(min = 1)
    public static int botPingToTicks = 25;

    @Rule(desc = "Ticks between tab list ping updates (default 600, so 20 refreshes the tab list every second)")
    @Bounds(min = 20)
    public static int tabListPing = 600;

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

    @Rule(desc = "Disable knockback scaling, as in entities will take the same vertical knockback regardless of their previous vertical velocity")
    public static boolean kbScaling = true;

    @Rule(desc = "Change how many ticks the hurt animation/red flash and damage cooldown last when any living entity is damaged (default 10)")
    @Bounds(min = 1)
    public static int damageTicks = 10;

    public static int damageInvulnerableTicks() {
        return damageTicks * 2;
    }

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

    @Rule(desc = "Shulker Boxes will always drop, regardless if the gamerule noTileDrops is on")
    public static boolean shulkerBoxAlwaysDrops = false;

    @Rule(desc = "Remove the experimental world setting (if on client disables all, if on world, only disables it on this world)")
    public static boolean disableExperimentalScreen = false;
}
