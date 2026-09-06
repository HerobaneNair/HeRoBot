package hero.bane.herobot.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import hero.bane.herobot.paper.HeroBot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class SourceAwareSelectorNodes {

    private static final String DATA_COMMAND = "data";

    private static Field childrenField;
    private static Field literalsField;
    private static Field argumentsField;

    private SourceAwareSelectorNodes() {
    }

    public static void apply(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            if (childrenField == null) {
                childrenField = field("children");
                literalsField = field("literals");
                argumentsField = field("arguments");
            }
            CommandNode<CommandSourceStack> root = dispatcher.getRoot();
            Set<CommandNode<CommandSourceStack>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            seen.add(root);

            for (CommandNode<CommandSourceStack> child : List.copyOf(root.getChildren())) {
                walk(root, child, seen, DATA_COMMAND.equals(child.getName()));
            }
        } catch (Throwable t) {
            HeroBot.LOGGER.warn("Could not enable HeroBot selector options outside HeroBot commands: {}", t.toString());
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = CommandNode.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void patch(CommandNode<CommandSourceStack> parent,
                              Set<CommandNode<CommandSourceStack>> seen,
                              boolean dataTarget) throws ReflectiveOperationException {
        if (!seen.add(parent)) return;

        for (CommandNode<CommandSourceStack> child : List.copyOf(parent.getChildren())) {
            walk(parent, child, seen, dataTarget);
        }
    }

    private static void walk(CommandNode<CommandSourceStack> parent,
                             CommandNode<CommandSourceStack> child,
                             Set<CommandNode<CommandSourceStack>> seen,
                             boolean dataTarget) throws ReflectiveOperationException {
        CommandNode<CommandSourceStack> current = child;

        if (child instanceof ArgumentCommandNode<CommandSourceStack, ?> argument
                && !(child instanceof SourceAware)
                && argument.getType() instanceof EntityArgument type) {
            current = swap(parent, argument, type, dataTarget);
        }

        patch(current, seen, dataTarget);
        if (current.getRedirect() != null) patch(current.getRedirect(), seen, dataTarget);
    }

    @SuppressWarnings("unchecked")
    private static CommandNode<CommandSourceStack> swap(CommandNode<CommandSourceStack> parent,
                                                        ArgumentCommandNode<CommandSourceStack, ?> old,
                                                        EntityArgument type,
                                                        boolean dataTarget) throws ReflectiveOperationException {
        ArgumentCommandNode<CommandSourceStack, EntitySelector> typed =
                (ArgumentCommandNode<CommandSourceStack, EntitySelector>) old;

        SourceAware replacement = new SourceAware(typed.getName(), type, typed.getCommand(),
                typed.getRequirement(), typed.getRedirect(), typed.getRedirectModifier(),
                typed.isFork(), typed.getCustomSuggestions(), dataTarget);

        for (CommandNode<CommandSourceStack> grandchild : old.getChildren()) {
            replacement.addChild(grandchild);
        }

        ((Map<String, ?>) childrenField.get(parent)).remove(old.getName());
        ((Map<String, ?>) literalsField.get(parent)).remove(old.getName());
        ((Map<String, ?>) argumentsField.get(parent)).remove(old.getName());
        parent.addChild(replacement);

        return replacement;
    }

    private static final class SourceAware extends ArgumentCommandNode<CommandSourceStack, EntitySelector> {

        private final boolean dataTarget;

        private SourceAware(String name,
                            EntityArgument type,
                            Command<CommandSourceStack> command,
                            Predicate<CommandSourceStack> requirement,
                            CommandNode<CommandSourceStack> redirect,
                            RedirectModifier<CommandSourceStack> modifier,
                            boolean forks,
                            SuggestionProvider<CommandSourceStack> customSuggestions,
                            boolean dataTarget) {
            super(name, type, command, requirement, redirect, modifier, forks, customSuggestions);
            this.dataTarget = dataTarget;
        }

        @Override
        public void parse(StringReader reader, CommandContextBuilder<CommandSourceStack> contextBuilder)
                throws CommandSyntaxException {
            int start = reader.getCursor();

            EntitySelector selector;
            try (SourceAwareSelectorOptions.Capture capture = SourceAwareSelectorOptions.begin()) {
                selector = capture.wrap(getType().parse(reader, contextBuilder.getSource()));
            }
            if (dataTarget) selector = PlayerDataSelector.wrap(selector);

            ParsedArgument<CommandSourceStack, EntitySelector> parsed =
                    new ParsedArgument<>(start, reader.getCursor(), selector);

            contextBuilder.withArgument(getName(), parsed);
            contextBuilder.withNode(this, parsed.getRange());
        }

        @Override
        public CompletableFuture<Suggestions> listSuggestions(CommandContext<CommandSourceStack> context,
                                                              SuggestionsBuilder builder) throws CommandSyntaxException {
            try (SourceAwareSelectorOptions.Capture ignored = SourceAwareSelectorOptions.begin()) {
                return super.listSuggestions(context, builder);
            }
        }
    }
}
