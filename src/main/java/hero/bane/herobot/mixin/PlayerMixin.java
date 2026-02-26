package hero.bane.herobot.mixin;

import hero.bane.herobot.HeroBotSettings;
import hero.bane.herobot.fakeplayer.FakePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {

    @Unique
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

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
        return player.isSpectator() || (HeroBotSettings.creativeNoClip && player.isCreative() && player.getAbilities().flying);
    }

    @Redirect(method = "aiStep", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z")
    )
    private boolean collidesWithEntities(Player player) {
        return player.isSpectator() || (HeroBotSettings.creativeNoClip && player.isCreative() && player.getAbilities().flying);
    }

    @Redirect(method = "updatePlayerPose", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isSpectator()Z")
    )
    private boolean spectatorsDontPose(Player player) {
        return player.isSpectator() || (HeroBotSettings.creativeNoClip && player.isCreative() && player.getAbilities().flying);
    }

    @Redirect(
            method = "causeExtraKnockback",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;hurtMarked:Z",
                    ordinal = 0,
                    opcode = Opcodes.GETFIELD) //It says it needs it, not sure if it breaks things
    )
    private boolean velocityModifiedAndNotCarpetFakePlayer(Entity target)
    {
        return target.hurtMarked && !(target instanceof FakePlayer);
    }

    @Inject(method = "blockUsingItem", at = @At("HEAD"))
    private void onShieldDisabled(ServerLevel serverLevel, LivingEntity livingEntity, CallbackInfo ci) {
        var canDisableShield = false;
        ItemStack itemStack = this.getItemBlockingWith();
        BlocksAttacks blocksAttacks = itemStack != null ? itemStack.get(DataComponents.BLOCKS_ATTACKS) : null;
        float f = livingEntity.getSecondsToDisableBlocking();
        if (f > 0.0F && blocksAttacks != null) {
            canDisableShield = true;
        }

        if (canDisableShield && HeroBotSettings.shieldStunning) {
            this.invulnerableTime = 20;
            executor.schedule(() -> this.invulnerableTime = 0, 1, TimeUnit.MILLISECONDS);
        }
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
