package hero.bane.herobot.mod.common.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import hero.bane.herobot.mod.common.util.EntitySelectorSharedState;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(EntitySelector.class)
public class EntitySelectorMixin implements EntitySelectorSharedState {

    @Unique private MinMaxBounds.Doubles horizontalDistance;
    @Unique private MinMaxBounds.Doubles verticalDistance;
    @Unique private Boolean self;
    @Unique private CommandSourceStack herobot$currentSource;

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

    @Inject(method = "findEntities", at = @At("HEAD"))
    private void captureSourceForEntities(CommandSourceStack source,
                                          CallbackInfoReturnable<List<? extends Entity>> cir) {
        this.herobot$currentSource = source;
    }

    @Inject(method = "findPlayers", at = @At("HEAD"))
    private void captureSourceForPlayers(CommandSourceStack source,
                                         CallbackInfoReturnable<List<ServerPlayer>> cir) {
        this.herobot$currentSource = source;
    }

    @ModifyReturnValue(method = "getPredicate", at = @At("RETURN"))
    private Predicate<Entity> addCustomFilters(Predicate<Entity> original,
                                               @Local(argsOnly = true, name = "pos") Vec3 origin) {
        if (horizontalDistance == null && verticalDistance == null && self == null) return original;

        CommandSourceStack source = this.herobot$currentSource;
        this.herobot$currentSource = null;

        Entity sourceEntity = source == null ? null : source.getEntity();
        UUID sourceId = sourceEntity == null ? null : sourceEntity.getUUID();

        return original.and(entity -> herobot$matches(entity, origin, sourceId));
    }

    @Unique
    private boolean herobot$matches(Entity entity, Vec3 origin, UUID sourceId) {
        if (self != null) {
            boolean isSource = sourceId != null && sourceId.equals(entity.getUUID());
            if (isSource != self) return false;
        }

        if (horizontalDistance != null) {
            double dx = entity.getX() - origin.x;
            double dz = entity.getZ() - origin.z;

            if (!horizontalDistance.matchesSqr(dx * dx + dz * dz)) return false;
        }

        return verticalDistance == null || verticalDistance.matches(Math.abs(entity.getY() - origin.y));
    }
}
