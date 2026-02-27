package hero.bane.herobot.mixin;

import hero.bane.herobot.util.EntitySelectorSharedDistance;
import net.fabricmc.fabric.mixin.command.EntitySelectorOptionsAccessor;
import net.minecraft.advancements.criterion.MinMaxBounds;
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
        EntitySelectorOptionsAccessor.callPutOption(
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

                    ((EntitySelectorSharedDistance) parser).setHorizontalDistance(bounds);
                    parser.setWorldLimited();
                },
                parser -> ((EntitySelectorSharedDistance) parser).getHorizontalDistance() == null,
                Component.literal("Horizontal distance")
        );
        EntitySelectorOptionsAccessor.callPutOption(
                "distanceV", //should this be distanceY? I think distanceV is better cause Vertical
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

                    ((EntitySelectorSharedDistance) parser).setVerticalDistance(bounds);
                    parser.setWorldLimited();
                },
                parser -> ((EntitySelectorSharedDistance) parser).getVerticalDistance() == null,
                Component.literal("Vertical distance")
        );
    }
}