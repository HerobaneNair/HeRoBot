package hero.bane.herobot.mixin;

import hero.bane.herobot.util.EntitySelectorSharedDistance;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySelectorParser.class)
public class EntitySelectorParserMixin implements EntitySelectorSharedDistance {

    @Unique
    private MinMaxBounds.Doubles horizontalDistance;
    @Unique
    private MinMaxBounds.Doubles verticalDistance;

    @Override
    public void setHorizontalDistance(MinMaxBounds.Doubles bounds) {
        this.horizontalDistance = bounds;
    }

    @Override
    public void setVerticalDistance(MinMaxBounds.Doubles bounds) {
        this.verticalDistance = bounds;
    }

    @Override
    public MinMaxBounds.Doubles getHorizontalDistance() {
        return horizontalDistance;
    }

    @Override
    public MinMaxBounds.Doubles getVerticalDistance() {
        return verticalDistance;
    }

    @Inject(method = "getSelector", at = @At("RETURN"))
    private void copyCustomDistancesToSelector(CallbackInfoReturnable<EntitySelector> cir) {
        EntitySelector selector = cir.getReturnValue();

        EntitySelectorSharedDistance selectorExt = (EntitySelectorSharedDistance) selector;
        selectorExt.setHorizontalDistance(this.horizontalDistance);
        selectorExt.setVerticalDistance(this.verticalDistance);
    }
}