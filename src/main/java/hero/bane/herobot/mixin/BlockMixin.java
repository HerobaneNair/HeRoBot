package hero.bane.herobot.mixin;

import hero.bane.herobot.HeroBotSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.function.Supplier;

@Mixin(Block.class)
public abstract class BlockMixin {

    /**
     * @author HerobaneNair
     * @reason Allows shulkers to be picked up even when tile drops are off
     */
    @Overwrite
    private static void popResource(Level level, Supplier<ItemEntity> supplier, ItemStack itemStack) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (itemStack.isEmpty()) return;

        boolean blockDrops = serverLevel.getGameRules().get(GameRules.BLOCK_DROPS);

        boolean isShulker =
                itemStack.getItem() instanceof BlockItem blockItem &&
                        blockItem.getBlock() instanceof ShulkerBoxBlock;

        if (!blockDrops && !(HeroBotSettings.shulkerBoxAlwaysDrops && isShulker)) {
            return;
        }

        ItemEntity itemEntity = supplier.get();
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }
}