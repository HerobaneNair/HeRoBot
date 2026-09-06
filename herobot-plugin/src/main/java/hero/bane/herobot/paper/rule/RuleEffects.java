package hero.bane.herobot.paper.rule;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import hero.bane.herobot.common.rule.HeroBotSettings;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class RuleEffects implements Listener {

    private static final float VANILLA_FLY_SPEED = 0.1F;

    public static void tickPlayer(ServerPlayer player) {
        Player bukkitPlayer = player.getBukkitEntity();
        applyFlySpeed(bukkitPlayer);
        applyDamageTicks(bukkitPlayer);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFailMove(PlayerFailMoveEvent event) {
        if (!HeroBotSettings.creativeNoClip) return;
        if (event.getFailReason() != PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE || !player.isFlying()) return;

        event.setLogWarning(false);
        event.setAllowed(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyFlySpeed(event.getPlayer());
        applyDamageTicks(event.getPlayer());
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) applyDamageTicks(living);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity living) applyDamageTicks(living);
    }

    private static void applyDamageTicks(LivingEntity entity) {
        int wanted = HeroBotSettings.damageInvulnerableTicks();
        if (entity.getMaximumNoDamageTicks() != wanted) entity.setMaximumNoDamageTicks(wanted);
    }

    private static void applyFlySpeed(Player player) {
        float wanted = (float) (VANILLA_FLY_SPEED * HeroBotSettings.creativeFlySpeed);
        if (wanted > 1.0F) wanted = 1.0F;
        if (wanted < -1.0F) wanted = -1.0F;
        if (player.getFlySpeed() != wanted) player.setFlySpeed(wanted);
    }
}
