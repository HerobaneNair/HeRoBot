package hero.bane.herobot.util;

import hero.bane.herobot.control.PlayerController;
import hero.bane.herobot.control.PlayerControllers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class BlockPlacer {
    private BlockPlacer() {}

    private static final Direction[] ANY_ORDER = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };

    public static boolean place(ServerPlayer player, Vec3 pos, String face) {
        BlockPos bp = BlockPos.containing(pos);
        Direction dir = direction(face);
        if (dir != null) return tryFace(player, bp, dir);
        for (Direction d : ANY_ORDER) {
            if (tryFace(player, bp, d)) return true;
        }
        return false;
    }

    private static Direction direction(String face) {
        if (face == null || face.equalsIgnoreCase("any")) return null;
        try {
            return Direction.valueOf(face.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean tryFace(ServerPlayer player, BlockPos bp, Direction face) {
        ServerLevel world = player.level();
        if (!(player.getMainHandItem().getItem() instanceof BlockItem blockItem)) return false;
        if (bp.getY() >= world.getMaxY() || bp.getY() < world.getMinY()) return false;
        if (!world.getBlockState(bp).canBeReplaced()) return false;
        if (!world.mayInteract(player, bp)) return false;

        BlockPos support = bp.relative(face.getOpposite());
        BlockState supportState = world.getBlockState(support);
        if (supportState.isAir() || supportState.canBeReplaced()) return false;

        Vec3 hitLoc = findHitPoint(player, world, support, face);
        if (hitLoc == null) return false;

        PlayerController controller = PlayerControllers.of(player);
        float oldYaw = player.getYRot();
        float oldPitch = player.getXRot();
        controller.lookAt(hitLoc);

        ItemStack stack = player.getMainHandItem();
        BlockHitResult hit = new BlockHitResult(hitLoc, face, support, false);
        InteractionResult result = blockItem.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        if (result instanceof InteractionResult.Success) {
            player.swing(InteractionHand.MAIN_HAND);
            player.resetLastActionTime();
            return true;
        }
        controller.look(oldYaw, oldPitch);
        return false;
    }

    private static final double CORNER_INSET = 0.1;

    private static Vec3 findHitPoint(ServerPlayer player, ServerLevel world, BlockPos support, Direction face) {
        Vec3 eye = player.getEyePosition();
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 center = Vec3.atCenterOf(support).add(normal.scale(0.5));
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

        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        for (Vec3 target : targets) {
            if (eye.distanceToSqr(target) > reach * reach) continue;
            BlockHitResult res = world.clip(new ClipContext(
                    eye, target.subtract(normal.scale(0.01)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (res.getType() == HitResult.Type.BLOCK
                    && res.getBlockPos().equals(support)
                    && res.getDirection() == face) {
                return res.getLocation();
            }
        }
        return null;
    }
}
