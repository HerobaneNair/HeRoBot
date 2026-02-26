package hero.bane.herobot.mixin;

import hero.bane.herobot.HeroBot;
import hero.bane.herobot.HeroBotSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(EntityDataAccessor.class)

public class EntityDataAccessorMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "setData", at = @At("HEAD"), cancellable = true)
    void allowPlayerNbtSet(CompoundTag compoundTag, CallbackInfo ci) {
        if (!HeroBotSettings.editablePlayerNbt) return;
        if (this.entity instanceof Player) {
            UUID UUID = this.entity.getUUID();
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.entity.problemPath(), HeroBot.LOGGER)) {
                this.entity.load(TagValueInput.create(scopedCollector, this.entity.registryAccess(), compoundTag));
                this.entity.setUUID(UUID);
            }
            ci.cancel();
        }
    }
}
