package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.mod.common.HeroBotSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void forceShulkerDrop(Level level, Supplier<ItemEntity> supplier, ItemStack itemStack, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!HeroBotSettings.shulkerBoxAlwaysDrops) return;
        if (serverLevel.getGameRules().get(GameRules.BLOCK_DROPS)) return;

        boolean isShulker = itemStack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
        if (!isShulker || itemStack.isEmpty()) return;

        ItemEntity itemEntity = supplier.get();
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
        ci.cancel();
    }
}