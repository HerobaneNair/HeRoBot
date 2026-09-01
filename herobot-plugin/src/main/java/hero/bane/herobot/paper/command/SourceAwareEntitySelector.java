package hero.bane.herobot.paper.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Function;

final class SourceAwareEntitySelector extends EntitySelector {

    private final EntitySelector delegate;
    private final SourceAwareSelectorOptions.Capture capture;

    SourceAwareEntitySelector(EntitySelector delegate, SourceAwareSelectorOptions.Capture capture) {
        super(0, false, false, List.of(), null, Function.identity(), null, ORDER_ARBITRARY,
                false, null, null, null, false);
        this.delegate = delegate;
        this.capture = capture;
    }

    @Override
    public int getMaxResults() {
        return delegate.getMaxResults();
    }

    @Override
    public boolean includesEntities() {
        return delegate.includesEntities();
    }

    @Override
    public boolean isSelfSelector() {
        return delegate.isSelfSelector();
    }

    @Override
    public boolean isWorldLimited() {
        return delegate.isWorldLimited();
    }

    @Override
    public boolean usesSelector() {
        return delegate.usesSelector();
    }

    @Override
    public @NonNull List<? extends Entity> findEntities(@NonNull CommandSourceStack source) throws CommandSyntaxException {
        return capture.scoped(source, () -> delegate.findEntities(source));
    }

    @Override
    public @NonNull Entity findSingleEntity(@NonNull CommandSourceStack source) throws CommandSyntaxException {
        return capture.scoped(source, () -> delegate.findSingleEntity(source));
    }

    @Override
    public @NonNull List<ServerPlayer> findPlayers(@NonNull CommandSourceStack source) throws CommandSyntaxException {
        return capture.scoped(source, () -> delegate.findPlayers(source));
    }

    @Override
    public @NonNull ServerPlayer findSinglePlayer(@NonNull CommandSourceStack source) throws CommandSyntaxException {
        return capture.scoped(source, () -> delegate.findSinglePlayer(source));
    }
}
