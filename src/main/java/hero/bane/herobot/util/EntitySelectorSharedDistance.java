package hero.bane.herobot.util;

import net.minecraft.advancements.criterion.MinMaxBounds;

public interface EntitySelectorSharedDistance {
    void setHorizontalDistance(MinMaxBounds.Doubles bounds);
    void setVerticalDistance(MinMaxBounds.Doubles bounds);

    MinMaxBounds.Doubles getHorizontalDistance();
    MinMaxBounds.Doubles getVerticalDistance();
}