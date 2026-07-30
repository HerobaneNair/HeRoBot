package hero.bane.herobot.mod.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import hero.bane.herobot.mod.common.util.EntitySelectorExcludeSelf;
import hero.bane.herobot.mod.common.util.EntitySelectorSharedDistance;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(EntitySelector.class)
public class EntitySelectorMixin implements EntitySelectorSharedDistance, EntitySelectorExcludeSelf {

    @Unique private MinMaxBounds.Doubles horizontalDistance;
    @Unique private MinMaxBounds.Doubles verticalDistance;
    @Unique private boolean excludeSelf;

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

    @ModifyReturnValue(method = "findEntities", at = @At("RETURN"))
    private List<? extends Entity> filterFoundEntities(List<? extends Entity> found, CommandSourceStack source) {
        return herobot$applyCustomFilters(found, source);
    }

    @ModifyReturnValue(method = "findPlayers", at = @At("RETURN"))
    private List<ServerPlayer> filterFoundPlayers(List<ServerPlayer> found, CommandSourceStack source) {
        return herobot$applyCustomFilters(found, source);
    }

    @Unique
    private <T extends Entity> List<T> herobot$applyCustomFilters(List<T> found, CommandSourceStack source) {
        Entity self = excludeSelf ? source.getEntity() : null;

        if (self == null && horizontalDistance == null && verticalDistance == null) return found;
        if (found.isEmpty()) return found;

        Vec3 origin = source.getPosition();
        List<T> kept = new ArrayList<>(found.size());

        for (T entity : found) {
            if (entity == self) continue;

            if (horizontalDistance != null) {
                double dx = entity.getX() - origin.x;
                double dz = entity.getZ() - origin.z;

                if (!horizontalDistance.matchesSqr(dx * dx + dz * dz)) continue;
            }

            if (verticalDistance != null && !verticalDistance.matches(Math.abs(entity.getY() - origin.y))) continue;

            kept.add(entity);
        }

        return kept;
    }
}
