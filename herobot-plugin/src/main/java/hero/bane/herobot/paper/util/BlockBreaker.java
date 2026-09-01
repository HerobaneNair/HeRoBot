package hero.bane.herobot.paper.util;

import hero.bane.herobot.paper.control.PlayerControllers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BlockBreaker {
    private BlockBreaker() {}

    public static boolean start(ServerPlayer player, Vec3 pos, boolean force) {
        BlockPos bp = BlockPos.containing(pos);
        ServerLevel world = player.level();
        if (bp.getY() > world.getMaxY() || bp.getY() < world.getMinY()) return false;

        BlockState state = world.getBlockState(bp);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (!world.mayInteract(player, bp)) return false;
        if (player.blockActionRestricted(world, bp, player.gameMode.getGameModeForPlayer())) return false;
        if (!player.isWithinBlockInteractionRange(bp, 1.0)) return false;

        boolean creative = player.getAbilities().instabuild;
        if (!creative && state.getDestroyProgress(player, world, bp) <= 0.0f) return false;

        Direction face;
        Vec3 hit;
        if (force) {
            face = nearestFace(player, bp);
            hit = FaceRaycast.faceCenter(bp, face);
        } else {
            face = null;
            hit = null;
            for (Direction d : FaceRaycast.ANY_ORDER) {
                Vec3 point = FaceRaycast.visibleHitPoint(player, world, bp, d);
                if (point != null) {
                    face = d;
                    hit = point;
                    break;
                }
            }
            if (face == null) return false;
        }

        BlockBreakTasks.cancel(player);
        PlayerControllers.of(player).lookAt(hit);

        if (creative) {
            player.gameMode.handleBlockBreakAction(bp,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, face, world.getMaxY(), -1);
            player.swing(InteractionHand.MAIN_HAND, true);
            player.resetLastActionTime();
            return true;
        }

        BlockBreakTasks.begin(player, bp, face, force);
        return true;
    }

    private static Direction nearestFace(ServerPlayer player, BlockPos bp) {
        Vec3 eye = player.getEyePosition();
        Direction best = Direction.UP;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : FaceRaycast.ANY_ORDER) {
            double dist = eye.distanceToSqr(FaceRaycast.faceCenter(bp, d));
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }
}
