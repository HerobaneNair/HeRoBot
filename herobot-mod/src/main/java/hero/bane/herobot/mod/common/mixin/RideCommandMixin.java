package hero.bane.herobot.mod.common.mixin;

import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RideCommand.class)
public class RideCommandMixin {

    @Redirect(
            method = "mount",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;is(Ljava/lang/Object;)Z"
            )
    )
    private static boolean allowPlayerMounts(Entity entity, Object type) {
        if (type == EntityTypes.PLAYER && HeroBotSettings.editablePlayerNbt) {
            return false;
        }
        return entity.is((EntityType<?>) type);
    }
}
