package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.mod.common.util.EntitySelectorSharedState;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("AddedMixinMembersNamePattern") // I HATE YOU DIEEE
@Mixin(EntitySelectorParser.class)
public class EntitySelectorParserMixin implements EntitySelectorSharedState {

    @Unique
    private MinMaxBounds.Doubles horizontalDistance;
    @Unique
    private MinMaxBounds.Doubles verticalDistance;
    @Unique
    private Boolean self;

    @Override
    public void setHorizontalDistance(MinMaxBounds.Doubles bounds) {
        this.horizontalDistance = bounds;
    }

    @Override
    public void setVerticalDistance(MinMaxBounds.Doubles bounds) {
        this.verticalDistance = bounds;
    }

    @Override
    public void setSelf(Boolean wanted) {
        this.self = wanted;
    }

    @Override
    public MinMaxBounds.Doubles getHorizontalDistance() {
        return horizontalDistance;
    }

    @Override
    public MinMaxBounds.Doubles getVerticalDistance() {
        return verticalDistance;
    }

    @Override
    public Boolean getSelf() {
        return self;
    }

    @Inject(method = "getSelector", at = @At("RETURN"))
    private void copyCustomStateToSelector(CallbackInfoReturnable<EntitySelector> cir) {
        EntitySelector selector = cir.getReturnValue();

        EntitySelectorSharedState selectorExt = (EntitySelectorSharedState) selector;
        selectorExt.setHorizontalDistance(this.horizontalDistance);
        selectorExt.setVerticalDistance(this.verticalDistance);
        selectorExt.setSelf(this.self);

    }
}
