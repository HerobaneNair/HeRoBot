package hero.bane.herobot.paper.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.paper.bot.BotPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;

public final class TradeSubtree {

    private TradeSubtree() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("trade")
                .then(Commands.literal("select")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(TradeSubtree::select)))
                .then(Commands.literal("restock")
                        .executes(TradeSubtree::restock))
                .then(Commands.literal("check")
                        .executes(c -> check(c, -1))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(c -> check(c, IntegerArgumentType.getInteger(c, "index") - 1))));
    }

    private static int select(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int index = IntegerArgumentType.getInteger(c, "index");
        int idx = index - 1;
        int selected = 0;

        for (BotPlayer bot : CommandHelper.requireBotTargets(c)) {
            MerchantMenu menu = requireMerchant(c, bot);
            if (menu == null) continue;
            if (!TradeOps.validIndex(menu, idx)) {
                c.getSource().sendFailure(Component.literal(
                        botName(bot) + ": trade " + index + " is out of range (1-" + menu.getOffers().size() + ")"));
                continue;
            }
            bot.setSelectedTradeIndex(idx);
            TradeOps.loadInputs(menu, idx);
            c.getSource().sendSuccess(() -> Component.literal(botName(bot) + " selected trade " + index), false);
            selected++;
        }
        return selected;
    }

    private static int restock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int restocked = 0;
        for (BotPlayer bot : CommandHelper.requireBotTargets(c)) {
            MerchantMenu menu = requireMerchant(c, bot);
            if (menu == null) continue;
            int idx = requireSelectedIndex(c, bot, menu);
            if (idx < 0) continue;
            TradeOps.loadInputs(menu, idx);
            c.getSource().sendSuccess(() -> Component.literal(botName(bot) + " refilled the trade inputs"), false);
            restocked++;
        }
        return restocked;
    }

    private static int check(CommandContext<CommandSourceStack> c, int explicitIdx) throws CommandSyntaxException {
        int last = TradeOps.NO_TRADE;
        for (BotPlayer bot : CommandHelper.requireBotTargets(c)) {
            MerchantMenu menu = requireMerchant(c, bot);
            if (menu == null) {
                last = TradeOps.NO_TRADE;
                continue;
            }
            int idx = explicitIdx >= 0 ? explicitIdx : bot.getSelectedTradeIndex();
            int status = TradeOps.check(bot, menu, idx);
            last = status;
            c.getSource().sendSuccess(() -> Component.literal(botName(bot) + " trade check: " + status), false);
        }
        return last;
    }

    private static MerchantMenu requireMerchant(CommandContext<CommandSourceStack> c, BotPlayer bot) {
        if (bot.isContainerOpen() && bot.containerMenu instanceof MerchantMenu menu) {
            return menu;
        }
        c.getSource().sendFailure(Component.literal(botName(bot) + " does not have a trading menu open"));
        return null;
    }

    private static int requireSelectedIndex(CommandContext<CommandSourceStack> c, BotPlayer bot, MerchantMenu menu) {
        int idx = bot.getSelectedTradeIndex();
        if (idx < 0) {
            c.getSource().sendFailure(Component.literal(
                    botName(bot) + " has no trade selected (use trade select <index> first)"));
            return -1;
        }
        if (!TradeOps.validIndex(menu, idx)) {
            c.getSource().sendFailure(Component.literal(
                    botName(bot) + " selected trade " + (idx + 1) + " is no longer available"));
            return -1;
        }
        return idx;
    }

    private static String botName(BotPlayer bot) {
        return bot.getGameProfile().name();
    }
}
