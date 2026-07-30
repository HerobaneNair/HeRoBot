package hero.bane.herobot.mod.common.ai.runtime.executors;

import hero.bane.herobot.mod.common.ai.block.BlockInstance;
import hero.bane.herobot.mod.common.ai.block.BlockType;
import hero.bane.herobot.mod.common.ai.runtime.Branch;
import hero.bane.herobot.mod.common.ai.runtime.Executor;
import hero.bane.herobot.mod.common.ai.runtime.ParamEval;
import hero.bane.herobot.mod.common.ai.runtime.ScriptRunner;
import hero.bane.herobot.mod.common.ai.runtime.StepResult;
import hero.bane.herobot.mod.common.control.PlayerController;
import hero.bane.herobot.mod.common.control.PlayerControllers;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class LookExecutor {
    private LookExecutor() {}

    public static void register(Map<BlockType, Executor> flow) {
        flow.put(BlockType.LOOK_DIRECTION, (b, r, br) -> {
            float[] dir = ParamEval.evalRotation(b, "direction", r, br);
            float yaw = dir[0];
            float pitch = dir[1];
            int ticks = ParamEval.evalInt(b, "ticks", r, br);
            yaw += randomOffset(ParamEval.evalDouble(b, "yawOffset", r, br));
            pitch = Mth.clamp(pitch + randomOffset(ParamEval.evalDouble(b, "pitchOffset", r, br)), -90.0f, 90.0f);
            if (ticks > 0) ap(r).lookInterpolated(yaw, pitch, ticks);
            else ap(r).look(yaw, pitch);
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.LOOK_CARDINAL, (b, r, br) -> {
            String dir = ParamEval.evalString(b, "direction", r, br);
            Direction d = parseDirection(dir);
            if (d != null) {
                int ticks = ParamEval.evalInt(b, "ticks", r, br);
                if (ticks > 0) ap(r).look(d, ticks);
                else ap(r).look(d);
                applyOffset(b, r, br, ticks);
            }
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.LOOK_AT_POS, (b, r, br) -> {
            Vec3 pos = ParamEval.evalVec3(b, "position", r, br);
            int ticks = ParamEval.evalInt(b, "ticks", r, br);
            if (ticks > 0) ap(r).lookAt(pos, ticks);
            else ap(r).lookAt(pos);
            applyOffset(b, r, br, ticks);
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.LOOK_AT_ENTITY, (b, r, br) -> {
            Object sel = ParamEval.raw(b, "target", r, br);
            Entity target = SelectorExecutor.resolveFirstEntity(sel, r);
            if (target != null) {
                int ticks = ParamEval.evalInt(b, "ticks", r, br);
                Vec3 lookTarget = lookTargetPoint(b, r, br, target);
                if (ticks > 0) ap(r).lookAt(lookTarget, ticks);
                else ap(r).lookAt(lookTarget);
                applyOffset(b, r, br, ticks);
            }
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.TURN, (b, r, br) -> {
            float dy = (float) ParamEval.evalDouble(b, "dYaw", r, br);
            float dp = (float) ParamEval.evalDouble(b, "dPitch", r, br);
            int ticks = ParamEval.evalInt(b, "ticks", r, br);
            dy += randomOffset(ParamEval.evalDouble(b, "yawOffset", r, br));
            dp += randomOffset(ParamEval.evalDouble(b, "pitchOffset", r, br));
            if (ticks > 0) ap(r).turn(dy, dp, ticks);
            else ap(r).turn(dy, dp);
            return StepResult.continueVia(0);
        });
    }

    private static Vec3 lookTargetPoint(BlockInstance b, ScriptRunner r, Branch br, Entity target) {
        String mode = ParamEval.evalString(b, "mode", r, br);
        if (mode == null) mode = "eyes";
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "feet" -> target.position();
            case "closest" -> closestPointToBox(r.player().getEyePosition(), target.getBoundingBox());
            default -> target.getEyePosition();
        };
    }

    private static Vec3 closestPointToBox(Vec3 eye, AABB box) {
        return new Vec3(
                Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ)
        );
    }

    private static void applyOffset(BlockInstance b, ScriptRunner r, Branch br, int ticks) {
        double yawRange = ParamEval.evalDouble(b, "yawOffset", r, br);
        double pitchRange = ParamEval.evalDouble(b, "pitchOffset", r, br);
        if (yawRange <= 0 && pitchRange <= 0) return;
        float newYaw = r.player().getYRot() + randomOffset(yawRange);
        float newPitch = Mth.clamp(r.player().getXRot() + randomOffset(pitchRange), -90.0f, 90.0f);
        if (ticks > 0) ap(r).lookInterpolated(newYaw, newPitch, ticks);
        else ap(r).look(newYaw, newPitch);
    }

    private static float randomOffset(double range) {
        if (range <= 0) return 0;
        return (float) (ThreadLocalRandom.current().nextDouble() * 2 * range - range);
    }

    private static Direction parseDirection(String s) {
        if (s == null) return null;
        try {
            return Direction.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PlayerController ap(ScriptRunner r) {
        return PlayerControllers.of(r.player());
    }
}
