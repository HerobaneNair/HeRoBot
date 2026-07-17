package hero.bane.herobot.bot;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import hero.bane.herobot.HeroBotSettings;
import hero.bane.herobot.mixin.PlayerAccessor;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SkinForcer {
    private SkinForcer() {}

    public static CompletableFuture<Boolean> forceLoadSkin(ServerPlayer target, String name) {
        MinecraftServer server = target.level().getServer();
        return CompletableFuture.supplyAsync(() -> {
            server.services().nameToIdCache().resolveOfflineUsers(false);
            UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, name);
            if (uuid == null && HeroBotSettings.allowSpawningOfflinePlayers) {
                server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
                uuid = UUIDUtil.createOfflinePlayerUUID(name);
            }
            return uuid;
        }).thenCompose(uuid -> {
            if (uuid == null) return CompletableFuture.completedFuture(false);
            return forceLoadSkin(target, uuid);
        });
    }

    public static CompletableFuture<Boolean> forceLoadSkin(ServerPlayer target, UUID skinUUID) {
        MinecraftServer server = target.level().getServer();
        String uuidStr = skinUUID.toString().replace("-", "");
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false"
                ).toURL().openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection.getResponseCode() != 200) return null;
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            } catch (Exception e) {
                return null;
            }
        }).thenApplyAsync(json -> {
            if (json == null) return false;

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
            String playerName = target.getGameProfile().name();
            GameProfile newProfile = new GameProfile(target.getUUID(), playerName, new PropertyMap(builder.build()));
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
}
