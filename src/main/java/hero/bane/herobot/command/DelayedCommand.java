package hero.bane.herobot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import hero.bane.herobot.util.delayer.DelayedManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.stream.Collectors;

public class DelayedCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("delayed")
                        .then(Commands.literal("tickDelay")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .then(Commands.literal("command")
                                                .then(Commands.argument("command", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
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
                                                                        StringRange originalRange = suggestions.getRange();

                                                                        StringRange shifted =
                                                                                new StringRange(
                                                                                        originalRange.getStart() + offset,
                                                                                        originalRange.getEnd() + offset
                                                                                );

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
                                                        .executes(ctx ->
                                                                DelayedManager.scheduleFunction(
                                                                        ctx.getSource(),
                                                                        IntegerArgumentType.getInteger(ctx, "ticks"),
                                                                        FunctionArgument.getFunctionOrTag(ctx, "function")
                                                                                .getFirst()
                                                                                .toString()
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("queue")
                                .executes(ctx ->
                                        DelayedManager.list(ctx.getSource(), null)
                                )
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("entity", EntityArgument.entity())
                                                .executes(ctx -> {
                                                    int total = 0;
                                                    for (Entity e : EntityArgument.getEntities(ctx, "entity")) {
                                                        total += DelayedManager.list(
                                                                ctx.getSource(),
                                                                e.getUUID()
                                                        );
                                                    }
                                                    return total;
                                                })
                                        )
                                        .executes(ctx -> {
                                            Entity self = ctx.getSource().getEntity();
                                            if (self == null) return 0;
                                            return DelayedManager.list(
                                                    ctx.getSource(),
                                                    self.getUUID()
                                            );
                                        })
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(ctx ->
                                                        DelayedManager.remove(
                                                                ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "index")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .executes(ctx ->
                                        DelayedManager.clear(ctx.getSource())
                                )
                        )
        );
    }
}
