package hero.bane.herobot.paper.rule;

import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
import org.bukkit.util.Vector;

public final class WorldRules implements Listener {

    private static final double AIM_SNAP_COSINE = 0.9945;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!HeroBotSettings.shulkerBoxAlwaysDrops) return;
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        Block block = event.getBlock();
        ServerLevel level = ((CraftWorld) block.getWorld()).getHandle();
        if (level.getGameRules().get(GameRules.BLOCK_DROPS)) return;

        BlockState state = ((CraftBlock) block).getNMS();
        if (!(state.getBlock() instanceof ShulkerBoxBlock)) return;

        BlockPos pos = ((CraftBlock) block).getPosition();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        for (ItemStack stack : net.minecraft.world.level.block.Block.getDrops(state, level, pos, blockEntity)) {
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;
            if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) continue;

            ItemEntity itemEntity = new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            itemEntity.setDefaultPickUpDelay();
            level.addFreshEntity(itemEntity);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!HeroBotSettings.noProjectileRandom) return;

        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof LivingEntity shooter)) return;
        if (!(((CraftEntity) projectile).getHandle()
                instanceof net.minecraft.world.entity.projectile.Projectile handle)) return;

        Vector velocity = projectile.getVelocity();
        double speed = velocity.length();
        if (speed < 1.0E-5) return;

        Vector aim = shooter.getEyeLocation().getDirection();
        if (velocity.clone().multiply(1.0 / speed).dot(aim) < AIM_SNAP_COSINE) return;

        handle.shoot(aim.getX(), aim.getY(), aim.getZ(), (float) speed, 0.0F);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExpCooldown(PlayerExpCooldownChangeEvent event) {
        if (!HeroBotSettings.xpNoCooldown) return;
        if (event.getReason() != PlayerExpCooldownChangeEvent.ChangeReason.PICKUP_ORB) return;
        event.setNewCooldown(0);
    }
}
