package hero.bane.herobot.mod.common.carpet;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CarpetCompat implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("HeroBot/carpet-compat");
    private static final String CARPET_CONFIG = "carpet.mixins.json";

    private static final Set<String> DISABLE = Set.of(
            "Player_creativeNoClipMixin",
            "Player_xpNoCooldownMixin",
            "Player_fakePlayersMixin",
            "PlayerList_fakePlayersMixin",
            "BlockItem_creativeNoClipMixin",
            "StandingAndWallBlockItem_creativeNoClipMixin",
            "ShulkerBoxBlockEntity_creativeNoClipMixin",
            "LivingEntity_creativeFlyMixin",
            "LevelRenderer_creativeNoClipMixin",
            "Explosion_optimizedTntMixin"
    );

    private static final String[] STRING_LIST_FIELDS = {
            "mixinClasses", "mixinClassesClient", "mixinClassesServer"
    };

    private static final String[] MIXININFO_LIST_FIELDS = {
            "pendingMixins", "mixins"
    };

    @Override
    public void onPreLaunch() {
        if (!FabricLoader.getInstance().isModLoaded("carpet")) return;

        try {
            Config carpetConfig = Mixins.getConfigs().stream()
                    .filter(config -> config.getName().equals(CARPET_CONFIG))
                    .findFirst()
                    .orElse(null);
            if (carpetConfig == null) {
                LOGGER.warn("carpet is loaded but {} could not be resolved; HeroBot could not disable "
                        + "Carpet's duplicate mixins", CARPET_CONFIG);
                return;
            }
            strip(carpetConfig.getConfig());
        } catch (Throwable t) {
            LOGGER.error("failed to disable Carpet's duplicate mixins; HeroBot and Carpet may "
                    + "conflict at runtime", t);
        }
    }

    private static void strip(IMixinConfig carpetConfig) throws ReflectiveOperationException {
        Class<?> cls = carpetConfig.getClass();
        Set<String> disabled = new java.util.TreeSet<>();

        for (String fieldName : STRING_LIST_FIELDS) {
            Field field = declaredField(cls, fieldName);
            if (field == null) continue;
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> current = (List<String>) field.get(carpetConfig);
            if (current == null || current.isEmpty()) continue;

            List<String> kept = new ArrayList<>(current.size());
            for (String mixin : current) {
                if (DISABLE.contains(mixin)) {
                    disabled.add(mixin);
                } else {
                    kept.add(mixin);
                }
            }
            field.set(carpetConfig, kept);
        }

        for (String fieldName : MIXININFO_LIST_FIELDS) {
            Field field = declaredField(cls, fieldName);
            if (field == null) continue;
            field.setAccessible(true);
            if (field.get(carpetConfig) instanceof List<?> list) {
                stripInfoList(list, disabled);
            }
        }

        Field mappingField = declaredField(cls, "mixinMapping");
        if (mappingField != null) {
            mappingField.setAccessible(true);
            if (mappingField.get(carpetConfig) instanceof Map<?, ?> map) {
                for (Object bucket : map.values()) {
                    if (bucket instanceof List<?> list) stripInfoList(list, disabled);
                }
            }
        }

        LOGGER.info("disabled {} duplicate Carpet mixin(s) so HeroBot can coexist: {}",
                disabled.size(), disabled);
    }

    private static void stripInfoList(List<?> list, Set<String> disabled) {
        for (Iterator<?> it = list.iterator(); it.hasNext(); ) {
            Object info = it.next();
            if (info instanceof IMixinInfo mi && isDisabled(mi.getClassName())) {
                disabled.add(simpleName(mi.getClassName()));
                it.remove();
            }
        }
    }

    private static boolean isDisabled(String className) {
        return DISABLE.contains(simpleName(className));
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static Field declaredField(Class<?> cls, String name) {
        try {
            return cls.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
