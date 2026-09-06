package hero.bane.herobot.paper.rule;

import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.common.rule.HeroBotSettings.ExplosionNoDmgMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;

public final class ExplosionRules implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (HeroBotSettings.windChargeNoTrigger && event.getEntity() instanceof WindCharge) {
            event.blockList().clear();
            return;
        }
        filterExplodedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplodedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!HeroBotSettings.explosionNoFire) return;
        if (event.getCause() != BlockIgniteEvent.IgniteCause.EXPLOSION) return;
        event.setCancelled(true);
    }

    private static void filterExplodedBlocks(List<Block> blocks) {
        ExplosionNoDmgMode mode = HeroBotSettings.explosionNoBlockDamage;
        if (!mode.enabled()) return;

        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            BlockState state = ((CraftBlock) block).getNMS();

            boolean remove = switch (mode) {
                case TRUE -> !state.isAir() && state.getBlock() != Blocks.FIRE;
                case MOST -> removeOnMost(block, state);
                default -> false;
            };

            if (remove) iterator.remove();
        }
    }

    private static boolean removeOnMost(Block block, BlockState state) {
        if (state.isSignalSource()) return true;
        if (state.getBlock() == Blocks.GLOWSTONE || state.getBlock() == Blocks.COBWEB) return false;

        ServerLevel level = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos pos = ((CraftBlock) block).getPosition();
        return !state.getCollisionShape(level, pos).isEmpty();
    }
}
