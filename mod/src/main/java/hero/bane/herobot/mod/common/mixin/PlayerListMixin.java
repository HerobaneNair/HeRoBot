package hero.bane.herobot.mod.common.mixin;

import com.mojang.authlib.GameProfile;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import hero.bane.herobot.mod.common.bot.connection.BotPlayerNetHandler;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow @Final
    private MinecraftServer server;

    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"))
    private void fixStartingPos(Connection connection, ServerPlayer serverPlayer, CommonListenerCookie commonListenerCookie, CallbackInfo ci)
    {
        if (serverPlayer instanceof BotPlayer)
        {
            ((BotPlayer) serverPlayer).fixStartingPosition.run();
        }
    }

    @Redirect(
            method = "placeNewPlayer",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
            )
    )
    private ServerGamePacketListenerImpl replaceNetworkHandler(
            MinecraftServer server,
            Connection connection,
            ServerPlayer player,
            CommonListenerCookie cookie
    ) {
        if (player instanceof BotPlayer bot) {
            return new BotPlayerNetHandler(this.server, connection, bot, cookie);
        }
        return new ServerGamePacketListenerImpl(this.server, connection, player, cookie);
    }

    @Redirect(
            method = "respawn",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;"
            )
    )
    private ServerPlayer makePlayerForRespawn(
            MinecraftServer server,
            ServerLevel level,
            GameProfile profile,
            ClientInformation cli,
            ServerPlayer oldPlayer,
            boolean alive
    ) {
        if (oldPlayer instanceof BotPlayer oldBot) {
            BotPlayer newBot = BotPlayer.respawnFake(this.server, level, profile, cli);
            newBot.getPathSettings().copyFrom(oldBot.getPathSettings());
            newBot.setDeltaMovement(Vec3.ZERO);
            return newBot;
        }
        return new ServerPlayer(this.server, level, profile, cli);
    }
}
