package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.mod.common.util.EntitySelectorSharedState;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySelectorOptions.class)
public class EntitySelectorOptionsMixin {

    @Inject(method = "bootStrap", at = @At("TAIL"))
    private static void registerAxesDistanceOptions(CallbackInfo ci) {
        EntitySelectorOptionsAccessor.invokeRegister(
                "distanceH",
                parser -> {
                    int cursor = parser.getReader().getCursor();
                    MinMaxBounds.Doubles bounds =
                            MinMaxBounds.Doubles.fromReader(parser.getReader());

                    if ((bounds.min().isPresent() && bounds.min().get() < 0.0D) ||
                            (bounds.max().isPresent() && bounds.max().get() < 0.0D)) {
                        parser.getReader().setCursor(cursor);
                        throw EntitySelectorOptions.ERROR_RANGE_NEGATIVE
                                .createWithContext(parser.getReader());
                    }

                    ((EntitySelectorSharedState) parser).setHorizontalDistance(bounds);
                    parser.setWorldLimited();
                },
                parser -> ((EntitySelectorSharedState) parser).getHorizontalDistance() == null,
                Component.literal("Horizontal distance")
        );
        EntitySelectorOptionsAccessor.invokeRegister(
                "distanceV",
                parser -> {
                    int cursor = parser.getReader().getCursor();
                    MinMaxBounds.Doubles bounds =
                            MinMaxBounds.Doubles.fromReader(parser.getReader());

                    if ((bounds.min().isPresent() && bounds.min().get() < 0.0D) ||
                            (bounds.max().isPresent() && bounds.max().get() < 0.0D)) {
                        parser.getReader().setCursor(cursor);
                        throw EntitySelectorOptions.ERROR_RANGE_NEGATIVE
                                .createWithContext(parser.getReader());
                    }

                    ((EntitySelectorSharedState) parser).setVerticalDistance(bounds);
                    parser.setWorldLimited();
                },
                parser -> ((EntitySelectorSharedState) parser).getVerticalDistance() == null,
                Component.literal("Vertical distance")
        );
        EntitySelectorOptionsAccessor.invokeRegister(
                "isSelf",
                parser -> {
                    boolean wanted = parser.getReader().readBoolean();
                    ((EntitySelectorSharedState) parser).setSelf(wanted);
                },
                parser -> ((EntitySelectorSharedState) parser).getSelf() == null,
                Component.literal("Whether the entity is the one running the command")
        );
    }
}
