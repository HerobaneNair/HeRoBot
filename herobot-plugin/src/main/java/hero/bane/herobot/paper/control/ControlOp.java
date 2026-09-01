package hero.bane.herobot.paper.control;

import hero.bane.herobot.paper.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.paper.bot.BotPlayerActionPack.ActionType;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public record ControlOp(int kind, double x, double y, double z,
                        float f0, float f1, int i0, int i1, int i2, int i3, String s0) {
    public static final int FORWARD = 0, STRAFE = 1, STOP_MOVEMENT = 2, SNEAK = 3, SPRINT = 4,
            AUTOJUMP = 5, START_ACTION = 6, STOP_ACTION = 7, START_OR_EXTEND = 8, STOP_ALL = 9,
            SET_SLOT = 10, LOOK = 11, LOOK_DIR = 12, TURN = 13, LOOK_AT = 14, STOP_INTERP = 15,
            PATH_GOTO_POS = 16, PATH_GOTO_ENTITY = 17, PATH_STOP = 18, PATH_SETTING = 19,
            PATH_MOVE_TYPE = 20, OPEN_INVENTORY = 21, SET_MAIN_HAND = 22, PICK_BLOCK = 23,
            PATH_AVOID_BLOCK = 24, PATH_DEBUG_CHANNEL = 25, ATTEMPT_AUTOJUMP = 26, CLOSE_SCREEN = 27;

    public static final int MODE_ONCE = 0, MODE_CONTINUOUS = 1, MODE_INTERVAL = 2, MODE_TWICE = 3;

    public static final int AVOID_ADD = 0, AVOID_REMOVE = 1, AVOID_CLEAR = 2;

    private static ControlOp of(int kind, double x, double y, double z,
                               float f0, float f1, int i0, int i1, int i2, int i3) {
        return new ControlOp(kind, x, y, z, f0, f1, i0, i1, i2, i3, "");
    }

    public static ControlOp forward(float v)      { return of(FORWARD, 0, 0, 0, v, 0, 0, 0, 0, 0); }
    public static ControlOp strafe(float v)       { return of(STRAFE, 0, 0, 0, v, 0, 0, 0, 0, 0); }
    public static ControlOp stopMovement()        { return of(STOP_MOVEMENT, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static ControlOp sneak(boolean b)      { return of(SNEAK, 0, 0, 0, 0, 0, b ? 1 : 0, 0, 0, 0); }
    public static ControlOp sprint(boolean b)     { return of(SPRINT, 0, 0, 0, 0, 0, b ? 1 : 0, 0, 0, 0); }
    public static ControlOp autoJump(boolean b)   { return of(AUTOJUMP, 0, 0, 0, 0, 0, b ? 1 : 0, 0, 0, 0); }
    public static ControlOp stopAll()             { return of(STOP_ALL, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static ControlOp stopInterp()          { return of(STOP_INTERP, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static ControlOp setSlot(int slot)     { return of(SET_SLOT, 0, 0, 0, 0, 0, slot, 0, 0, 0); }

    public static ControlOp startAction(ActionType t, int mode, int interval, int ticks) {
        return of(START_ACTION, 0, 0, 0, 0, 0, t.ordinal(), mode, interval, ticks);
    }
    public static ControlOp stopAction(ActionType t)            { return of(STOP_ACTION, 0, 0, 0, 0, 0, t.ordinal(), 0, 0, 0); }
    public static ControlOp startOrExtend(ActionType t, int tk) { return of(START_OR_EXTEND, 0, 0, 0, 0, 0, t.ordinal(), tk, 0, 0); }

    public static ControlOp pathGotoPos(Vec3 target, int seq)       { return of(PATH_GOTO_POS, target.x, target.y, target.z, 0, 0, seq, 0, 0, 0); }
    public static ControlOp pathGotoEntity(int entityId, int seq)   { return of(PATH_GOTO_ENTITY, 0, 0, 0, 0, 0, seq, entityId, 0, 0); }
    public static ControlOp pathStop()                              { return of(PATH_STOP, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static ControlOp pathSetting(int key, double value)      { return of(PATH_SETTING, value, 0, 0, 0, 0, key, 0, 0, 0); }
    public static ControlOp pathMoveType(int ordinal)               { return of(PATH_MOVE_TYPE, 0, 0, 0, 0, 0, ordinal, 0, 0, 0); }
    public static ControlOp pathAvoidBlock(String blockKey, int mode) {
        return new ControlOp(PATH_AVOID_BLOCK, 0, 0, 0, 0, 0, 0, mode, 0, 0, blockKey);
    }
    public static ControlOp pathDebugChannel(int channel, boolean on) { return of(PATH_DEBUG_CHANNEL, 0, 0, 0, 0, 0, channel, on ? 1 : 0, 0, 0); }
    public static ControlOp openInventory()                         { return of(OPEN_INVENTORY, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static ControlOp closeScreen()                           { return of(CLOSE_SCREEN, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static ControlOp setMainHand(boolean left)               { return of(SET_MAIN_HAND, 0, 0, 0, 0, 0, left ? 1 : 0, 0, 0, 0); }
    public static ControlOp pickBlock(boolean includeData)          { return of(PICK_BLOCK, 0, 0, 0, 0, 0, includeData ? 1 : 0, 0, 0, 0); }
    public static ControlOp attemptAutoJump()                       { return of(ATTEMPT_AUTOJUMP, 0, 0, 0, 0, 0, 0, 0, 0, 0); }

    public static ControlOp look(float yaw, float pitch, int ticks) { return of(LOOK, 0, 0, 0, yaw, pitch, ticks, 0, 0, 0); }
    public static ControlOp lookDir(Direction d, int ticks)         { return of(LOOK_DIR, 0, 0, 0, 0, 0, d.get3DDataValue(), ticks, 0, 0); }
    public static ControlOp turn(float yaw, float pitch, int ticks) { return of(TURN, 0, 0, 0, yaw, pitch, ticks, 0, 0, 0); }
    public static ControlOp lookAt(Vec3 p, int ticks)               { return of(LOOK_AT, p.x, p.y, p.z, 0, 0, ticks, 0, 0, 0); }

    public void apply(PlayerController c) {
        switch (kind) {
            case FORWARD -> c.setForward(f0);
            case STRAFE -> c.setStrafing(f0);
            case STOP_MOVEMENT -> c.stopMovement();
            case SNEAK -> c.setSneaking(i0 != 0);
            case SPRINT -> c.setSprinting(i0 != 0);
            case AUTOJUMP -> c.setAutoJump(i0 != 0);
            case START_ACTION -> {
                ActionType t = ActionType.values()[i0];
                Action a = switch (i1) {
                    case MODE_CONTINUOUS -> i3 > 0 ? Action.continuous(i3) : Action.continuous();
                    case MODE_INTERVAL -> i3 > 0 ? Action.interval(i2, i3) : Action.interval(i2);
                    case MODE_TWICE -> Action.once(2);
                    default -> Action.once();
                };
                c.start(t, a);
            }
            case STOP_ACTION -> c.stop(ActionType.values()[i0]);
            case START_OR_EXTEND -> c.startOrExtender(ActionType.values()[i0], i1);
            case STOP_ALL -> c.stopAll();
            case SET_SLOT -> c.setSlot(i0);
            case PICK_BLOCK -> c.pickBlock(i0 != 0);
            case ATTEMPT_AUTOJUMP -> c.attemptAutoJump();
            case LOOK -> { if (i0 > 0) c.lookInterpolated(f0, f1, i0); else c.look(f0, f1); }
            case LOOK_DIR -> { Direction d = Direction.from3DDataValue(i0); if (i1 > 0) c.look(d, i1); else c.look(d); }
            case TURN -> { if (i0 > 0) c.turn(f0, f1, i0); else c.turn(f0, f1); }
            case LOOK_AT -> { Vec3 p = new Vec3(x, y, z); if (i0 > 0) c.lookAt(p, i0); else c.lookAt(p); }
            case STOP_INTERP -> c.stopInterpolation();
            default -> {  }
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ControlOp> STREAM_CODEC = StreamCodec.of(
            (buf, op) -> {
                buf.writeVarInt(op.kind);
                buf.writeDouble(op.x);
                buf.writeDouble(op.y);
                buf.writeDouble(op.z);
                buf.writeFloat(op.f0);
                buf.writeFloat(op.f1);
                buf.writeInt(op.i0);
                buf.writeInt(op.i1);
                buf.writeInt(op.i2);
                buf.writeInt(op.i3);
                buf.writeUtf(op.s0);
            },
            buf -> new ControlOp(
                    buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readUtf())
    );
}
