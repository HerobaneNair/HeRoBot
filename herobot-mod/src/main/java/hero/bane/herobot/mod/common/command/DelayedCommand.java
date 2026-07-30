package hero.bane.herobot.mod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import hero.bane.herobot.mod.common.util.delayer.DelayedManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.stream.Collectors;

public class DelayedCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                Commands.literal("delayed")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("tickDelay")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .then(Commands.literal("command")
                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                        //Incredibly annoying to figure out
                                                        .suggests((c, b) -> {
                                                            String remaining = b.getRemaining();
                                                            ParseResults<CommandSourceStack> parse =
                                                                    dispatcher.parse(remaining, c.getSource());
                                                            return dispatcher.getCompletionSuggestions(parse)
                                                                    .thenApply(suggestions -> {

                                                                        if (suggestions.isEmpty()) {
                                                                            return suggestions;
                                                                        }

                                                                        int offset = b.getStart();
                                                                        StringRange shifted = getShifted(suggestions, offset);

                                                                        List<Suggestion> shiftedSuggestions = suggestions.getList().stream()
                                                                                .map(s -> new Suggestion(
                                                                                        new StringRange(
                                                                                                s.getRange().getStart() + offset,
                                                                                                s.getRange().getEnd() + offset
                                                                                        ),
                                                                                        s.getText(),
                                                                                        s.getTooltip()
                                                                                ))
                                                                                .collect(Collectors.toList());

                                                                        return new Suggestions(shifted, shiftedSuggestions);
                                                                    });
                                                        })
                                                        .executes(context1 ->
                                                                DelayedManager.scheduleCommand(
                                                                        context1.getSource(),
                                                                        IntegerArgumentType.getInteger(context1, "ticks"),
                                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context1, "command"
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("function")
                                                .then(Commands.argument("function", FunctionArgument.functions())
                                                        .suggests(FunctionCommand.SUGGEST_FUNCTION)
                                                        .executes(context ->
                                                                DelayedManager.scheduleFunction(
                                                                        context.getSource(),
                                                                        IntegerArgumentType.getInteger(context, "ticks"),
                                                                        FunctionArgument.getFunctionOrTag(context, "function")
                                                                                .getFirst()
                                                                                .toString()
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("queue")
                                .executes(context ->
                                        DelayedManager.list(context.getSource(), null)
                                )
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("entity", EntityArgument.entity())
                                                .executes(context -> {
                                                    int total = 0;
                                                    for (Entity e : EntityArgument.getEntities(context, "entity")) {
                                                        total += DelayedManager.list(
                                                                context.getSource(),
                                                                e.getUUID()
                                                        );
                                                    }
                                                    return total;
                                                })
                                        )
                                        .executes(context -> {
                                            Entity self = context.getSource().getEntity();
                                            if (self == null) return 0;
                                            return DelayedManager.list(
                                                    context.getSource(),
                                                    self.getUUID()
                                            );
                                        })
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(context ->
                                                        DelayedManager.remove(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "index")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .executes(context ->
                                        DelayedManager.clear(context.getSource())
                                )
                        )
        );
    }

    private static @NonNull StringRange getShifted(Suggestions suggestions, int offset) {
        StringRange originalRange = suggestions.getRange();

        return new StringRange(
                originalRange.getStart() + offset,
                originalRange.getEnd() + offset
        );
    }
}
