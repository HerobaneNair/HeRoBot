package hero.bane.herobot.mod.common.util;

import net.minecraft.advancements.predicates.MinMaxBounds;

public interface EntitySelectorSharedState {
    void setHorizontalDistance(MinMaxBounds.Doubles bounds);
    void setVerticalDistance(MinMaxBounds.Doubles bounds);
    void setSelf(Boolean wanted);

    MinMaxBounds.Doubles getHorizontalDistance();
    MinMaxBounds.Doubles getVerticalDistance();
    Boolean getSelf();
}
