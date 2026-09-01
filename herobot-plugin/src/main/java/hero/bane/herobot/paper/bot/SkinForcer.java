package hero.bane.herobot.paper.bot;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import hero.bane.herobot.paper.HeroBot;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class SkinForcer {
    private SkinForcer() {}

    private record CacheEntry<T>(CompletableFuture<T> future, long createdAt) {}

    private static final PropertyMap EMPTY_PROPERTIES = new PropertyMap(ImmutableMultimap.of());

    private static final Map<UUID, CacheEntry<ImmutableMultimap<String, Property>>> PROFILE_CACHE =
            new ConcurrentHashMap<>();

    private static <K, T> CompletableFuture<T> cached(Map<K, CacheEntry<T>> cache, K key, Function<K, T> loader) {
        long now = System.currentTimeMillis();
        return cache.compute(key, (k, existing) -> {
            if (existing != null) {
                CompletableFuture<T> future = existing.future();
                boolean usable = !future.isDone()
                        || (future.getNow(null) != null && now - existing.createdAt() < 300000L);
                if (usable) return existing;
            }
            return new CacheEntry<>(CompletableFuture.supplyAsync(() -> loader.apply(k)), now);
        }).future();
    }

    public static CompletableFuture<Boolean> forceLoadSkin(ServerPlayer target, String name) {
        MinecraftServer server = target.level().getServer();
        return skinProperties(server, name).thenApplyAsync(properties -> {
            if (properties == null || properties.isEmpty()) return false;
            applyProperties(server, target, properties);
            return true;
        }, server);
    }

    public static CompletableFuture<Boolean> forceLoadSkin(ServerPlayer target, UUID skinUUID) {
        MinecraftServer server = target.level().getServer();
        return cached(PROFILE_CACHE, skinUUID, SkinForcer::requestSkinProperties).thenApplyAsync(properties -> {
            if (properties == null || properties.isEmpty()) return false;
            applyProperties(server, target, new PropertyMap(properties));
            return true;
        }, server);
    }

    public static CompletableFuture<PropertyMap> skinProperties(MinecraftServer server, String name) {
        return ResolvableProfile.createUnresolved(name)
                .resolveProfile(server.services().profileResolver())
                .handle((profile, error) -> {
                    if (error != null) {
                        HeroBot.LOGGER.warn("Failed to resolve skin for '{}'", name, error);
                        return EMPTY_PROPERTIES;
                    }
                    return profile == null ? EMPTY_PROPERTIES : profile.properties();
                });
    }

    private static void applyProperties(MinecraftServer server, ServerPlayer target, PropertyMap properties) {
        target.gameProfile =
                new GameProfile(target.getUUID(), target.getGameProfile().name(), properties);

        var playerList = server.getPlayerList();
        playerList.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
        playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));

        ServerLevel level = target.level();
        level.getChunkSource().removeEntity(target);
        level.getChunkSource().addEntity(target);
    }

    private static ImmutableMultimap<String, Property> requestSkinProperties(UUID skinUUID) {
        String uuidStr = skinUUID.toString().replace("-", "");
        JsonObject json;
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false"
            ).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != 200) return null;
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            return null;
        }

        ImmutableMultimap.Builder<String, Property> builder = ImmutableMultimap.builder();
        JsonArray properties = json.getAsJsonArray("properties");
        if (properties != null) {
            for (var element : properties) {
                JsonObject prop = element.getAsJsonObject();
                String propName = prop.get("name").getAsString();
                String value = prop.get("value").getAsString();
                String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                builder.put(propName, new Property(propName, value, signature));
            }
        }
        return builder.build();
    }
}
