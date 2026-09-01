package hero.bane.herobot.paper.command;

import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.bot.BotPlayer;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public final class HeroBotSelectorOptions {

    private static boolean registered;

    private HeroBotSelectorOptions() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        EntitySelectorOptions.bootStrap();

        put("isBot",
                parser -> {
                    boolean wanted = parser.getReader().readBoolean();
                    parser.addPredicate(entity -> entity instanceof BotPlayer == wanted);
                },
                parser -> true,
                Component.literal("Whether the entity is a HeroBot player"));

        SourceAwareSelectorOptions.register();
    }

    static void put(String name,
                    EntitySelectorOptions.Modifier modifier,
                    Predicate<EntitySelectorParser> canUse,
                    Component description) {
        try {
            Method register = EntitySelectorOptions.class.getDeclaredMethod(
                    "register",
                    String.class,
                    EntitySelectorOptions.Modifier.class,
                    Predicate.class,
                    Component.class);
            register.setAccessible(true);
            register.invoke(null, name, modifier, canUse, description);
        } catch (ReflectiveOperationException e) {
            HeroBot.LOGGER.warn("Could not register the '{}' entity selector option: {}", name, e.toString());
        }
    }
}
