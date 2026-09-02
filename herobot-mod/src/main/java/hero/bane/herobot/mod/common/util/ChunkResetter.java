package hero.bane.herobot.mod.common.util;

import hero.bane.herobot.mod.common.HeroBot;
import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChunkResetter {

    public static boolean resetChunk(ServerLevel level, ChunkPos pos) {
        if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null) {
            return false;
        }

        discardEntities(level, pos);

        try {
            level.getChunkSource().chunkMap.write(pos, (CompoundTag) null);
        } catch (Exception e) {
            HeroBot.LOGGER.warn("Failed to reset chunk {}: {}", pos, e);
            return false;
        }

        return true;
    }

    public record ChunkDeleteResult(int resetted, int skipped) {
    }

    public static ChunkDeleteResult resetChunkRange(ServerLevel level, ChunkPos from, ChunkPos to) {
        int minX = Math.min(from.x(), to.x());
        int minZ = Math.min(from.z(), to.z());
        int maxX = Math.max(from.x(), to.x());
        int maxZ = Math.max(from.z(), to.z());

        int resetted = 0;
        int skipped = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (resetChunk(level, new ChunkPos(x, z))) {
                    resetted++;
                } else {
                    skipped++;
                }
            }
        }
        return new ChunkDeleteResult(resetted, skipped);
    }

    public record WorldDeleteResult(int resetted, int skipped) {
    }

    public static WorldDeleteResult resetWorld(ServerLevel level) {
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(e -> {
            if (!(e instanceof Player)) entities.add(e);
        });
        for (Entity entity : entities) {
            entity.discard();
        }

        Path dimDir = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT));
        List<ChunkPos> allChunks = getChunkPositionsFromRegionDir(dimDir.resolve("region"));

        int resetted = 0;
        int skipped = 0;
        for (ChunkPos pos : allChunks) {
            if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null) {
                skipped++;
                continue;
            }
            try {
                level.getChunkSource().chunkMap.write(pos, (CompoundTag) null);
                resetted++;
            } catch (Exception e) {
                HeroBot.LOGGER.warn("Failed to reset  chunk {}: {}", pos, e);
                skipped++;
            }
        }

        if (HeroBotSettings.deleteChunkEntities) deleteMca(dimDir.resolve("entities"));
        deleteMca(dimDir.resolve("poi"));

        return new WorldDeleteResult(resetted, skipped);
    }

    private static void discardEntities(ServerLevel level, ChunkPos pos) {
        List<Entity> toRemove = new ArrayList<>();
        level.getAllEntities().forEach(e -> {
            if (!(e instanceof Player) && pos.equals(ChunkPos.containing(e.blockPosition()))) {
                toRemove.add(e);
            }
        });
        for (Entity entity : toRemove) {
            entity.discard();
        }
    }

    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private static List<ChunkPos> getChunkPositionsFromRegionDir(Path regionDir) {
        List<ChunkPos> chunks = new ArrayList<>();
        if (!Files.exists(regionDir)) return chunks;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
            for (Path file : stream) {
                Matcher m = REGION_FILE_PATTERN.matcher(file.getFileName().toString());
                if (m.matches()) {
                    int regionX = Integer.parseInt(m.group(1));
                    int regionZ = Integer.parseInt(m.group(2));
                    for (int cx = regionX * 32; cx < regionX * 32 + 32; cx++) {
                        for (int cz = regionZ * 32; cz < regionZ * 32 + 32; cz++) {
                            chunks.add(new ChunkPos(cx, cz));
                        }
                    }
                }
            }
        } catch (IOException e) {
            HeroBot.LOGGER.warn("Failed to list region dir {}: {}", regionDir, e);
        }

        return chunks;
    }

    private static void deleteMca(Path dir) {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.mca")) {
            for (Path file : stream) {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    HeroBot.LOGGER.warn("Failed to reset region {}: {}", file, e);
                }
            }
        } catch (IOException e) {
            HeroBot.LOGGER.warn("Failed to list region {}: {}", dir, e);
        }
    }
}
