package hero.bane.herobot.paper.control;

import hero.bane.herobot.paper.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.paper.bot.BotPlayerActionPack.ActionType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public interface PlayerController {
    PlayerController setForward(float value);
    PlayerController setStrafing(float value);
    PlayerController setSneaking(boolean doSneak);
    PlayerController setSprinting(boolean doSprint);
    PlayerController stopMovement();
    PlayerController setAutoJump(boolean value);

    PlayerController start(ActionType type, Action action);
    PlayerController stop(ActionType type);
    PlayerController startOrExtender(ActionType type, int ticks);
    PlayerController stopAll();

    void setSlot(int slot);
    void pickBlock(boolean includeData);
    void attemptAutoJump();

    PlayerController look(Direction direction);
    PlayerController look(Direction direction, int ticks);
    PlayerController look(Vec2 rotation);
    PlayerController look(Vec2 rotation, int ticks);
    PlayerController look(float yaw, float pitch);
    PlayerController lookInterpolated(float targetYaw, float targetPitch, int ticks);
    PlayerController lookAt(Vec3 position);
    PlayerController lookAt(Vec3 position, int ticks);
    PlayerController turn(float yaw, float pitch);
    PlayerController turn(float yaw, float pitch, int ticks);
    void stopInterpolation();
}
