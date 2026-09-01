package hero.bane.herobot.paper.ai.runtime.executors;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.ai.SelectorValidation;
import hero.bane.herobot.paper.ai.runtime.ScriptRunner;
import hero.bane.herobot.paper.command.SourceAwareSelectorOptions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SelectorExecutor {
    private SelectorExecutor() {}

    public static List<Entity> resolve(String expression, ScriptRunner r) {
        if (expression == null || expression.isBlank()) return List.of();
        try {
            CommandSourceStack source = r.player().createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
            StringReader reader = new StringReader(expression);
            EntitySelectorParser parser = new EntitySelectorParser(reader, true);

            EntitySelector selector;
            try (SourceAwareSelectorOptions.Capture distance = SourceAwareSelectorOptions.begin()) {
                selector = distance.wrap(parser.parse());
            }

            List<Entity> found = new ArrayList<>(selector.findEntities(source));
            if (found.size() > 1) return List.of(found.getFirst());
            return found;
        } catch (CommandSyntaxException e) {
            HeroBot.LOGGER.warn("Invalid selector '{}': {}", expression, e.getMessage());
            return List.of();
        } catch (Throwable t) {
            HeroBot.LOGGER.warn("Selector failure '{}': {}", expression, t.toString());
            return List.of();
        }
    }

    public static Entity resolveSingle(String expression, ScriptRunner r) {
        List<Entity> es = resolve(expression, r);
        return es.isEmpty() ? null : es.getFirst();
    }

    public static Entity resolveFirstEntity(Object sel, ScriptRunner r) {
        if (sel instanceof Entity e) return e;
        if (sel instanceof List<?> list) {
            for (Object o : list) if (o instanceof Entity e) return e;
            return null;
        }
        if (sel instanceof UUID u) return entityByUuid(u, r);
        if (sel instanceof String s) {
            String t = s.trim();
            if (SelectorValidation.isUuid(t)) return entityByUuid(UUID.fromString(t), r);
            return resolveSingle(s, r);
        }
        return null;
    }

    private static Entity entityByUuid(UUID id, ScriptRunner r) {
        return r.player().level().getEntity(id);
    }
}
