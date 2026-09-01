package hero.bane.herobot.mod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import hero.bane.herobot.mod.common.util.ChunkResetter;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ChunkResetterCommand {
    private static final int MAX_CHUNK_LIMIT = 256;
    private static final Dynamic2CommandExceptionType ERROR_TOO_MANY_CHUNKS =
            new Dynamic2CommandExceptionType((max, count) ->
                    Component.literal("Too many chunks: " + count + " [max " + max + "]").withColor(0xFFAAAA));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                literal("chunk-resetter")
                        .requires(s -> !s.isPlayer() || s.getServer().getPlayerList().isOp(Objects.requireNonNull(s.getPlayer()).nameAndId()))
                        .then(literal("chunk")
                                .then(argument("from", ColumnPosArgument.columnPos())
                                        .executes(c -> deleteChunks(c.getSource(),
                                                ColumnPosArgument.getColumnPos(c, "from"),
                                                ColumnPosArgument.getColumnPos(c, "from")))
                                        .then(argument("to", ColumnPosArgument.columnPos())
                                                .executes(c -> deleteChunks(c.getSource(),
                                                        ColumnPosArgument.getColumnPos(c, "from"),
                                                        ColumnPosArgument.getColumnPos(c, "to"))))))
                        .then(literal("world")
                                .executes(c -> deleteWorld(c.getSource(), c.getSource().getLevel()))
                                .then(argument("dimension", DimensionArgument.dimension())
                                        .executes(c -> deleteWorld(c.getSource(), DimensionArgument.getDimension(c, "dimension")))))
        );
    }

    private static int deleteChunks(CommandSourceStack source, ColumnPos from, ColumnPos to) throws CommandSyntaxException {
        int fromChunkX = SectionPos.blockToSectionCoord(from.x());
        int fromChunkZ = SectionPos.blockToSectionCoord(from.z());
        int toChunkX = SectionPos.blockToSectionCoord(to.x());
        int toChunkZ = SectionPos.blockToSectionCoord(to.z());

        long count = ((long) Math.abs(toChunkX - fromChunkX) + 1L) * ((long) Math.abs(toChunkZ - fromChunkZ) + 1L);
        if (count > MAX_CHUNK_LIMIT) {
            throw ERROR_TOO_MANY_CHUNKS.create(MAX_CHUNK_LIMIT, count);
        }

        ServerLevel level = source.getLevel();
        ChunkPos chunkFrom = new ChunkPos(fromChunkX, fromChunkZ);
        ChunkPos chunkTo = new ChunkPos(toChunkX, toChunkZ);

        ChunkResetter.ChunkDeleteResult result = ChunkResetter.resetChunkRange(level, chunkFrom, chunkTo);
        int deleted = result.resetted();
        int skipped = result.skipped();

        if (skipped > 0) {
            source.sendFailure(Component.literal("Skipped " + skipped + " loaded chunk" + (skipped > 1 ? "s" : "") + " - unload them first"));
        }
        if (deleted > 0) {
            source.sendSuccess(() -> Component.literal("Deleted " + deleted + " chunk" + (deleted > 1 ? "s" : "")), true);
        }
        if (deleted == 0 && skipped == 0) {
            source.sendFailure(Component.literal("No chunks reset"));
        }
        return deleted;
    }

    private static int deleteWorld(CommandSourceStack source, ServerLevel level) {
        ChunkResetter.WorldDeleteResult result = ChunkResetter.resetWorld(level);
        int deleted = result.resetted();
        int skipped = result.skipped();

        if (deleted > 0) {
            source.sendSuccess(() -> Component.literal(
                    "Reset " + deleted + " chunk" + (deleted > 1 ? "s" : "") + " in " +
                            level.dimension().identifier()), true);
        }
        if (skipped > 0) {
            source.sendFailure(Component.literal("Skipped " + skipped + " loaded chunk" + (skipped > 1 ? "s" : "") + " - unload them first"));
        }
        if (deleted == 0 && skipped == 0) {
            source.sendFailure(Component.literal("No chunks found to delete in " + level.dimension()));
        }
        return deleted;
    }
}
