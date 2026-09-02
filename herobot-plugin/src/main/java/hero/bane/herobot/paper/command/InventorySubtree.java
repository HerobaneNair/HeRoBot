package hero.bane.herobot.paper.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import hero.bane.herobot.paper.bot.BotPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.item.ItemStackTemplate;

public final class InventorySubtree {

    private static final SimpleCommandExceptionType NOT_BOT =
            new SimpleCommandExceptionType(Component.literal("Only bot players can be targeted"));
    private static final SimpleCommandExceptionType NO_SCREEN =
            new SimpleCommandExceptionType(Component.literal("Bot does not have a screen open"));
    private static final SimpleCommandExceptionType NO_CONTAINER =
            new SimpleCommandExceptionType(Component.literal("Bot does not have a container open"));
    private static final SimpleCommandExceptionType SCREEN_OPEN =
            new SimpleCommandExceptionType(Component.literal("Cannot do that while a screen is open"));

    public static LiteralArgumentBuilder<CommandSourceStack> buildInventory(CommandBuildContext ctx) {
        return Commands.literal("inventory")
                .executes(InventorySubtree::queryInventory)
                .then(Commands.literal("open")
                        .executes(InventorySubtree::openInventory))
                .then(Commands.literal("close")
                        .executes(InventorySubtree::closeScreen))
                .then(Commands.literal("leave")
                        .executes(InventorySubtree::closeScreen))
                .then(Commands.literal("click")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> slotClick(c, 0, ContainerInput.PICKUP))))
                .then(Commands.literal("rightClick")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> slotClick(c, 1, ContainerInput.PICKUP))))
                .then(Commands.literal("shiftClick")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> slotClick(c, 0, ContainerInput.QUICK_MOVE))))
                .then(Commands.literal("keybindSwap")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .then(Commands.argument("hotbarSlot", IntegerArgumentType.integer(1, 9))
                                        .executes(InventorySubtree::keybindSwap))))
                .then(Commands.literal("swapToOffhand")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(InventorySubtree::swapToOffhand)))
                .then(Commands.literal("throw")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> throwItem(c, false))))
                .then(Commands.literal("throwAll")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> throwItem(c, true))))
                .then(Commands.literal("held")
                        .then(Commands.literal("throw")
                                .executes(InventorySubtree::heldThrow))
                        .then(Commands.literal("drag")
                                .then(Commands.argument("slots", StringArgumentType.word())
                                        .suggests((context, b) -> { b.suggest("1,2,3,4"); return b.buildFuture(); })
                                        .executes(InventorySubtree::heldDrag))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildContainer(CommandBuildContext ctx) {
        return Commands.literal("container")
                .executes(InventorySubtree::queryContainer)
                .then(Commands.literal("close")
                        .executes(InventorySubtree::closeScreen))
                .then(Commands.literal("leave")
                        .executes(InventorySubtree::closeScreen))
                .then(Commands.literal("click")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> containerSlotClick(c, 0, ContainerInput.PICKUP))))
                .then(Commands.literal("rightClick")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> containerSlotClick(c, 1, ContainerInput.PICKUP))))
                .then(Commands.literal("shiftClick")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> containerSlotClick(c, 0, ContainerInput.QUICK_MOVE))))
                .then(Commands.literal("keybindSwap")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .then(Commands.argument("hotbarSlot", IntegerArgumentType.integer(1, 9))
                                        .executes(InventorySubtree::containerKeybindSwap))))
                .then(Commands.literal("swapToOffhand")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(InventorySubtree::containerSwapToOffhand)))
                .then(Commands.literal("throw")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> containerThrowItem(c, false))))
                .then(Commands.literal("throwAll")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(c -> containerThrowItem(c, true))))
                .then(Commands.literal("held")
                        .then(Commands.literal("throw")
                                .executes(InventorySubtree::containerHeldThrow))
                        .then(Commands.literal("drag")
                                .then(Commands.argument("slots", StringArgumentType.greedyString())
                                        .executes(InventorySubtree::containerHeldDrag))))
                .then(Commands.literal("quickLoot")
                        .then(Commands.literal("containerSlot")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                        .executes(c -> quickLoot(c, true))))
                        .then(Commands.literal("inventorySlot")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                        .executes(c -> quickLoot(c, false)))))
                .then(Commands.literal("recipeBook")
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .executes(c -> recipeBook(c, false))))
                .then(Commands.literal("shiftRecipeBook")
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .executes(c -> recipeBook(c, true))));
    }

    private static List<BotPlayer> requireBots(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
        List<BotPlayer> bots = new ArrayList<>();
        for (ServerPlayer p : players) {
            if (!(p instanceof BotPlayer bot)) throw NOT_BOT.create();
            bots.add(bot);
        }
        if (bots.isEmpty()) throw NOT_BOT.create();
        return bots;
    }

    private static AbstractContainerMenu requireInventoryMenu(BotPlayer bot) throws CommandSyntaxException {
        AbstractContainerMenu menu = bot.getActiveMenu();
        if (menu == null) throw NO_SCREEN.create();
        return menu;
    }

    private static AbstractContainerMenu requireContainerMenu(BotPlayer bot) throws CommandSyntaxException {
        if (!bot.isContainerOpen()) throw NO_CONTAINER.create();
        return bot.containerMenu;
    }

    public static void requireNoScreen(BotPlayer bot) throws CommandSyntaxException {
        if (bot.isScreenOpen()) throw SCREEN_OPEN.create();
    }

    private static boolean isValidSlot(AbstractContainerMenu menu, int slot) {
        return slot >= 0 && slot < menu.slots.size();
    }

    private static void syncMenu(AbstractContainerMenu menu) {
        menu.broadcastChanges();
    }

    private static int openInventory(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        for (BotPlayer bot : requireBots(c)) {
            bot.openInventoryScreen();
            c.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " opened inventory"), false);
        }
        return 1;
    }

    private static int closeScreen(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        for (BotPlayer bot : requireBots(c)) {
            bot.closeScreen();
            c.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " closed screen"), false);
        }
        return 1;
    }

    private static int slotClick(CommandContext<CommandSourceStack> c, int button, ContainerInput clickType) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireInventoryMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Index out of bounds, slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, button, clickType, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int keybindSwap(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        int hotbar = IntegerArgumentType.getInteger(c, "hotbarSlot") - 1;
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireInventoryMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, hotbar, ContainerInput.SWAP, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int swapToOffhand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireInventoryMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, 40, ContainerInput.SWAP, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int throwItem(CommandContext<CommandSourceStack> c, boolean all) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireInventoryMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, all ? 1 : 0, ContainerInput.THROW, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int heldThrow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireInventoryMenu(bot);
            menu.clicked(-999, 0, ContainerInput.PICKUP, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int heldDrag(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String slotsStr = StringArgumentType.getString(c, "slots");
        int[] slots = parseSlotList(slotsStr);
        if (slots == null || slots.length == 0) {
            c.getSource().sendFailure(Component.literal("Invalid slot format. Use comma-separated numbers (i.e. 1,2,3,4)"));
            return 0;
        }

        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireInventoryMenu(bot);
            if (menu.getCarried().isEmpty()) {
                c.getSource().sendFailure(Component.literal(bot.getGameProfile().name() + " is not holding anything"));
                return 0;
            }
            executeDrag(menu, bot, slots);
            syncMenu(menu);
        }
        return 1;
    }

    private static int containerSlotClick(CommandContext<CommandSourceStack> c, int button, ContainerInput clickType) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, button, clickType, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int containerKeybindSwap(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        int hotbar = IntegerArgumentType.getInteger(c, "hotbarSlot") - 1;
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, hotbar, ContainerInput.SWAP, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int containerSwapToOffhand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, 40, ContainerInput.SWAP, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int containerThrowItem(CommandContext<CommandSourceStack> c, boolean all) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            menu.clicked(slot, all ? 1 : 0, ContainerInput.THROW, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int containerHeldThrow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            menu.clicked(-999, 0, ContainerInput.PICKUP, bot);
            syncMenu(menu);
        }
        return 1;
    }

    private static int containerHeldDrag(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String slotsStr = StringArgumentType.getString(c, "slots");
        int[] slots = parseSlotList(slotsStr);
        if (slots == null || slots.length == 0) {
            c.getSource().sendFailure(Component.literal("Invalid slot format. Use comma-separated numbers (e.g. 1,2,3,4)"));
            return 0;
        }

        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            if (menu.getCarried().isEmpty()) {
                c.getSource().sendFailure(Component.literal(bot.getGameProfile().name() + " is not holding anything"));
                return 0;
            }
            executeDrag(menu, bot, slots);
            syncMenu(menu);
        }
        return 1;
    }

    private static int quickLoot(CommandContext<CommandSourceStack> c, boolean fromContainer) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(c, "slot");
        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            if (!isValidSlot(menu, slot)) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is out of range (0-" + (menu.slots.size() - 1) + ")"));
                return 0;
            }
            ItemStack reference = menu.getSlot(slot).getItem();
            if (reference.isEmpty()) {
                c.getSource().sendFailure(Component.literal("Slot " + slot + " is empty"));
                return 0;
            }

            int moved = 0;
            for (int i = 0; i < menu.slots.size(); i++) {
                ItemStack slotItem = menu.getSlot(i).getItem();
                if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, reference)) {
                    boolean isContainerSlot = isContainerSlot(menu, i);
                    if (fromContainer && isContainerSlot || !fromContainer && !isContainerSlot) {
                        menu.clicked(i, 0, ContainerInput.QUICK_MOVE, bot);
                        moved++;
                    }
                }
            }
            syncMenu(menu);

            int finalMoved = moved;
            c.getSource().sendSuccess(() -> Component.literal("Quick-looted " + finalMoved + " slot(s)"), false);
        }
        return 1;
    }

    private static boolean isContainerSlot(AbstractContainerMenu menu, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
        return !(menu.getSlot(slotIndex).container instanceof Inventory);
    }

    private static int recipeBook(CommandContext<CommandSourceStack> c, boolean useMaxItems) throws CommandSyntaxException {
        Item targetItem = ItemArgument.getItem(c, "item").item().value();
        String itemName = BuiltInRegistries.ITEM.getKey(targetItem).toString();

        for (BotPlayer bot : requireBots(c)) {
            AbstractContainerMenu menu = requireContainerMenu(bot);
            if (!(menu instanceof CraftingMenu craftingMenu)) {
                c.getSource().sendFailure(Component.literal("recipeBook only works in crafting tables"));
                return 0;
            }

            RecipeHolder<?> found = findCraftingRecipe(bot, targetItem);

            if (found == null) {
                c.getSource().sendFailure(Component.literal("No matching crafting recipe found for " + itemName));
                return 0;
            }

            bot.getRecipeBook().add(found.id());

            ServerLevel serverLevel = bot.level();
            craftingMenu.handlePlacement(useMaxItems, true, found, serverLevel, bot.getInventory());
            syncMenu(menu);

            c.getSource().sendSuccess(() -> Component.literal("Placed recipe for " + itemName), false);
        }
        return 1;
    }

    public static RecipeHolder<?> findCraftingRecipe(ServerPlayer player, Item targetItem) {
        boolean limitedCrafting = player.level().getGameRules().get(GameRules.LIMITED_CRAFTING);

        var recipeManager = player.level().getServer().getRecipeManager();
        for (var holder : recipeManager.getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe)) continue;

            var displays = holder.value().display();
            boolean matches = false;
            for (var display : displays) {
                if (display.result() instanceof SlotDisplay.ItemSlotDisplay(
                        Holder<Item> item1
                )) {
                    if (item1.value() == targetItem) {
                        matches = true;
                        break;
                    }
                } else if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate template)) {
                    if (template.typeHolder().value() == targetItem) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches) continue;

            if (limitedCrafting && !player.getRecipeBook().contains(holder.id())) {
                continue;
            }
            return holder;
        }
        return null;
    }

    public static void executeDrag(AbstractContainerMenu menu, ServerPlayer player, int[] slots) {
        executeDrag(menu, player, slots, 0);
    }

    public static void executeDrag(AbstractContainerMenu menu, ServerPlayer player, int[] slots, int type) {
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, type), ContainerInput.QUICK_CRAFT, player);
        for (int slot : slots) {
            if (isValidSlot(menu, slot)) {
                menu.clicked(slot, AbstractContainerMenu.getQuickcraftMask(1, type), ContainerInput.QUICK_CRAFT, player);
            }
        }
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, type), ContainerInput.QUICK_CRAFT, player);
    }

    private static int queryInventory(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        for (BotPlayer bot : requireBots(c)) {
            boolean open = bot.isScreenOpen();
            String name = bot.getGameProfile().name();
            c.getSource().sendSuccess(() -> Component.literal(name + (open ? " has inventory open" : " does not have inventory open")), false);
            return open ? 1 : 0;
        }
        return 0;
    }

    private static int queryContainer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        for (BotPlayer bot : requireBots(c)) {
            boolean open = bot.isContainerOpen();
            String name = bot.getGameProfile().name();
            if (open) {
                int slotCount = bot.containerMenu.slots.size();
                String menuType = bot.containerMenu.getClass().getSimpleName();
                c.getSource().sendSuccess(() -> Component.literal(name + " has " + menuType + " open (" + slotCount + " slots)"), false);
                c.getSource().sendSuccess(() -> Component.literal("Returns: " + slotCount).withColor(0xAAAAAA), false);
                return slotCount;
            } else {
                c.getSource().sendSuccess(() -> Component.literal(name + " does not have a container open"), false);
                c.getSource().sendSuccess(() -> Component.literal("Returns: 0").withColor(0xAAAAAA), false);
                return 0;
            }
        }
        return 0;
    }

    public static int[] parseSlotList(String input) {
        String[] parts = input.trim().split(",");
        List<Integer> slots = new ArrayList<>();
        for (String raw : parts) {
            String part = raw.trim();
            if (part.isEmpty()) continue;
            try {
                int dash = part.indexOf('-', 1);
                if (dash > 0) {
                    int from = Integer.parseInt(part.substring(0, dash).trim());
                    int to = Integer.parseInt(part.substring(dash + 1).trim());
                    if (Math.abs(to - from) > 1000) return null;
                    if (from <= to) for (int i = from; i <= to; i++) slots.add(i);
                    else for (int i = from; i >= to; i--) slots.add(i);
                } else {
                    slots.add(Integer.parseInt(part));
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
