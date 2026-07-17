package hero.bane.herobot.client.control;

import com.mojang.blaze3d.platform.InputConstants;
import hero.bane.herobot.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.bot.BotPlayerActionPack.ActionType;
import hero.bane.herobot.control.PlayerController;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

public final class ClientPlayerController implements PlayerController {
    public static final ClientPlayerController INSTANCE = new ClientPlayerController();

    private static final int MODE_CONTINUOUS = 1, MODE_INTERVAL = 2;

    private ClientPlayerController() {}

    private float forward, strafe;
    private boolean sneaking, sprinting;
    @SuppressWarnings("unused")
    private boolean autoJump;

    private Float pendingYaw, pendingPitch;
    private LookInterp interp;

    private final Map<ActionType, Act> actions = new EnumMap<>(ActionType.class);

    private static Minecraft mc() { return Minecraft.getInstance(); }

    @Override public PlayerController setForward(float value)  { forward = value; updateMovementKeys(); return this; }
    @Override public PlayerController setStrafing(float value) { strafe = value; updateMovementKeys(); return this; }
    @Override public PlayerController setSneaking(boolean v)   { sneaking = v; updateMovementKeys(); return this; }
    @Override public PlayerController setSprinting(boolean v)  { sprinting = v; updateMovementKeys(); return this; }
    @Override public PlayerController setAutoJump(boolean v)   { autoJump = v; return this; }

    @Override
    public PlayerController stopMovement() {
        forward = 0;
        strafe = 0;
        sneaking = false;
        sprinting = false;
        updateMovementKeys();
        return this;
    }

    private void updateMovementKeys() {
        Options o = mc().options;
        if (o == null) return;
        o.keyUp.setDown(forward > 0);
        o.keyDown.setDown(forward < 0);
        o.keyLeft.setDown(strafe > 0);
        o.keyRight.setDown(strafe < 0);
        o.keyShift.setDown(sneaking);
        o.keySprint.setDown(sprinting);
    }

    @Override
    public PlayerController start(ActionType type, Action action) {
        int mode = action.limit == 1 ? 0 : (action.isContinuous() ? MODE_CONTINUOUS : MODE_INTERVAL);
        if (mode == 0) {
            for (int i = 0; i < action.hits; i++) execOnce(type);
            return this;
        }
        actions.put(type, new Act(mode, action.ticksRemaining(), Math.max(1, action.interval)));
        if (mode == MODE_CONTINUOUS && isHeld(type)) setActionDown(type, true);
        return this;
    }

    @Override
    public PlayerController stop(ActionType type) {
        actions.remove(type);
        setActionDown(type, false);
        return this;
    }

    @Override
    public PlayerController startOrExtender(ActionType type, int ticks) {
        Act current = actions.get(type);
        if (current != null && current.mode == MODE_CONTINUOUS) {
            current.ticksRemaining = ticks;
            return this;
        }
        actions.put(type, new Act(MODE_CONTINUOUS, ticks, 1));
        if (isHeld(type)) setActionDown(type, true);
        return this;
    }

    @Override
    public PlayerController stopAll() {
        for (ActionType type : actions.keySet()) setActionDown(type, false);
        actions.clear();
        stopInterpolation();
        return stopMovement();
    }

    private boolean isHeld(ActionType type) {
        return type == ActionType.JUMP;
    }

    private void setActionDown(ActionType type, boolean down) {
        if (type != ActionType.JUMP) return;
        Options o = mc().options;
        if (o != null) o.keyJump.setDown(down);
    }

    private void execOnce(ActionType type) {
        LocalPlayer p = mc().player;
        switch (type) {
            case ATTACK -> click(mc().options.keyAttack);
            case USE -> click(mc().options.keyUse);
            case JUMP -> click(mc().options.keyJump);
            case SWAP_HANDS -> click(mc().options.keySwapOffhand);
            case SWING -> { if (p != null) p.swing(InteractionHand.MAIN_HAND); }
            case DROP_ITEM -> { if (p != null) p.drop(false); }
            case DROP_STACK -> { if (p != null) p.drop(true); }
        }
    }

    private static void click(KeyMapping key) {
        InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
        KeyMapping.click(bound);
    }

    @Override
    public void setSlot(int slot) {
        LocalPlayer p = mc().player;
        if (p != null) p.getInventory().setSelectedSlot(Mth.clamp(slot - 1, 0, 8));
    }

    @Override public PlayerController look(Direction direction)            { return look(yawFor(direction), pitchFor(direction)); }
    @Override public PlayerController look(Direction direction, int ticks) { return lookInterpolated(yawFor(direction), pitchFor(direction), ticks); }
    @Override public PlayerController look(Vec2 rotation)                  { return look(rotation.y, rotation.x); }
    @Override public PlayerController look(Vec2 rotation, int ticks)       { return lookInterpolated(rotation.y, rotation.x, ticks); }

    @Override
    public PlayerController look(float yaw, float pitch) {
        pendingYaw = yaw % 360;
        pendingPitch = Mth.clamp(pitch, -90, 90);
        interp = null;
        return this;
    }

    @Override
    public PlayerController lookInterpolated(float targetYaw, float targetPitch, int ticks) {
        LocalPlayer p = mc().player;
        if (p == null || ticks <= 0) return look(targetYaw, targetPitch);
        float clampedPitch = Mth.clamp(targetPitch, -90, 90);

        interp = new LookInterp(
                p.getYRot(), p.getXRot(),
                Mth.wrapDegrees(targetYaw - p.getYRot()),
                clampedPitch - p.getXRot(),
                ticks);
        pendingYaw = null;
        return this;
    }

    @Override public PlayerController lookAt(Vec3 position)            { float[] yp = yawPitchTo(position); return yp == null ? this : look(yp[0], yp[1]); }
    @Override public PlayerController lookAt(Vec3 position, int ticks) { float[] yp = yawPitchTo(position); return yp == null ? this : lookInterpolated(yp[0], yp[1], ticks); }

    @Override
    public PlayerController turn(float yaw, float pitch) {
        LocalPlayer p = mc().player;
        if (p == null) return this;
        return look(p.getYRot() + yaw, p.getXRot() + pitch);
    }

    @Override
    public PlayerController turn(float yaw, float pitch, int ticks) {
        LocalPlayer p = mc().player;
        if (p == null) return this;
        return lookInterpolated(p.getYRot() + yaw, p.getXRot() + pitch, ticks);
    }

    @Override
    public void stopInterpolation() {
        interp = null;
    }

    private float yawFor(Direction d) {
        LocalPlayer p = mc().player;
        return switch (d) {
            case NORTH -> 180;
            case SOUTH -> 0;
            case EAST -> -90;
            case WEST -> 90;
            case UP, DOWN -> p == null ? 0 : p.getYRot();
        };
    }

    private float pitchFor(Direction d) {
        return switch (d) {
            case UP -> -90;
            case DOWN -> 90;
            default -> 0;
        };
    }

    private float[] yawPitchTo(Vec3 position) {
        LocalPlayer p = mc().player;
        if (p == null) return null;
        Vec3 eye = p.getEyePosition();
        double dx = position.x - eye.x;
        double dy = position.y - eye.y;
        double dz = position.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, dist) * (180.0 / Math.PI))));
        return new float[]{yaw, pitch};
    }

    private static void applyRotation(LocalPlayer p, float yaw, float pitch) {
        p.yRotO = p.getYRot();
        p.xRotO = p.getXRot();
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    public void clientTick() {
        LocalPlayer p = mc().player;
        if (p == null) return;

        if (interp != null) {
            interp.elapsed++;
            float e = easeInOutExpo(Math.min(1f, (float) interp.elapsed / interp.totalTicks));

            applyRotation(p,
                    Mth.wrapDegrees(interp.startYaw + interp.deltaYaw * e),
                    Mth.clamp(interp.startPitch + interp.deltaPitch * e, -90, 90));

            if (interp.elapsed > interp.totalTicks) interp = null;
        } else if (pendingYaw != null) {
            applyRotation(p, pendingYaw, pendingPitch);
            pendingYaw = null;
            pendingPitch = null;
        }

        if (actions.isEmpty()) return;
        var it = actions.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ActionType type = e.getKey();
            Act a = e.getValue();
            if (a.mode == MODE_CONTINUOUS) {
                if (!isHeld(type)) execOnce(type);
                if (a.ticksRemaining > 0 && --a.ticksRemaining <= 0) {
                    setActionDown(type, false);
                    it.remove();
                }
            } else {
                if (++a.since >= a.interval) {
                    execOnce(type);
                    a.since = 0;
                }
                if (a.ticksRemaining > 0 && --a.ticksRemaining <= 0) {
                    it.remove();
                }
            }
        }
    }

    public void reset() {
        for (ActionType type : actions.keySet()) setActionDown(type, false);
        actions.clear();
        forward = 0;
        strafe = 0;
        sneaking = false;
        sprinting = false;
        updateMovementKeys();
        interp = null;
        pendingYaw = null;
        pendingPitch = null;
    }

    private static final class Act {
        final int mode;
        int ticksRemaining;
        final int interval;
        int since;

        Act(int mode, int ticksRemaining, int interval) {
            this.mode = mode;
            this.ticksRemaining = ticksRemaining;
            this.interval = interval;
        }
    }

    public Float viewYaw(float partialTick) {
        LookInterp i = interp;
        if (i == null) return null;
        return Mth.wrapDegrees(i.startYaw + i.deltaYaw * easeInOutExpo(frameProgress(i, partialTick)));
    }

    public Float viewPitch(float partialTick) {
        LookInterp i = interp;
        if (i == null) return null;
        return Mth.clamp(i.startPitch + i.deltaPitch * easeInOutExpo(frameProgress(i, partialTick)), -90, 90);
    }

    private static float frameProgress(LookInterp i, float partialTick) {
        return Math.min(1f, (i.elapsed + partialTick) / i.totalTicks);
    }

    private static float easeInOutExpo(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t < 0.5f
                ? (float) Math.pow(2, 20 * t - 10) / 2f
                : (2f - (float) Math.pow(2, -20 * t + 10)) / 2f;
    }

    private static final class LookInterp {
        final float startYaw;
        final float startPitch;
        final float deltaYaw;
        final float deltaPitch;
        final int totalTicks;
        int elapsed;

        LookInterp(float startYaw, float startPitch, float deltaYaw, float deltaPitch, int totalTicks) {
            this.startYaw = startYaw;
            this.startPitch = startPitch;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.totalTicks = totalTicks;
        }
    }
}
