package hero.bane.herobot.paper.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.common.rule.HeroBotSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

final class PlayerDataSelector extends EntitySelector {

    private final EntitySelector delegate;

    static EntitySelector wrap(EntitySelector delegate) {
        return new PlayerDataSelector(delegate);
    }

    private PlayerDataSelector(EntitySelector delegate) {
        super(0, false, false, List.of(), null, Function.identity(), null, ORDER_ARBITRARY,
                false, null, null, null, false);
        this.delegate = delegate;
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
        return delegate.findEntities(source);
    }

    @Override
    public @NonNull Entity findSingleEntity(@NonNull CommandSourceStack source) throws CommandSyntaxException {
        Entity entity = delegate.findSingleEntity(source);
        if (!HeroBotSettings.editablePlayerNbt) return entity;
        if (!(entity instanceof ServerPlayer player)) return entity;
        return new PlayerData(player);
    }

    @Override
    public @NonNull List<ServerPlayer> findPlayers(@NonNull CommandSourceStack source) throws CommandSyntaxException {
        return delegate.findPlayers(source);
    }

    @Override
    public @NonNull ServerPlayer findSinglePlayer(@NonNull CommandSourceStack source) throws CommandSyntaxException {
        return delegate.findSinglePlayer(source);
    }

    private static final class PlayerData extends Entity {

        private final ServerPlayer player;

        private PlayerData(ServerPlayer player) {
            super(EntityTypes.MARKER, player.level());
            this.player = player;
        }

        @Override
        public void load(@NonNull ValueInput input) {
            player.load(input);
        }

        @Override
        public void saveWithoutId(@NonNull ValueOutput output) {
            player.saveWithoutId(output);

            ItemStack selected = player.getInventory().getSelectedItem();
            if (!selected.isEmpty()) output.store("SelectedItem", ItemStack.CODEC, selected);
        }

        @Override
        public @NonNull UUID getUUID() {
            return player.getUUID();
        }

        @Override
        public void setUUID(@NonNull UUID uuid) {
            player.setUUID(uuid);
        }

        @Override
        public @NonNull Component getDisplayName() {
            return player.getDisplayName();
        }

        @Override
        public ProblemReporter.@NonNull PathElement problemPath() {
            return player.problemPath();
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        }

        @Override
        protected void readAdditionalSaveData(@NonNull ValueInput input) {
        }

        @Override
        protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        }

        @Override
        public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
            return false;
        }
    }
}
