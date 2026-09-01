package hero.bane.herobot.paper.ai.runtime.executors;

import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.paper.ai.runtime.Executor;
import hero.bane.herobot.paper.ai.runtime.ParamEval;
import hero.bane.herobot.paper.ai.runtime.Reporter;
import hero.bane.herobot.paper.ai.runtime.ScriptRunner;
import hero.bane.herobot.common.ai.runtime.StepResult;
import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.command.InventorySubtree;
import hero.bane.herobot.paper.command.TradeOps;
import hero.bane.herobot.paper.control.ControlOp;
import hero.bane.herobot.paper.control.PlayerControllers;
import hero.bane.herobot.paper.control.RemoteOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Map;

public final class InventoryExecutor {
    private InventoryExecutor() {}

    public static void register(Map<BlockType, Executor> flow, Map<BlockType, Reporter> reporter) {
        flow.put(BlockType.SELECT_HOTBAR, (b, r, br) -> {
            int slot = Math.clamp(ParamEval.evalInt(b, "slot", r, br), 1, 9);
            PlayerControllers.of(r.player()).setSlot(slot);
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.OPEN_INVENTORY, (b, r, br) -> {
            BotPlayer bot = r.bot();
            if (bot != null) bot.openInventoryScreen();
            else RemoteOps.send(r.player(), ControlOp.openInventory());
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.CLOSE_SCREEN, (b, r, br) -> {
            BotPlayer bot = r.bot();
            if (bot != null) {
                bot.closeScreen();
            } else {
                ServerPlayer player = r.player();
                if (!RemoteOps.send(player, ControlOp.closeScreen())
                        && player.containerMenu != player.inventoryMenu) {
                    player.closeContainer();
                }
            }
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.HANDEDNESS, (b, r, br) -> {
            BotPlayer bot = r.bot();
            String side = ParamEval.evalString(b, "side", r, br);
            boolean left = "left".equalsIgnoreCase(side);
            if (bot != null) bot.setMainHand(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
            else RemoteOps.send(r.player(), ControlOp.setMainHand(left));
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.INV_CLICK, (b, r, br) -> {
            AbstractContainerMenu menu = menuFor(r, ParamEval.evalString(b, "menu", r, br));
            int slot = ParamEval.evalInt(b, "slot", r, br);
            if (menu == null || !validSlot(menu, slot)) return StepResult.continueVia(0);
            String mode = ParamEval.evalString(b, "mode", r, br);
            ServerPlayer p = r.player();
            switch (mode == null ? "click" : mode) {
                case "rightClick" -> menu.clicked(slot, 1, ClickType.PICKUP, p);
                case "shiftClick" -> menu.clicked(slot, 0, ClickType.QUICK_MOVE, p);
                case "throw" -> menu.clicked(slot, 0, ClickType.THROW, p);
                case "throwAll" -> menu.clicked(slot, 1, ClickType.THROW, p);
                default -> menu.clicked(slot, 0, ClickType.PICKUP, p);
            }
            menu.broadcastChanges();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.INV_SWAP_HOTBAR, (b, r, br) -> {
            AbstractContainerMenu menu = menuFor(r, ParamEval.evalString(b, "menu", r, br));
            int slot = ParamEval.evalInt(b, "slot", r, br);
            if (menu == null || !validSlot(menu, slot)) return StepResult.continueVia(0);
            String with = ParamEval.evalString(b, "with", r, br);
            int button = "offhand".equalsIgnoreCase(with) ? 40 : Math.clamp(ParamEval.asInt(with), 1, 9) - 1;
            menu.clicked(slot, button, ClickType.SWAP, r.player());
            menu.broadcastChanges();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.INV_HELD_THROW, (b, r, br) -> {
            AbstractContainerMenu menu = menuFor(r, ParamEval.evalString(b, "menu", r, br));
            if (menu == null) return StepResult.continueVia(0);
            menu.clicked(-999, 0, ClickType.PICKUP, r.player());
            menu.broadcastChanges();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.INV_HELD_DRAG, (b, r, br) -> {
            AbstractContainerMenu menu = menuFor(r, ParamEval.evalString(b, "menu", r, br));
            if (menu == null || menu.getCarried().isEmpty()) return StepResult.continueVia(0);
            int[] slots = InventorySubtree.parseSlotList(ParamEval.evalString(b, "slots", r, br));
            if (slots == null || slots.length == 0) return StepResult.continueVia(0);
            String button = ParamEval.evalString(b, "button", r, br);
            InventorySubtree.executeDrag(menu, r.player(), slots, "right".equalsIgnoreCase(button) ? 1 : 0);
            menu.broadcastChanges();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.QUICK_LOOT, (b, r, br) -> {
            AbstractContainerMenu menu = menuFor(r, "container");
            int slot = ParamEval.evalInt(b, "slot", r, br);
            if (menu == null || !validSlot(menu, slot)) return StepResult.continueVia(0);
            ItemStack reference = menu.getSlot(slot).getItem();
            if (reference.isEmpty()) return StepResult.continueVia(0);
            boolean fromContainer = !"inventory".equals(ParamEval.evalString(b, "from", r, br));
            ServerPlayer p = r.player();
            for (int i = 0; i < menu.slots.size(); i++) {
                ItemStack slotItem = menu.getSlot(i).getItem();
                if (slotItem.isEmpty() || !ItemStack.isSameItemSameComponents(slotItem, reference)) continue;
                boolean isContainerSlot = !(menu.getSlot(i).container instanceof Inventory);
                if (fromContainer == isContainerSlot) {
                    menu.clicked(i, 0, ClickType.QUICK_MOVE, p);
                }
            }
            menu.broadcastChanges();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.RECIPE_BOOK, (b, r, br) -> {
            if (!(menuFor(r, "container") instanceof CraftingMenu craftingMenu)) return StepResult.continueVia(0);
            Item item = DataExecutor.asItemStack(ParamEval.raw(b, "item", r, br), r).getItem();
            if (item == Items.AIR) return StepResult.continueVia(0);
            ServerPlayer p = r.player();
            RecipeHolder<?> found = InventorySubtree.findCraftingRecipe(p, item);
            if (found == null) return StepResult.continueVia(0);
            Object modeVal = ParamEval.raw(b, "mode", r, br);
            boolean max = modeVal instanceof Boolean bb ? bb : "max".equalsIgnoreCase(ParamEval.asString(modeVal));
            p.getRecipeBook().add(found.id());
            craftingMenu.handlePlacement(max, true, found, p.level(), p.getInventory());
            craftingMenu.broadcastChanges();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.TRADE_SELECT, (b, r, br) -> {
            BotPlayer bot = r.bot();
            MerchantMenu menu = TradeOps.merchantMenu(r.player());
            if (bot != null && menu != null) {
                int idx = ParamEval.evalInt(b, "index", r, br) - 1;
                if (TradeOps.validIndex(menu, idx)) {
                    bot.setSelectedTradeIndex(idx);
                    TradeOps.loadInputs(menu, idx);
                }
            }
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.TRADE_RESTOCK, (b, r, br) -> {
            BotPlayer bot = r.bot();
            MerchantMenu menu = TradeOps.merchantMenu(r.player());
            if (bot != null && menu != null) {
                int idx = bot.getSelectedTradeIndex();
                if (TradeOps.validIndex(menu, idx)) TradeOps.loadInputs(menu, idx);
            }
            return StepResult.continueVia(0);
        });

        reporter.put(BlockType.INVENTORY_OPEN, (b, r, br) -> {
            BotPlayer bot = r.bot();
            if (bot != null) {
                if (bot.isContainerOpen()) return 2;
                return bot.isScreenOpen() ? 1 : 0;
            }
            ServerPlayer p = r.player();
            return p.containerMenu != p.inventoryMenu ? 2 : 0;
        });

        reporter.put(BlockType.CONTAINER_SIZE, (b, r, br) -> {
            AbstractContainerMenu menu = menuFor(r, "container");
            return menu == null ? 0 : menu.slots.size();
        });

        reporter.put(BlockType.TRADE_CHECK, (b, r, br) -> {
            MerchantMenu menu = TradeOps.merchantMenu(r.player());
            if (menu == null) return TradeOps.NO_TRADE;
            int param = ParamEval.evalInt(b, "index", r, br);
            int idx;
            if (param <= 0) {
                BotPlayer bot = r.bot();
                idx = bot != null ? bot.getSelectedTradeIndex() : -1;
            } else {
                idx = param - 1;
            }
            return TradeOps.check(r.player(), menu, idx);
        });
    }

    private static AbstractContainerMenu menuFor(ScriptRunner r, String which) {
        boolean container = "container".equals(which);
        BotPlayer bot = r.bot();
        if (bot != null) {
            if (container) return bot.isContainerOpen() ? bot.containerMenu : null;
            return bot.getActiveMenu();
        }
        ServerPlayer p = r.player();
        boolean hasContainer = p.containerMenu != p.inventoryMenu;
        if (container) return hasContainer ? p.containerMenu : null;
        return hasContainer ? null : p.inventoryMenu;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean validSlot(AbstractContainerMenu menu, int slot) {
        return slot >= 0 && slot < menu.slots.size();
    }
}
