package hero.bane.herobot.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import hero.bane.herobot.util.EntitySelectorExcludeSelf;
import hero.bane.herobot.util.EntitySelectorSharedDistance;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySelectorParser.class)
public class EntitySelectorParserMixin implements EntitySelectorSharedDistance, EntitySelectorExcludeSelf {

    @Unique
    private MinMaxBounds.Doubles horizontalDistance;
    @Unique
    private MinMaxBounds.Doubles verticalDistance;
    @Unique
    private boolean excludeSelf;

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

    @Override
    public void setExcludeSelf(boolean excludeSelf) {
        this.excludeSelf = excludeSelf;
    }

    @Override
    public boolean isExcludeSelf() {
        return excludeSelf;
    }

    @ModifyExpressionValue(
            method = "parseSelector",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;read()C"))
    private char acceptExcludeSelfSelector(char selectorType) {
        this.excludeSelf = selectorType == 'z';
        return this.excludeSelf ? 'e' : selectorType;
    }

    @Inject(method = "fillSelectorSuggestions", at = @At("TAIL"))
    private static void suggestExcludeSelfSelector(SuggestionsBuilder builder, CallbackInfo ci) {
        builder.suggest("@z", Component.translatable("argument.entity.selector.allEntitiesExceptSelf"));
    }

    @Inject(method = "getSelector", at = @At("RETURN"))
    private void copyCustomStateToSelector(CallbackInfoReturnable<EntitySelector> cir) {
        EntitySelector selector = cir.getReturnValue();

        EntitySelectorSharedDistance selectorExt = (EntitySelectorSharedDistance) selector;
        selectorExt.setHorizontalDistance(this.horizontalDistance);
        selectorExt.setVerticalDistance(this.verticalDistance);

        ((EntitySelectorExcludeSelf) selector).setExcludeSelf(this.excludeSelf);
    }
}