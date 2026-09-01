package hero.bane.herobot.paper.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.paper.voice.VoiceOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class SoundSubtree {
    private SoundSubtree() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("vc")
                .then(bluetooth())
                .then(file())
                .then(group())
                .then(Commands.literal("distance")
                        .then(Commands.literal("reset")
                                .executes(SoundSubtree::resetDistance))
                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(0f))
                                .executes(SoundSubtree::distance)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bluetooth() {
        return Commands.literal("bluetooth")
                .then(Commands.literal("off")
                        .executes(SoundSubtree::stopBluetooth))
                .then(Commands.argument("name", EntityArgument.player())
                        .executes(SoundSubtree::startBluetooth));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> file() {
        return Commands.literal("file")
                .then(Commands.literal("play")
                        .then(Commands.argument("file", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        VoiceOps.soundNames(c.getSource().getServer()), b))
                                .executes(c -> play(c, false))
                                .then(Commands.literal("loop")
                                        .executes(c -> play(c, true)))))
                .then(Commands.literal("off")
                        .executes(SoundSubtree::stopFile))
                .then(Commands.literal("list")
                        .executes(SoundSubtree::list));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> group() {
        return Commands.literal("group")
                .then(Commands.literal("join")
                        .then(Commands.argument("groupname", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(VoiceOps.groupNames(), b))
                                .executes(c -> joinGroup(c, null))
                                .then(Commands.argument("password", StringArgumentType.string())
                                        .executes(c -> joinGroup(c, StringArgumentType.getString(c, "password"))))))
                .then(Commands.literal("leave")
                        .executes(SoundSubtree::leaveGroup))
                .then(Commands.literal("create")
                        .then(Commands.argument("groupname", StringArgumentType.string())
                                .executes(c -> createGroup(c, null))
                                .then(Commands.argument("password", StringArgumentType.string())
                                        .executes(c -> createGroup(c, StringArgumentType.getString(c, "password"))))));
    }

    private static int play(CommandContext<CommandSourceStack> context, boolean loop)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "file");
        return each(context, speaker -> VoiceOps.play(speaker, name, loop),
                speaker -> speaker.getGameProfile().name() + " is saying '" + name + "'" + (loop ? " on loop" : ""));
    }

    private static int stopFile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return each(context, VoiceOps::stop,
                speaker -> speaker.getGameProfile().name() + " stopped playing its sound file");
    }

    private static int joinGroup(CommandContext<CommandSourceStack> context, String password)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "groupname");
        return each(context, speaker -> VoiceOps.joinGroup(speaker, name, password),
                speaker -> speaker.getGameProfile().name() + " joined the voice group '" + name
                        + "' and will only be heard inside it");
    }

    private static int createGroup(CommandContext<CommandSourceStack> context, String password)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "groupname");
        return each(context, speaker -> VoiceOps.createGroup(speaker, name, password),
                speaker -> speaker.getGameProfile().name() + " created the voice group '" + name
                        + "'" + (password == null ? "" : " with a password")
                        + " and will only be heard inside it");
    }

    private static int leaveGroup(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int applied = 0;
        for (ServerPlayer speaker : CommandHelper.requireControllableTargets(context)) {
            boolean wasInGroup = VoiceOps.inGroup(speaker);
            String error = VoiceOps.leaveGroup(speaker);
            if (error != null) {
                context.getSource().sendFailure(Component.literal(error));
                continue;
            }
            applied++;
            context.getSource().sendSuccess(() -> Component.literal(wasInGroup
                    ? speaker.getGameProfile().name() + " left its voice group and can be heard by position again"
                    : speaker.getGameProfile().name() + " was not in a voice group"), false);
        }
        return applied;
    }

    private static int distance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float blocks = FloatArgumentType.getFloat(context, "blocks");
        return each(context, speaker -> VoiceOps.setDistance(speaker, blocks),
                speaker -> blocks <= 0f
                        ? "Reset " + speaker.getGameProfile().name() + "'s voice range to the server default"
                        : "Set " + speaker.getGameProfile().name() + "'s voice range to " + blocks + " blocks");
    }

    private static int resetDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return each(context, speaker -> VoiceOps.setDistance(speaker, 0f),
                speaker -> "Reset " + speaker.getGameProfile().name() + "'s voice range to the server default");
    }

    private static int startBluetooth(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer source = EntityArgument.getPlayer(context, "name");
        UUID sourceId = source.getUUID();
        return each(context, speaker -> VoiceOps.bluetooth(speaker, sourceId),
                speaker -> speaker.getGameProfile().name() + " is now a bluetooth speaker for "
                        + source.getGameProfile().name());
    }

    private static int stopBluetooth(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int applied = 0;
        for (ServerPlayer speaker : CommandHelper.requireControllableTargets(context)) {
            boolean wasLinked = VoiceOps.isBluetoothed(speaker);
            String error = VoiceOps.stopBluetooth(speaker);
            if (error != null) {
                context.getSource().sendFailure(Component.literal(error));
                continue;
            }
            applied++;
            context.getSource().sendSuccess(() -> Component.literal(wasLinked
                    ? speaker.getGameProfile().name() + " is no longer a bluetooth speaker"
                    : speaker.getGameProfile().name() + " was not a bluetooth speaker"), false);
        }
        return applied;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        String blocked = VoiceOps.unavailable();
        if (blocked != null) {
            context.getSource().sendFailure(Component.literal(blocked));
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        List<String> sounds = VoiceOps.soundNames(server);
        if (sounds.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "No sounds yet - drop .wav or .mp3 files into the world's herobot_sounds folder"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Sounds (" + sounds.size() + "): " + String.join(", ", sounds)), false);
        return sounds.size();
    }

    private static int each(CommandContext<CommandSourceStack> context,
                            Function<ServerPlayer, String> action,
                            Function<ServerPlayer, String> success) throws CommandSyntaxException {
        int applied = 0;
        for (ServerPlayer speaker : CommandHelper.requireControllableTargets(context)) {
            String error = action.apply(speaker);
            if (error != null) {
                context.getSource().sendFailure(Component.literal(error));
                continue;
            }
            applied++;
            String message = success.apply(speaker);
            context.getSource().sendSuccess(() -> Component.literal(message), false);
        }
        return applied;
    }
}
