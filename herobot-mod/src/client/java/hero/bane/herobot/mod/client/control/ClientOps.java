package hero.bane.herobot.mod.client.control;

import hero.bane.herobot.common.bot.pathing.DebugChannel;
import hero.bane.herobot.mod.common.bot.pathing.PathSettingOps;
import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import hero.bane.herobot.mod.client.net.ServerLink;
import hero.bane.herobot.mod.common.control.ControlOp;
import hero.bane.herobot.mod.common.networking.PathDonePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class ClientOps {
    public static final ClientOps INSTANCE = new ClientOps();

    private ClientOps() {
    }

    private PathSettings pathSettings = new PathSettings();
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
            case ControlOp.PATH_AVOID_BLOCK -> {
                if (op.i1() == ControlOp.AVOID_CLEAR) {
                    pathSettings.clearAvoidedBlocks();
                } else {
                    Identifier id = Identifier.tryParse(op.s0());
                    Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
                    if (block != null) {
                        if (op.i1() == ControlOp.AVOID_REMOVE) pathSettings.removeAvoidedBlock(block);
                        else pathSettings.addAvoidedBlock(block);
                    }
                }
            }
            case ControlOp.PATH_DEBUG_CHANNEL -> {
                DebugChannel[] channels = DebugChannel.values();
                if (op.i0() >= 0 && op.i0() < channels.length) {
                    pathSettings.setDebugChannel(channels[op.i0()], op.i1() != 0);
                }
            }
            case ControlOp.PATH_MOVE_TYPE -> {
                PathSettings.MoveType[] types = PathSettings.MoveType.values();
                if (op.i0() >= 0 && op.i0() < types.length) pathSettings.setMoveType(types[op.i0()]);
            }
            case ControlOp.OPEN_INVENTORY -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(new InventoryScreen(mc.player));
            }
            case ControlOp.CLOSE_SCREEN -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.gui.setScreen(null);
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

    Float pathViewYaw() {
        ClientPathing p = path;
        return p == null ? null : p.viewYaw();
    }

    Float pathViewPitch() {
        ClientPathing p = path;
        return p == null ? null : p.viewPitch();
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
        pathSettings = new PathSettings();
    }

    private static void sendDone(int seq) {
        if (ServerLink.canSend(PathDonePayload.TYPE)) {
            ClientPlayNetworking.send(new PathDonePayload(seq));
        }
    }
}
