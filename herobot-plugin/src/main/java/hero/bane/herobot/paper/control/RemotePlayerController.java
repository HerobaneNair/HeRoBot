package hero.bane.herobot.paper.control;

import hero.bane.herobot.paper.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.paper.bot.BotPlayerActionPack.ActionType;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class RemotePlayerController implements PlayerController {
    private final ServerPlayer player;

    public RemotePlayerController(ServerPlayer player) {
        this.player = player;
    }

    private PlayerController send(ControlOp op) {
        RemoteOps.send(player, op);
        return this;
    }

    @Override public PlayerController setForward(float value)   { return send(ControlOp.forward(value)); }
    @Override public PlayerController setStrafing(float value)  { return send(ControlOp.strafe(value)); }
    @Override public PlayerController setSneaking(boolean v)    { return send(ControlOp.sneak(v)); }
    @Override public PlayerController setSprinting(boolean v)   { return send(ControlOp.sprint(v)); }
    @Override public PlayerController stopMovement()            { return send(ControlOp.stopMovement()); }
    @Override public PlayerController setAutoJump(boolean v)    { return send(ControlOp.autoJump(v)); }

    @Override
    public PlayerController start(ActionType type, Action action) {
        int mode = action.limit == 1
                ? (action.hits > 1 ? ControlOp.MODE_TWICE : ControlOp.MODE_ONCE)
                : (action.isContinuous() ? ControlOp.MODE_CONTINUOUS : ControlOp.MODE_INTERVAL);
        return send(ControlOp.startAction(type, mode, action.interval, action.ticksRemaining()));
    }

    @Override public PlayerController stop(ActionType type)                  { return send(ControlOp.stopAction(type)); }
    @Override public PlayerController startOrExtender(ActionType type, int t) { return send(ControlOp.startOrExtend(type, t)); }
    @Override public PlayerController stopAll()                              { return send(ControlOp.stopAll()); }

    @Override public void setSlot(int slot)                                 { send(ControlOp.setSlot(slot)); }
    @Override public void pickBlock(boolean includeData)                    { send(ControlOp.pickBlock(includeData)); }
    @Override public void attemptAutoJump()                                 { send(ControlOp.attemptAutoJump()); }

    @Override public PlayerController look(Direction direction)             { return send(ControlOp.lookDir(direction, 0)); }
    @Override public PlayerController look(Direction direction, int ticks)  { return send(ControlOp.lookDir(direction, ticks)); }
    @Override public PlayerController look(Vec2 rotation)                   { return send(ControlOp.look(rotation.y, rotation.x, 0)); }
    @Override public PlayerController look(Vec2 rotation, int ticks)        { return send(ControlOp.look(rotation.y, rotation.x, ticks)); }
    @Override public PlayerController look(float yaw, float pitch)          { return send(ControlOp.look(yaw, pitch, 0)); }
    @Override public PlayerController lookInterpolated(float y, float p, int t) { return send(ControlOp.look(y, p, t)); }
    @Override public PlayerController lookAt(Vec3 position)                 { return send(ControlOp.lookAt(position, 0)); }
    @Override public PlayerController lookAt(Vec3 position, int ticks)      { return send(ControlOp.lookAt(position, ticks)); }
    @Override public PlayerController turn(float yaw, float pitch)          { return send(ControlOp.turn(yaw, pitch, 0)); }
    @Override public PlayerController turn(float yaw, float pitch, int t)   { return send(ControlOp.turn(yaw, pitch, t)); }
    @Override public void stopInterpolation()                              { send(ControlOp.stopInterp()); }
}
