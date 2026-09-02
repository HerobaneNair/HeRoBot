package hero.bane.herobot.mod.client.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.Lifecycle;
import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.FileReader;

@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {

    @Shadow
    private void openWorldLoadBundledResourcePack(
            LevelStorageSource.LevelStorageAccess access,
            WorldStem worldStem,
            PackRepository repo,
            Runnable runnable
    ) {
        throw new AssertionError();
    }

    @Inject(
            method = "openWorldCheckWorldStemCompatibility",
            at = @At("HEAD"),
            cancellable = true
    )
    private void skipBackupIfConfigured(
            LevelStorageSource.LevelStorageAccess access,
            WorldStem worldStem,
            PackRepository repo,
            Runnable runnable,
            CallbackInfo ci
    ) {
        LevelDataAndDimensions.WorldDataAndGenSettings settings = worldStem.worldDataAndGenSettings();
        WorldData worldData = settings.data();

        boolean customized = settings.genSettings().options().isOldCustomizedWorld();
        boolean experimental = worldData.worldGenSettingsLifecycle() != Lifecycle.stable();

        if (!customized && !experimental) return;

        if (!HeroBotSettings.disableExperimentalScreen && !disableExperimentalScreen(access)) {
            return;
        }

        ci.cancel();
        this.openWorldLoadBundledResourcePack(access, worldStem, repo, runnable);
    }

    @Unique
    private static boolean disableExperimentalScreen(LevelStorageSource.LevelStorageAccess session) {
        File worldFile = session.getLevelPath(LevelResource.ROOT)
                .resolve("herobot.gson")
                .toFile();

        if (!worldFile.exists()) return false;

        try (FileReader reader = new FileReader(worldFile)) {
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            if (json != null && json.has("disableExperimentalScreen")) {
                return json.get("disableExperimentalScreen").getAsBoolean();
            }
        } catch (Exception ignored) {
        }

        return false;
    }
}
