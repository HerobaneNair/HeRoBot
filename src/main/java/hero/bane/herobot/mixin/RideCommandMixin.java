package hero.bane.herobot.mixin;

import hero.bane.herobot.HeRoBotSettings;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RideCommand.class)
public class RideCommandMixin {

    @Redirect(
            method = "mount",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getType()Lnet/minecraft/world/entity/EntityType;"
            )
    )
    private static EntityType<?> allowPlayerMounts(Entity entity) {
        EntityType<?> type = entity.getType();
        if (type == EntityType.PLAYER && HeRoBotSettings.editablePlayerNbt) {
            return EntityType.PIG; //Definitely a better way to do this I can't think of one tho
        }
        return type;
    }
}
