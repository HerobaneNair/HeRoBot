package hero.bane.herobot.mod.common.ai.runtime.executors;

import hero.bane.herobot.mod.common.ai.block.BlockInstance;
import hero.bane.herobot.mod.common.ai.block.BlockType;
import hero.bane.herobot.mod.common.ai.block.ItemSlots;
import hero.bane.herobot.mod.common.ai.runtime.Branch;
import hero.bane.herobot.mod.common.ai.runtime.ParamEval;
import hero.bane.herobot.mod.common.ai.runtime.Reporter;
import hero.bane.herobot.mod.common.ai.runtime.ScriptRunner;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.ActionType;
import hero.bane.herobot.mod.common.control.PlayerControllers;
import hero.bane.herobot.mod.common.util.DistanceCalculator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.Locale;
import java.util.Map;

public final class SensorExecutor {
    private SensorExecutor() {}

    private static final Map<String, EquipmentSlot> EQUIP = Map.of(
            "weapon.mainhand", EquipmentSlot.MAINHAND,
            "weapon.offhand", EquipmentSlot.OFFHAND,
            "armor.head", EquipmentSlot.HEAD,
            "armor.chest", EquipmentSlot.CHEST,
            "armor.legs", EquipmentSlot.LEGS,
            "armor.feet", EquipmentSlot.FEET
    );

    public static void register(Map<BlockType, Reporter> reporter) {
        reporter.put(BlockType.HEALTH, (b, r, br) -> {
            Entity e = subject(b, r, br, "target");
            return e instanceof LivingEntity le ? (double) le.getHealth() : 0.0;
        });
        reporter.put(BlockType.POSITION, (b, r, br) -> subject(b, r, br, "target").position());
        reporter.put(BlockType.YAW, (b, r, br) -> {
            Entity e = subject(b, r, br, "target");
            String axis = ParamEval.evalString(b, "axis", r, br);
            return (double) ("pitch".equals(axis) ? e.getXRot() : e.getYRot());
        });

        reporter.put(BlockType.TIME_OF_DAY, (b, r, br) -> r.player().level().getDayTime() % 24000L);

        reporter.put(BlockType.EVERY_X_TICKS, (b, r, br) -> {
            int n = Math.max(1, ParamEval.evalInt(b, "ticks", r, br));
            return r.player().level().getGameTime() % n == 0L;
        });

        reporter.put(BlockType.DOING_ACTION, (b, r, br) -> {
            String action = ParamEval.evalString(b, "action", r, br);
            Entity e = subject(b, r, br, "target");
            BotPlayerActionPack ap = e instanceof ServerPlayer sp
                    && PlayerControllers.of(sp) instanceof BotPlayerActionPack p ? p : null;
            return switch (action == null ? "" : action) {
                case "walking" -> ap != null && ap.getForward() != 0;
                case "strafing" -> ap != null && ap.getStrafing() != 0;
                case "sprinting" -> ap != null ? ap.isSprinting() : e.isSprinting();
                case "sneaking" -> ap != null ? ap.isSneaking() : e.isShiftKeyDown();
                case "using" -> ap != null ? ap.isActive(ActionType.USE)
                        : e instanceof LivingEntity le && le.isUsingItem();
                case "attacking" -> ap != null && ap.isActive(ActionType.ATTACK);
                case "swinging" -> ap != null && ap.isActive(ActionType.SWING);
                case "jumping" -> ap != null && ap.isActive(ActionType.JUMP);
                case "dropping" -> ap != null
                        && (ap.isActive(ActionType.DROP_ITEM) || ap.isActive(ActionType.DROP_STACK));
                case "swapping" -> ap != null && ap.isActive(ActionType.SWAP_HANDS);
                default -> false;
            };
        });

        reporter.put(BlockType.ON_DAMAGE, (b, r, br) -> {
            Entity e = subject(b, r, br, "target");
            if (e == r.player()) return r.wasDamaged();
            return e instanceof LivingEntity le && le.hurtTime > 0 && le.hurtTime == le.hurtDuration;
        });

        reporter.put(BlockType.ATTACK_COOLDOWN, (b, r, br) -> {
            Entity e = subject(b, r, br, "target");
            boolean ticks = "ticksLeft".equals(ParamEval.evalString(b, "kind", r, br));
            if (!(e instanceof Player p)) return ticks ? 0.0 : 1.0;
            if (ticks) {
                float delay = p.getCurrentItemAttackStrengthDelay();
                return (double) Math.max(0, (int) Math.ceil(delay * (1.0F - p.getAttackStrengthScale(0.0F))));
            }
            return (double) p.getAttackStrengthScale(0.5F);
        });

        reporter.put(BlockType.HURT_TIME, (b, r, br) -> {
            Entity e = subject(b, r, br, "target");
            return e instanceof LivingEntity le ? le.hurtTime : 0;
        });

        reporter.put(BlockType.PING, (b, r, br) -> {
            Entity e = subject(b, r, br, "target");
            if (e instanceof BotPlayer bot) return bot.ping;
            if (e instanceof ServerPlayer sp && sp.connection != null) return sp.connection.latency();
            return 0;
        });

        reporter.put(BlockType.IN_PATH, (b, r, br) -> {
            BotPlayer bot = r.bot();
            if (bot != null) return bot.getPathFollower() != null && !bot.getPathFollower().isDone();
            return hero.bane.herobot.mod.common.control.RemotePathState.isActive(r.player());
        });

        reporter.put(BlockType.PATH_REACHED, (b, r, br) -> r.pathReached());
        reporter.put(BlockType.PATH_FAILED, (b, r, br) -> r.pathFailed());

        reporter.put(BlockType.BLOCK_AT, (b, r, br) -> {
            Entity base = subject(b, r, br, "target");
            Vec3 pos = ParamEval.asVec3(ParamEval.raw(b, "position", r, br), base);
            BlockPos bp = BlockPos.containing(pos);
            BlockState st = base.level().getBlockState(bp);
            Identifier rl = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            return rl.toString();
        });

        reporter.put(BlockType.IS_TOUCHING_BLOCK, (b, r, br) -> {
            String blockId = ParamEval.evalString(b, "block", r, br);
            Identifier rl = Identifier.tryParse(blockId);
            if (rl == null) return false;
            Block target = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (target == null) return false;
            Entity e = subject(b, r, br, "target");
            AABB box = e.getBoundingBox().inflate(0.001);
            Level level = e.level();
            BlockPos minBp = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos maxBp = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            for (BlockPos bp : BlockPos.betweenClosed(minBp, maxBp)) {
                if (level.getBlockState(bp).is(target)) return true;
            }
            return false;
        });

        reporter.put(BlockType.HAS_TAG, (b, r, br) -> {
            Object sel = ParamEval.raw(b, "target", r, br);
            String tag = ParamEval.evalString(b, "tag", r, br);
            Entity e = SelectorExecutor.resolveFirstEntity(sel, r);
            return e != null && e.getTags().contains(tag);
        });

        reporter.put(BlockType.DISTANCE_TO, (b, r, br) -> {
            Object sel = ParamEval.raw(b, "target", r, br);
            Entity e = SelectorExecutor.resolveFirstEntity(sel, r);
            if (e == null) return Double.POSITIVE_INFINITY;
            Entity from = subject(b, r, br, "from");
            Vec3[] closest = DistanceCalculator.closestPointsBetween(
                    distanceShape(from, ParamEval.evalString(b, "fromShape", r, br)),
                    distanceShape(e, ParamEval.evalString(b, "toShape", r, br)));
            Vec3 a = closest[0];
            Vec3 c = closest[1];
            String mode = ParamEval.evalString(b, "mode", r, br);
            if (mode == null) mode = "normal";
            return switch (mode.trim().toLowerCase(Locale.ROOT)) {
                case "horizontal" -> Math.sqrt(
                        (a.x - c.x) * (a.x - c.x) + (a.z - c.z) * (a.z - c.z));
                case "vertical" -> Math.abs(a.y - c.y);
                default -> a.distanceTo(c);
            };
        });

        reporter.put(BlockType.SCOREBOARD, (b, r, br) -> {
            String obj = ParamEval.evalString(b, "objective", r, br);
            Object sel = ParamEval.raw(b, "target", r, br);
            Entity e = SelectorExecutor.resolveFirstEntity(sel, r);
            if (e == null) return 0;
            MinecraftServer server = r.player().level().getServer();
            Scoreboard sb = server.getScoreboard();
            Objective o = sb.getObjective(obj);
            if (o == null) return 0;
            return sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(e.getScoreboardName()), o).get();
        });

        reporter.put(BlockType.ITEM_IN_SLOT, (b, r, br) -> {
            int slot = ItemSlots.index(ParamEval.raw(b, "slot", r, br));
            return slotStack(r.player(), slot).copy();
        });

        reporter.put(BlockType.EQUIPMENT, (b, r, br) -> {
            Object sel = ParamEval.raw(b, "target", r, br);
            String slot = ParamEval.evalString(b, "slot", r, br);
            Entity e = SelectorExecutor.resolveFirstEntity(sel, r);
            EquipmentSlot es = EQUIP.get(slot);
            if (!(e instanceof LivingEntity le) || es == null) return ItemStack.EMPTY;
            return le.getItemBySlot(es).copy();
        });

        reporter.put(BlockType.GET_COUNT, (b, r, br) -> {
            ItemStack st = slotStack(r.player(), ItemSlots.index(ParamEval.raw(b, "slot", r, br)));
            if ("durability".equals(ParamEval.evalString(b, "kind", r, br))) {
                return st.isDamageableItem() ? st.getMaxDamage() - st.getDamageValue() : 0;
            }
            return st.getCount();
        });

        reporter.put(BlockType.MAX_COUNT, (b, r, br) -> {
            ItemStack st = DataExecutor.asItemStack(ParamEval.raw(b, "item", r, br), r);
            if ("durability".equals(ParamEval.evalString(b, "kind", r, br))) {
                return st.isDamageableItem() ? st.getMaxDamage() : 0;
            }
            return st.getMaxStackSize();
        });

        reporter.put(BlockType.COMMAND_RESULT, (b, r, br) -> {
            String cmd = ParamEval.evalString(b, "command", r, br).trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            MinecraftServer server = r.player().level().getServer();
            if (cmd.isEmpty()) return 0.0;
            int[] result = {0};
            CommandSourceStack src = r.player().createCommandSourceStack()
                    .withPermission(PermissionSet.ALL_PERMISSIONS)
                    .withSuppressedOutput()
                    .withCallback((success, value) -> result[0] = value);
            server.getCommands().performPrefixedCommand(src, cmd);
            return (double) result[0];
        });
    }

    private static AABB distanceShape(Entity e, String shape) {
        if (shape != null && shape.trim().equalsIgnoreCase("hitbox")) return e.getBoundingBox();
        Vec3 pos = e.position();
        return new AABB(pos, pos);
    }

    private static Entity subject(BlockInstance b, ScriptRunner r, Branch br, String slot) {
        if (b.getParam(slot) == null && b.getReporter(slot) == null) return r.player();
        Entity e = SelectorExecutor.resolveFirstEntity(ParamEval.raw(b, slot, r, br), r);
        return e != null ? e : r.player();
    }

    private static ItemStack slotStack(ServerPlayer player, int index) {
        if (index < 0) return ItemStack.EMPTY;
        if (index < 36) return player.getInventory().getItem(index);
        EquipmentSlot es = switch (index) {
            case 36 -> EquipmentSlot.MAINHAND;
            case 37 -> EquipmentSlot.OFFHAND;
            case 38 -> EquipmentSlot.HEAD;
            case 39 -> EquipmentSlot.CHEST;
            case 40 -> EquipmentSlot.LEGS;
            case 41 -> EquipmentSlot.FEET;
            default -> null;
        };
        return es == null ? ItemStack.EMPTY : player.getItemBySlot(es);
    }
}
