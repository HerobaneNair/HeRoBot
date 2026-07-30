package hero.bane.herobot.mod.common.bot;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import hero.bane.herobot.mod.common.HeroBotSettings;
import hero.bane.herobot.mod.common.mixin.PlayerAccessor;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.OldUsersConverter;

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

    private static final Map<UUID, CacheEntry<ImmutableMultimap<String, Property>>> PROFILE_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<String, CacheEntry<UUID>> NAME_CACHE = new ConcurrentHashMap<>();

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
        return cached(NAME_CACHE, name, n -> resolveUuid(server, n))
                .thenCompose(uuid -> uuid == null
                        ? CompletableFuture.completedFuture(false)
                        : forceLoadSkin(target, uuid));
    }

    public static CompletableFuture<Boolean> forceLoadSkin(ServerPlayer target, UUID skinUUID) {
        MinecraftServer server = target.level().getServer();
        return cached(PROFILE_CACHE, skinUUID, SkinForcer::requestSkinProperties).thenApplyAsync(properties -> {
            if (properties == null) return false;

            String playerName = target.getGameProfile().name();
            GameProfile newProfile = new GameProfile(target.getUUID(), playerName, new PropertyMap(properties));
            ((PlayerAccessor) target).setGameProfile(newProfile);

            var playerList = server.getPlayerList();
            playerList.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
            playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));

            ServerLevel level = target.level();
            level.getChunkSource().removeEntity(target);
            level.getChunkSource().addEntity(target);
            return true;
        }, server);
    }

    private static UUID resolveUuid(MinecraftServer server, String name) {
        server.services().nameToIdCache().resolveOfflineUsers(false);
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, name);
        if (uuid == null && HeroBotSettings.allowSpawningOfflinePlayers) {
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(name);
        }
        return uuid;
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
