package hero.bane.herobot.paper.rule;

import hero.bane.herobot.common.rule.HeroBotSettings;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatRules implements Listener {

    private static final double STUNNED_KNOCKBACK_SCALE = 0.4;

    private static final Map<UUID, Long> SHIELD_DISABLED_TICK = new HashMap<>();

    public static void tick(MinecraftServer server) {
        int wanted = HeroBotSettings.shieldDelayTicks;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyBlockDelay(player.getMainHandItem(), wanted);
            applyBlockDelay(player.getOffhandItem(), wanted);
        }
    }

    public static void forget(UUID playerId) {
        SHIELD_DISABLED_TICK.remove(playerId);
    }

    public static void clear() {
        SHIELD_DISABLED_TICK.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent event) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        SHIELD_DISABLED_TICK.put(event.getPlayer().getUniqueId(), (long) server.getTickCount());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!usesVanillaFormula(event.getCause())) return;

        double horizontalScale = horizontalScale(event.getEntity());
        if (horizontalScale >= 1.0 && HeroBotSettings.kbScaling) return;

        net.minecraft.world.entity.Entity handle = ((CraftEntity) event.getEntity()).getHandle();
        Vec3 velocity = handle.getDeltaMovement();
        Vector knockback = event.getKnockback();

        double pushX = -(knockback.getX() + velocity.x / 2.0);
        double pushZ = -(knockback.getZ() + velocity.z / 2.0);
        double strength = Math.sqrt(pushX * pushX + pushZ * pushZ);

        double verticalVelocity = HeroBotSettings.kbScaling ? velocity.y : 0.0;
        boolean grounded = handle.onGround() || !HeroBotSettings.kbScaling;

        double x = velocity.x / 2.0 - pushX * horizontalScale;
        double y = grounded ? Math.min(0.4, verticalVelocity / 2.0 + strength) : verticalVelocity;
        double z = velocity.z / 2.0 - pushZ * horizontalScale;

        event.setKnockback(new Vector(x - velocity.x, y - velocity.y, z - velocity.z));
    }

    private static boolean usesVanillaFormula(EntityKnockbackEvent.Cause cause) {
        return cause == EntityKnockbackEvent.Cause.DAMAGE
                || cause == EntityKnockbackEvent.Cause.ENTITY_ATTACK
                || cause == EntityKnockbackEvent.Cause.SHIELD_BLOCK
                || cause == EntityKnockbackEvent.Cause.SWEEP_ATTACK;
    }

    private static double horizontalScale(Entity entity) {
        if (!HeroBotSettings.shieldStunning) return 1.0;
        if (HeroBotSettings.shieldStunningWindow <= 0) return 1.0;
        if (!(entity instanceof Player player)) return 1.0;

        Long disabledTick = SHIELD_DISABLED_TICK.get(player.getUniqueId());
        if (disabledTick == null) return 1.0;

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return 1.0;

        long elapsed = server.getTickCount() - disabledTick;
        if (elapsed < 0 || elapsed > HeroBotSettings.shieldStunningWindow) return 1.0;
        return STUNNED_KNOCKBACK_SCALE;
    }

    private static void applyBlockDelay(ItemStack stack, int wanted) {
        BlocksAttacks current = stack.get(DataComponents.BLOCKS_ATTACKS);
        if (current == null) return;

        BlocksAttacks prototype = stack.getItem().components().get(DataComponents.BLOCKS_ATTACKS);
        if (prototype != null && prototype.blockDelayTicks() == wanted && matchesExceptDelay(current, prototype)) {
            DataComponentPatch patch = stack.getComponentsPatch();
            DataComponentPatch cleaned = patch.forget(type -> type == DataComponents.BLOCKS_ATTACKS);
            if (cleaned.size() != patch.size()) stack.restorePatch(cleaned);
            return;
        }

        if (current.blockDelayTicks() == wanted) return;

        stack.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(
                wanted / 20.0F,
                current.disableCooldownScale(),
                current.damageReductions(),
                current.itemDamage(),
                current.bypassedBy(),
                current.blockSound(),
                current.disableSound()));
    }

    private static boolean matchesExceptDelay(BlocksAttacks current, BlocksAttacks prototype) {
        return current.disableCooldownScale() == prototype.disableCooldownScale()
                && current.damageReductions().equals(prototype.damageReductions())
                && current.itemDamage().equals(prototype.itemDamage())
                && current.bypassedBy().equals(prototype.bypassedBy())
                && current.blockSound().equals(prototype.blockSound())
                && current.disableSound().equals(prototype.disableSound());
    }
}
