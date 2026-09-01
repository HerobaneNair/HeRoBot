package hero.bane.herobot.paper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.config.BotNameSuggestions;
import hero.bane.herobot.paper.rule.RuleCommandBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public final class HeroBotCommand {

    private static final String versionProblems = "Version Getter Messed Up, ping HerobaneNair or fix paper-plugin.yml";

    private static volatile String pluginVersion = "unknown";

    private HeroBotCommand() {
    }

    public static void setPluginVersion(String version) {
        pluginVersion = version;
    }

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
        String version = pluginVersion;
        int versionReturned = 0;

        try {
            String digits = version.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) versionReturned = Integer.parseInt(digits);
        } catch (Exception e) {
            context.getSource().sendSuccess(() -> Component.literal(versionProblems).withColor(0xFF5555), false);
            HeroBot.LOGGER.error(versionProblems, e);
        }

        context.getSource().sendSuccess(() -> Component.literal("HeroBot Plugin Version: " + version), false);
        int finalVersionReturned = versionReturned;
        context.getSource().sendSuccess(() -> Component.literal("Returns: " + finalVersionReturned).withColor(0xAAAAAA), false);
        return finalVersionReturned;
    }
}
