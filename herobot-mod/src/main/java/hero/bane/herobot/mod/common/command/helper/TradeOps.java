package hero.bane.herobot.mod.common.command.helper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

public final class TradeOps {
    public static final int NO_TRADE = -3;

    private TradeOps() {}

    public static MerchantMenu merchantMenu(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu && player.containerMenu instanceof MerchantMenu menu) {
            return menu;
        }
        return null;
    }

    public static boolean validIndex(MerchantMenu menu, int idx) {
        return idx >= 0 && idx < menu.getOffers().size();
    }

    public static void loadInputs(MerchantMenu menu, int idx) {
        menu.setSelectionHint(idx);
        menu.tryMoveItems(idx);
        menu.broadcastChanges();
    }

    public static int check(ServerPlayer player, MerchantMenu menu, int idx) {
        if (!validIndex(menu, idx)) return NO_TRADE;
        MerchantOffer offer = menu.getOffers().get(idx);
        boolean locked = offer.isOutOfStock();
        boolean affordable = canAfford(player, offer);
        if (locked) return affordable ? -1 : -2;
        return affordable ? 1 : 0;
    }

    private static boolean canAfford(ServerPlayer player, MerchantOffer offer) {
        if (countMatching(player, offer.getItemCostA()) < offer.getCostA().getCount()) return false;
        Optional<ItemCost> costB = offer.getItemCostB();
        return !(costB.isPresent() && countMatching(player, costB.get()) < offer.getCostB().getCount());
    }

    private static int countMatching(ServerPlayer player, ItemCost cost) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && cost.test(stack)) total += stack.getCount();
        }
        return total;
    }
}
