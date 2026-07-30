package hero.bane.herobot.util;

import hero.bane.herobot.control.PlayerController;
import hero.bane.herobot.control.PlayerControllers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockPlacer {
    private BlockPlacer() {}

    public static boolean place(ServerPlayer player, Vec3 pos, String face, boolean force) {
        BlockPos bp = BlockPos.containing(pos);
        Direction dir = FaceRaycast.direction(face);
        if (dir != null) return tryFace(player, bp, dir, force);
        for (Direction d : FaceRaycast.ANY_ORDER) {
            if (tryFace(player, bp, d, force)) return true;
        }
        return false;
    }

    private static boolean tryFace(ServerPlayer player, BlockPos bp, Direction face, boolean force) {
        ServerLevel world = player.level();
        if (!(player.getMainHandItem().getItem() instanceof BlockItem blockItem)) return false;
        if (bp.getY() >= world.getMaxY() || bp.getY() < world.getMinY()) return false;
        if (!world.getBlockState(bp).canBeReplaced()) return false;
        if (!world.mayInteract(player, bp)) return false;

        BlockPos support = bp.relative(face.getOpposite());
        BlockState supportState = world.getBlockState(support);
        if (supportState.isAir() || supportState.canBeReplaced()) return false;

        Vec3 hitLoc = force
                ? FaceRaycast.faceCenter(support, face)
                : FaceRaycast.visibleHitPoint(player, world, support, face);
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
}
