package hero.bane.herobot.paper.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class SourceAwareSelectorOptions {

    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    private SourceAwareSelectorOptions() {
    }

    static void register() {
        HeroBotSelectorOptions.put("distanceH",
                parser -> readDistance(parser, true),
                parser -> {
                    Capture capture = ACTIVE.get();
                    return capture != null && capture.horizontal == null;
                },
                Component.literal("Horizontal distance"));

        HeroBotSelectorOptions.put("distanceV",
                parser -> readDistance(parser, false),
                parser -> {
                    Capture capture = ACTIVE.get();
                    return capture != null && capture.vertical == null;
                },
                Component.literal("Vertical distance"));

        HeroBotSelectorOptions.put("isSelf",
                SourceAwareSelectorOptions::readSelf,
                parser -> {
                    Capture capture = ACTIVE.get();
                    return capture != null && capture.self == null;
                },
                Component.literal("Whether the entity is the one running the command"));
    }

    private static void readDistance(EntitySelectorParser parser, boolean horizontal) throws CommandSyntaxException {
        int cursor = parser.getReader().getCursor();
        MinMaxBounds.Doubles bounds = MinMaxBounds.Doubles.fromReader(parser.getReader());

        if ((bounds.min().isPresent() && bounds.min().get() < 0.0D) ||
                (bounds.max().isPresent() && bounds.max().get() < 0.0D)) {
            parser.getReader().setCursor(cursor);
            throw EntitySelectorOptions.ERROR_RANGE_NEGATIVE.createWithContext(parser.getReader());
        }

        Capture capture = ACTIVE.get();
        if (capture == null) return;

        if (horizontal) {
            capture.horizontal = bounds;
        } else {
            capture.vertical = bounds;
        }
        capture.install(parser);
        parser.setWorldLimited();
    }

    private static void readSelf(EntitySelectorParser parser) throws CommandSyntaxException {
        boolean wanted = parser.getReader().readBoolean();

        Capture capture = ACTIVE.get();
        if (capture == null) return;

        capture.self = wanted;
        capture.install(parser);
    }

    public static Capture begin() {
        Capture capture = new Capture();
        ACTIVE.set(capture);
        return capture;
    }

    @FunctionalInterface
    public interface SelectorCall<T> {
        T get() throws CommandSyntaxException;
    }

    public static final class Capture implements AutoCloseable {

        private final ThreadLocal<Context> current = new ThreadLocal<>();

        private EntitySelectorParser parser;
        private MinMaxBounds.Doubles horizontal;
        private MinMaxBounds.Doubles vertical;
        private Boolean self;
        private boolean installed;

        private Capture() {
        }

        @Override
        public void close() {
            ACTIVE.remove();
        }

        public boolean isEmpty() {
            return horizontal == null && vertical == null && self == null;
        }

        public EntitySelector wrap(EntitySelector selector) {
            return isEmpty() ? selector : new SourceAwareEntitySelector(selector, this);
        }

        private void install(EntitySelectorParser parser) {
            this.parser = parser;
            if (installed) return;
            installed = true;
            parser.addPredicate(this::test);
        }

        <T> T scoped(CommandSourceStack source, SelectorCall<T> call) throws CommandSyntaxException {
            Context previous = current.get();
            Entity sourceEntity = source.getEntity();
            current.set(new Context(originOf(source.getPosition()),
                    sourceEntity == null ? null : sourceEntity.getUUID()));
            try {
                return call.get();
            } finally {
                if (previous == null) {
                    current.remove();
                } else {
                    current.set(previous);
                }
            }
        }

        private boolean test(Entity entity) {
            Context context = current.get();
            if (context == null) return true;

            if (self != null) {
                boolean isSource = context.sourceId != null && context.sourceId.equals(entity.getUUID());
                if (isSource != self) return false;
            }

            if (horizontal != null) {
                double dx = entity.getX() - context.origin.x;
                double dz = entity.getZ() - context.origin.z;
                if (!horizontal.matchesSqr(dx * dx + dz * dz)) return false;
            }

            return vertical == null || vertical.matches(Math.abs(entity.getY() - context.origin.y));
        }

        private Vec3 originOf(Vec3 sourcePosition) {
            if (parser == null) return sourcePosition;

            Double x = parser.getX();
            Double y = parser.getY();
            Double z = parser.getZ();

            if (x == null && y == null && z == null) return sourcePosition;

            return new Vec3(x == null ? sourcePosition.x : x,
                    y == null ? sourcePosition.y : y,
                    z == null ? sourcePosition.z : z);
        }

        private record Context(Vec3 origin, UUID sourceId) {
        }
    }
}
