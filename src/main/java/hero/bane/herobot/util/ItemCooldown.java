package hero.bane.herobot.util;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.mixin.CooldownInstanceInterface;
import hero.bane.herobot.mixin.ItemCooldownsInterface;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.UseCooldown;

import java.util.List;

public final class ItemCooldown {

    public static int itemCdClearAll(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        var players = EntityArgument.getPlayers(ctx, "targets");

        int numCooldownsReset = 0;

        for (var p : players) {
            int resetted = ItemCooldown.clearAll(p);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    p.getName().getString() + " reset " +
                            (resetted > 0 ? resetted : "no") +
                            " item cooldown" + (resetted == 1 ? "" : "s")
            ), false);
            numCooldownsReset += resetted;
        }
        return numCooldownsReset;
    }

    public static int itemCdAsk(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        var players = EntityArgument.getPlayers(ctx, "targets");
        var item = ItemArgument.getItem(ctx, "item").getItem();
        String itemName = BuiltInRegistries.ITEM.getKey(item).getPath();

        int last = 0;

        for (var p : players) {
            last = ItemCooldown.getRemaining(p, item);

            if (last > 0) {
                int finalLast = last;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        p.getName().getString() + " " +
                                itemName + " cd: " + finalLast + " ticks remaining"
                ), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        p.getName().getString() + " " +
                                itemName + " cd: ready"
                ), false);
            }
        }

        return last;
    }

    public static int itemCdReset(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        var players = EntityArgument.getPlayers(ctx, "targets");
        var item = ItemArgument.getItem(ctx, "item").getItem();
        String itemName = BuiltInRegistries.ITEM.getKey(item).getPath();

        for (var p : players) {
            ItemCooldown.clear(p, item);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Reset " + p.getName().getString() + "'s " + itemName + " cooldown"
            ), false);
        }

        return players.size();
    }

    public static int itemCdSetDefault(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        var players = EntityArgument.getPlayers(ctx, "targets");
        var item = ItemArgument.getItem(ctx, "item").getItem();
        String itemName = BuiltInRegistries.ITEM.getKey(item).getPath();

        for (var p : players) {
            if (ItemCooldown.setDefault(p, item)) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Applied default cooldown to " +
                                p.getName().getString() + "'s " + itemName
                ), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        itemName + " has no default cooldown"
                ), false);
            }
        }

        return players.size();
    }

    public static int itemCdSetCustom(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        var players = EntityArgument.getPlayers(ctx, "targets");
        var item = ItemArgument.getItem(ctx, "item").getItem();
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        String itemName = BuiltInRegistries.ITEM.getKey(item).getPath();

        for (var p : players) {
            ItemCooldown.set(p, item, ticks);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Applied cooldown of " + ticks + " to " +
                            p.getName().getString() + "'s " + itemName
            ), false);
        }

        return players.size();
    }

    private static int clearAll(ServerPlayer player) {
        int c = 0;
        ItemCooldowns cooldowns = player.getCooldowns();
        ItemCooldownsInterface accessor = (ItemCooldownsInterface) cooldowns;
        try {
            for (Identifier id : List.copyOf(accessor.getCooldowns().keySet())) {
                c++;
                cooldowns.removeCooldown(id);
            }
        } catch (Exception e) {
            System.out.println("Tell HerobaneNair that this happened and what item you used");
        }

        return c;
    }

    private static void clear(ServerPlayer player, Item item) {
        player.getCooldowns().removeCooldown(group(item));
    }

    private static int getRemaining(ServerPlayer player, Item item) {
        ItemCooldowns cooldowns = player.getCooldowns();

        ItemCooldownsInterface accessor = (ItemCooldownsInterface) cooldowns;
        Object inst = accessor.getCooldowns().get(group(item));
        if (inst == null) return 0;

        int end = ((CooldownInstanceInterface) inst).getEndTime();
        int now = accessor.getTickCount();

        return Math.max(0, end - now);
    }

    private static boolean setDefault(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        UseCooldown cd = stack.get(DataComponents.USE_COOLDOWN);
        if (stack.getItem() == Items.SHIELD) {
            shieldDisabler(player, item, 100);
            return true;
        }
        if (cd == null) return false;

        Identifier group =
                cd.cooldownGroup().orElse(BuiltInRegistries.ITEM.getKey(item));

        player.getCooldowns().addCooldown(group, cd.ticks());
        return true;
    }

    private static void set(ServerPlayer player, Item item, int ticks) {
        if ((new ItemStack(item)).getItem() == Items.SHIELD) {
            shieldDisabler(player, item, ticks);
            return;
        }
        player.getCooldowns().addCooldown(group(item), ticks);
    }

    private static void shieldDisabler(ServerPlayer player, Item item, int cdTicks) {
        player.getCooldowns().addCooldown(group(item), cdTicks);
        if (player.isUsingItem()) {
            ItemStack shield = player.getUseItem();
            BlocksAttacks blocksAttacks = shield.get(DataComponents.BLOCKS_ATTACKS);

            if (blocksAttacks != null) {
                blocksAttacks.disable(
                        (ServerLevel) player.level(),
                        player,
                        cdTicks / 20F,
                        shield
                );
            }
        }
    }

    private static Identifier group(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}