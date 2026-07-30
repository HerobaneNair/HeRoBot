package hero.bane.herobot.mod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import hero.bane.herobot.mod.common.HeroBot;
import hero.bane.herobot.mod.common.config.BotNameSuggestions;
import hero.bane.herobot.mod.common.rule.RuleCommandBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public final class HeroBotCommand {
    private static final String versionProblems = "Version Getter Messed Up, ping HerobaneNair or fix fabric.mod";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                Commands.literal("herobot")
                        .executes(HeroBotCommand::version)
                        .then(RuleCommandBuilder.build())
                        .then(Commands.literal("botNameSuggestion")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(HeroBotCommand::addBotNameSuggestion)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .suggests((c, b) -> suggest(BotNameSuggestions.all(), b))
                                                .executes(HeroBotCommand::removeBotNameSuggestion))))
        );
    }

    private static int addBotNameSuggestion(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (BotNameSuggestions.add(name)) {
            context.getSource().sendSuccess(() -> Component.literal("Added bot name suggestion: " + name), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("Bot name suggestion already exists: " + name).withColor(0xFFAA00), false);
        return 0;
    }

    private static int removeBotNameSuggestion(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (BotNameSuggestions.remove(name)) {
            context.getSource().sendSuccess(() -> Component.literal("Removed bot name suggestion: " + name), false);
            return 1;
        }
        context.getSource().sendSuccess(() -> Component.literal("No such bot name suggestion: " + name).withColor(0xFFAA00), false);
        return 0;
    }

    private static int version(CommandContext<CommandSourceStack> context) {
        String pvpBotVersion = FabricLoader.getInstance()
                .getModContainer("herobot")
                .orElseThrow(() -> new IllegalStateException("HeroBot mod container not found. Something went very wrong"))
                .getMetadata().getVersion().getFriendlyString();

        int versionReturned = 0;

        try {
            versionReturned = Integer.parseInt(pvpBotVersion.substring(pvpBotVersion.indexOf('-') + 1, pvpBotVersion.indexOf('+')).replaceAll("\\.", ""));
        } catch (Exception e) {
            context.getSource().sendSuccess(() -> Component.literal(versionProblems).withColor(0xFF5555), false);
            HeroBot.LOGGER.error(versionProblems, e);
        }

        context.getSource().sendSuccess(() -> Component.literal("HeroBotVersion: " + pvpBotVersion), false);
        int finalVersionReturned = versionReturned;
        context.getSource().sendSuccess(() -> Component.literal("Returns: " + finalVersionReturned).withColor(0xAAAAAA), false);
        return finalVersionReturned;
    }
}
