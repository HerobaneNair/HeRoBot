package hero.bane.herobot.mixin;

import com.mojang.authlib.GameProfile;
import hero.bane.herobot.fakeplayer.FakePlayerActionPack;
import hero.bane.herobot.fakeplayer.connection.ServerPlayerInterface;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements ServerPlayerInterface {
    @Unique
    public FakePlayerActionPack actionPack;

    @Override
    public FakePlayerActionPack getActionPack() {
        return actionPack;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onServerPlayerEntityContructor(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation cli, CallbackInfo ci) {
        this.actionPack = new FakePlayerActionPack((ServerPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        actionPack.onUpdate();
    }
}
