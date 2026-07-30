package hero.bane.herobot.mod.common.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PistonMovingBlockEntity.class)
public class PistonMovingBlockEntityMixin {

    // Thanks fabric modding discord
    @Definition(id = "entity", local = @Local(type = Entity.class))
    @Definition(id = "ServerPlayer", type = ServerPlayer.class)
    @Expression("entity instanceof ServerPlayer")
    @ModifyExpressionValue(method = "moveCollidedEntities", at = @At("MIXINEXTRAS:EXPRESSION"))
    private static boolean isBotPlayerNotServerPlayer(boolean original, @Local Entity entity) {
        if (entity instanceof BotPlayer) return false;
        return original;
    }
}
