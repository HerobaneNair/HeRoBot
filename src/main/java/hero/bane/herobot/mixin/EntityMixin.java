package hero.bane.herobot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hero.bane.herobot.HeRoBotSettings;
import hero.bane.herobot.fakeplayer.FakePlayer;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private Level level;

    @Shadow
    public @Nullable
    abstract LivingEntity getControllingPassenger();

    @Unique
    @Final
    private Entity self = (Entity) (Object) this;

    @Inject(method = "isLocalInstanceAuthoritative", at = @At("HEAD"), cancellable = true)
    private void isFakePlayer(CallbackInfoReturnable<Boolean> cir) {
        if (getControllingPassenger() instanceof FakePlayer)
            cir.setReturnValue(!level.isClientSide());
    }

    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void removePassengerForce(Entity passenger, CallbackInfo ci) {
        if (!HeRoBotSettings.editablePlayerNbt) return;

        if (!passenger.level().isClientSide() && passenger instanceof ServerPlayer sp)
            sp.connection.send(new ClientboundSetPassengersPacket(passenger));

        if (!self.level().isClientSide() && self instanceof ServerPlayer sp)
            sp.connection.send(new ClientboundSetPassengersPacket(self));
    }

    @Inject(method = "addPassenger", at = @At("TAIL"))
    private void addPassengerForce(Entity passenger, CallbackInfo ci) {
        if (!HeRoBotSettings.editablePlayerNbt) return;

        if (!passenger.level().isClientSide() && passenger instanceof ServerPlayer sp)
            sp.connection.send(new ClientboundSetPassengersPacket(passenger));

        if (!self.level().isClientSide() && self instanceof ServerPlayer sp)
            sp.connection.send(new ClientboundSetPassengersPacket(self));
    }

    @WrapOperation(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"
            )
    )
    private boolean allowPlayerRiding(
            EntityType<?> type,
            Operation<Boolean> original
    ) {
        if (!HeRoBotSettings.editablePlayerNbt)
            return original.call(type);

        if (type == EntityType.PLAYER)
            return true;

        return original.call(type);
    }
}
