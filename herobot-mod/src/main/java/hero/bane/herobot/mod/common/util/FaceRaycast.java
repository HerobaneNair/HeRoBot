package hero.bane.herobot.mod.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class FaceRaycast {
    private FaceRaycast() {}

    public static final Direction[] ANY_ORDER = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };

    private static final double CORNER_INSET = 0.1;

    public static Direction direction(String face) {
        if (face == null || face.equalsIgnoreCase("any")) return null;
        try {
            return Direction.valueOf(face.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    public static double reach(ServerPlayer player) {
        return player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }

    public static Vec3 visibleHitPoint(ServerPlayer player, ServerLevel world, BlockPos target, Direction face) {
        Vec3 eye = player.getEyePosition();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 center = faceCenter(target, face);
        if (eye.subtract(center).dot(normal) <= 0) return null;

        Vec3 u;
        Vec3 v;
        switch (face.getAxis()) {
            case X -> { u = new Vec3(0.0, 1.0, 0.0); v = new Vec3(0.0, 0.0, 1.0); }
            case Y -> { u = new Vec3(1.0, 0.0, 0.0); v = new Vec3(0.0, 0.0, 1.0); }
            default -> { u = new Vec3(1.0, 0.0, 0.0); v = new Vec3(0.0, 1.0, 0.0); }
        }

        double half = 0.5 - CORNER_INSET;
        Vec3[] targets = {
                center,
                center.add(u.scale(half)).add(v.scale(half)),
                center.add(u.scale(half)).subtract(v.scale(half)),
                center.subtract(u.scale(half)).add(v.scale(half)),
                center.subtract(u.scale(half)).subtract(v.scale(half))
        };

        double reach = reach(player);
        for (Vec3 point : targets) {
            if (eye.distanceToSqr(point) > reach * reach) continue;
            BlockHitResult res = world.clip(new ClipContext(
                    eye, point.subtract(normal.scale(0.01)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (res.getType() == HitResult.Type.BLOCK
                    && res.getBlockPos().equals(target)
                    && res.getDirection() == face) {
                return res.getLocation();
            }
        }
        return null;
    }
}
