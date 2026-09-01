package hero.bane.herobot.mod.common.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.Optional;
import java.util.function.Predicate;

public class RayTrace
{
    @FunctionalInterface
    public interface EntityView
    {
        AABB boxOf(Entity entity);

        default double searchMargin()
    {
            return 0.0;
        }
    }

    public static final EntityView LIVE = e -> e.getBoundingBox().inflate(e.getPickRadius());

    public static HitResult rayTrace(Entity source, float partialTicks, double reach, boolean fluids, EntityView view)
    {
        BlockHitResult blockHit = rayTraceBlocks(source, partialTicks, reach, fluids);
        double maxSqDist = reach * reach;
        // Edge case in case it's not finding an air block when it hits nothing
        //noinspection ConstantValue
        if (blockHit != null) {
            maxSqDist = blockHit.getLocation().distanceToSqr(source.getEyePosition(partialTicks));
        }
        EntityHitResult entityHit = rayTraceEntities(source, partialTicks, reach, maxSqDist, view);
        return entityHit == null ? blockHit : entityHit;
    }

    public static BlockHitResult rayTraceBlocks(Entity source, float partialTicks, double reach, boolean fluids)
    {
        Vec3 pos = source.getEyePosition(partialTicks);
        Vec3 rotation = source.getViewVector(partialTicks);
        Vec3 reachEnd = pos.add(rotation.x * reach, rotation.y * reach, rotation.z * reach);
        return source.level().clip(new ClipContext(
                pos,
                reachEnd,
                ClipContext.Block.OUTLINE,
                fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                source
        ));
    }

    public static EntityHitResult rayTraceEntities(Entity source, float partialTicks, double reach, double maxSqDist, EntityView view)
    {
        Vec3 pos = source.getEyePosition(partialTicks);
        Vec3 reachVec = source.getViewVector(partialTicks).scale(reach);
        AABB box = source.getBoundingBox().expandTowards(reachVec).inflate(1 + view.searchMargin());
        return rayTraceEntities(
                source,
                pos,
                pos.add(reachVec),
                box,
                e -> !e.isSpectator() && e.isPickable(),
                maxSqDist,
                view
        );
    }

    public static EntityHitResult rayTraceEntities(
            Entity source,
            Vec3 start,
            Vec3 end,
            AABB box,
            Predicate<Entity> predicate,
            double maxSqDistance,
            EntityView view
    )
    {
        Level world = source.level();
        double targetDistance = maxSqDistance;
        Entity target = null;
        Vec3 targetHitPos = null;

        for (Entity current : world.getEntities(source, box, predicate))
        {
            AABB currentBox = view.boxOf(current);
            Optional<Vec3> currentHit = currentBox.clip(start, end);

            if (currentBox.contains(start))
            {
                if (targetDistance >= 0)
                {
                    target = current;
                    targetHitPos = currentHit.orElse(start);
                    targetDistance = 0;
                }
            }
            else if (currentHit.isPresent())
            {
                Vec3 currentHitPos = currentHit.get();
                double currentDistance = start.distanceToSqr(currentHitPos);

                if (currentDistance < targetDistance || targetDistance == 0)
                {
                    if (current.getRootVehicle() == source.getRootVehicle())
                    {
                        if (targetDistance == 0)
                        {
                            target = current;
                            targetHitPos = currentHitPos;
                        }
                    }
                    else
                    {
                        target = current;
                        targetHitPos = currentHitPos;
                        targetDistance = currentDistance;
                    }
                }
            }
        }

        return target == null ? null : new EntityHitResult(target, targetHitPos);
    }
}
