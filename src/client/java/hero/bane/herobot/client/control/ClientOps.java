package hero.bane.herobot.client.control;

import hero.bane.herobot.bot.pathing.PathSettingOps;
import hero.bane.herobot.bot.pathing.PathSettings;
import hero.bane.herobot.control.ControlOp;
import hero.bane.herobot.networking.PathDonePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

public final class ClientOps {
    public static final ClientOps INSTANCE = new ClientOps();

    private ClientOps() {}

    private final PathSettings pathSettings = new PathSettings();
    private ClientPathing path;
    private int pathSeq = -1;

    public boolean handle(ControlOp op) {
        switch (op.kind()) {
            case ControlOp.PATH_GOTO_POS -> startPath(op.i0(), new Vec3(op.x(), op.y(), op.z()), null);
            case ControlOp.PATH_GOTO_ENTITY -> {
                Entity target = Minecraft.getInstance().level == null
                        ? null : Minecraft.getInstance().level.getEntity(op.i1());
                startPath(op.i0(), null, target);
            }
            case ControlOp.PATH_STOP -> stopPath(false);
            case ControlOp.PATH_SETTING -> PathSettingOps.apply(pathSettings, op.i0(), op.x());
            case ControlOp.PATH_MOVE_TYPE -> {
                PathSettings.MoveType[] types = PathSettings.MoveType.values();
                if (op.i0() >= 0 && op.i0() < types.length) pathSettings.setMoveType(types[op.i0()]);
            }
            case ControlOp.OPEN_INVENTORY -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.screen == null) mc.setScreen(new InventoryScreen(mc.player));
            }
            case ControlOp.SET_MAIN_HAND -> {
                Minecraft mc = Minecraft.getInstance();
                mc.options.mainHand().set(op.i0() != 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void startPath(int seq, Vec3 targetPos, Entity targetEntity) {
        if (path != null) {
            path.stop();
            path = null;
        }
        pathSeq = seq;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || (targetPos == null && targetEntity == null)) {
            sendDone(seq);
            return;
        }
        path = targetEntity != null
                ? new ClientPathing(targetEntity, pathSettings)
                : new ClientPathing(targetPos, pathSettings);
        if (path.isDone()) {
            path = null;
            sendDone(seq);
        }
    }

    public void stopPath(boolean silent) {
        if (path == null) return;
        path.stop();
        path = null;
        if (!silent) sendDone(pathSeq);
    }

    public void tick() {
        if (path == null) return;
        path.tick();
        if (path.isDone()) {
            path = null;
            sendDone(pathSeq);
        }
    }

    public void reset() {
        if (path != null) {
            path.stop();
            path = null;
        }
    }

    private static void sendDone(int seq) {
        if (ClientPlayNetworking.canSend(PathDonePayload.TYPE)) {
            ClientPlayNetworking.send(new PathDonePayload(seq));
        }
    }
}
