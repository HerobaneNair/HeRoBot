package hero.bane.herobot.mixin;

import hero.bane.herobot.util.EntitySelectorSharedDistance;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin implements EntitySelectorSharedDistance {

    @Unique private MinMaxBounds.Doubles horizontalDistance;
    @Unique private MinMaxBounds.Doubles verticalDistance;

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

    @Inject(method = "findEntities", at = @At("RETURN"))
    private void applyCustomDistanceFiltering(
            CommandSourceStack source,
            CallbackInfoReturnable<List<Entity>> cir
    ) {
        if (horizontalDistance == null && verticalDistance == null) return;

        Vec3 origin = source.getPosition();
        List<Entity> entities = cir.getReturnValue();

        Iterator<Entity> iterator = entities.iterator();

        while (iterator.hasNext()) {
            Entity entity = iterator.next();

            if (horizontalDistance != null) {
                double dx = entity.getX() - origin.x;
                double dz = entity.getZ() - origin.z;
                double horizontalSq = dx * dx + dz * dz;

                if (!horizontalDistance.matchesSqr(horizontalSq)) {
                    iterator.remove();
                    continue;
                }
            }

            if (verticalDistance != null) {
                double dy = Math.abs(entity.getY() - origin.y);

                if (!verticalDistance.matches(dy)) {
                    iterator.remove();
                }
            }
        }
    }
}