package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import hero.bane.herobot.mod.common.rule.ModRules;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {
    @Shadow
    protected abstract void touch(Entity entity);

    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Redirect(method = "tick", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z")
    )
    private boolean canClipThroughWorld(Player player) {
        return player.isSpectator() || ModRules.isCreativeNoClipFlying(player);
    }

    @Redirect(method = "aiStep", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z")
    )
    private boolean collidesWithEntities(Player player) {
        return player.isSpectator() || ModRules.isCreativeNoClipFlying(player);
    }

    @Redirect(method = "updatePlayerPose", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z")
    )
    private boolean spectatorsDontPose(Player player) {
        return player.isSpectator() || ModRules.isCreativeNoClipFlying(player);
    }

    @Redirect(
            method = "causeExtraKnockback",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;hurtMarked:Z",
                    ordinal = 0,
                    opcode = Opcodes.GETFIELD)
    )
    private boolean velocityModifiedAndNotBotPlayer(Entity target) {
        return target.hurtMarked && !(target instanceof BotPlayer);
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "java/util/List.add(Ljava/lang/Object;)Z"))
    public boolean processXpOrbCollisions(List<Entity> instance, Object e) {
        Entity entity = (Entity) e;
        if (HeroBotSettings.xpNoCooldown) {
            this.touch(entity);
            return true;
        }
        return instance.add(entity);
    }
}
