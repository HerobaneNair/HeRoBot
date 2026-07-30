package hero.bane.herobot.mod.common.util;

import net.minecraft.advancements.criterion.MinMaxBounds;

public interface EntitySelectorSharedDistance {
    void setHorizontalDistance(MinMaxBounds.Doubles bounds);
    void setVerticalDistance(MinMaxBounds.Doubles bounds);

    MinMaxBounds.Doubles getHorizontalDistance();
    MinMaxBounds.Doubles getVerticalDistance();
}