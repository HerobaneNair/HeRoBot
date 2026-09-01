package hero.bane.herobot.mod.common.mixin;

import net.minecraft.commands.Commands;
import net.minecraft.server.commands.TickCommand;
import net.minecraft.server.permissions.PermissionProviderCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TickCommand.class)
public abstract class TickCommandMixin {

    @Redirect(
            method = "register",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/Commands;hasPermission(Lnet/minecraft/server/permissions/PermissionCheck;)Lnet/minecraft/server/permissions/PermissionProviderCheck;")
    )
    private static PermissionProviderCheck<?> redirectPermission(net.minecraft.server.permissions.PermissionCheck check) {
        return Commands.hasPermission(Commands.LEVEL_MODERATORS);
    }
}
