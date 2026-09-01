package hero.bane.herobot.mod.common.mixin;

import com.mojang.authlib.GameProfile;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack;
import hero.bane.herobot.mod.common.bot.connection.ServerPlayerInterface;
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
    public BotPlayerActionPack actionPack;

    @Override
    public BotPlayerActionPack getActionPack() {
        return actionPack;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onServerPlayerEntityContructor(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation cli, CallbackInfo ci) {
        this.actionPack = new BotPlayerActionPack((ServerPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        actionPack.onUpdate();
    }
}
