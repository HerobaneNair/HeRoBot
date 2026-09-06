package hero.bane.herobot.paper.bot.pathing;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import hero.bane.herobot.common.bot.pathing.DebugChannel;

public class PathSettings {

    public enum MoveType {
        WALK, SPRINT, SPRINT_JUMP;

        public String displayName() {
            return switch (this) {
                case WALK -> "walk";
                case SPRINT -> "sprint";
                case SPRINT_JUMP -> "sprint-jump";
            };
        }
    }

    private final Set<Block> avoidedBlocks = new LinkedHashSet<>();
    private MoveType moveType = MoveType.SPRINT;
    private double maxHorizontalDistance = 1.0;
    private double maxVerticalDistance = 2.0;
    private double nodeHorizontalDistance = 0.1;
    private double nodeVerticalDistance = 0.5;
    private boolean stopFollowing = true;
    private double horizontalMoveCost = 1.0;
    private double verticalMoveCost = 1.5;
    private double swimCostMultiplier = 3.0;
    private final EnumSet<DebugChannel> debugChannels = EnumSet.noneOf(DebugChannel.class);

    public PathSettings() {
        avoidedBlocks.add(Blocks.LAVA);
        avoidedBlocks.add(Blocks.MAGMA_BLOCK);
        avoidedBlocks.add(Blocks.FIRE);
        avoidedBlocks.add(Blocks.SOUL_FIRE);
        avoidedBlocks.add(Blocks.CAMPFIRE);
        avoidedBlocks.add(Blocks.SOUL_CAMPFIRE);
        avoidedBlocks.add(Blocks.POWDER_SNOW);

        avoidedBlocks.add(Blocks.CACTUS);
        avoidedBlocks.add(Blocks.SWEET_BERRY_BUSH);
        avoidedBlocks.add(Blocks.WITHER_ROSE);
        avoidedBlocks.add(Blocks.POINTED_DRIPSTONE);

        avoidedBlocks.add(Blocks.COBWEB);
        avoidedBlocks.add(Blocks.SCULK_SHRIEKER);

        avoidedBlocks.add(Blocks.NETHER_PORTAL);
        avoidedBlocks.add(Blocks.END_PORTAL);
        avoidedBlocks.add(Blocks.END_GATEWAY);

        avoidedBlocks.add(Blocks.RAIL);
        avoidedBlocks.add(Blocks.POWERED_RAIL);
        avoidedBlocks.add(Blocks.DETECTOR_RAIL);
        avoidedBlocks.add(Blocks.ACTIVATOR_RAIL);
    }

    public void copyFrom(PathSettings other) {
        this.avoidedBlocks.clear();
        this.avoidedBlocks.addAll(other.avoidedBlocks);
        this.moveType = other.moveType;
        this.maxHorizontalDistance = other.maxHorizontalDistance;
        this.maxVerticalDistance = other.maxVerticalDistance;
        this.nodeHorizontalDistance = other.nodeHorizontalDistance;
        this.nodeVerticalDistance = other.nodeVerticalDistance;
        this.stopFollowing = other.stopFollowing;
        this.horizontalMoveCost = other.horizontalMoveCost;
        this.verticalMoveCost = other.verticalMoveCost;
        this.swimCostMultiplier = other.swimCostMultiplier;
        this.debugChannels.clear();
        this.debugChannels.addAll(other.debugChannels);
    }

    public boolean isNotAvoided(Block block) {
        return !avoidedBlocks.contains(block);
    }

    public Set<Block> getAvoidedBlocks() {
        return Collections.unmodifiableSet(avoidedBlocks);
    }

    public void addAvoidedBlock(Block block) {
        avoidedBlocks.add(block);
    }

    public boolean removeAvoidedBlock(Block block) {
        return avoidedBlocks.remove(block);
    }

    public void clearAvoidedBlocks() {
        avoidedBlocks.clear();
    }

    public MoveType getMoveType() {
        return moveType;
    }

    public void setMoveType(MoveType moveType) {
        this.moveType = moveType;
    }

    public double getMaxHorizontalDistance() {
        return maxHorizontalDistance;
    }

    public void setMaxHorizontalDistance(double value) {
        if (value <= 0) return;
        this.maxHorizontalDistance = value;
    }

    public double getMaxVerticalDistance() {
        return maxVerticalDistance;
    }

    public void setMaxVerticalDistance(double value) {
        this.maxVerticalDistance = value;
    }

    public double getNodeHorizontalDistance() {
        return nodeHorizontalDistance;
    }

    public void setNodeHorizontalDistance(double value) {
        if (value <= 0) return;
        this.nodeHorizontalDistance = value;
    }

    public double getNodeVerticalDistance() {
        return nodeVerticalDistance;
    }

    public void setNodeVerticalDistance(double value) {
        this.nodeVerticalDistance = value;
    }

    public boolean isStopFollowing() {
        return stopFollowing;
    }

    public void setStopFollowing(boolean value) {
        this.stopFollowing = value;
    }

    public double getHorizontalMoveCost() {
        return horizontalMoveCost;
    }

    public void setHorizontalMoveCost(double value) {
        if (value <= 0) return;
        this.horizontalMoveCost = value;
    }

    public double getVerticalMoveCost() {
        return verticalMoveCost;
    }

    public void setVerticalMoveCost(double value) {
        if (value <= 0) return;
        this.verticalMoveCost = value;
    }

    public double getSwimCostMultiplier() {
        return swimCostMultiplier;
    }

    public void setSwimCostMultiplier(double value) {
        if (value <= 0) return;
        this.swimCostMultiplier = value;
    }

    public void calculateSwimCost(double waterMovementEfficiency, boolean hasDolphinsGrace) {
        double base = 3.0;
        base *= (1.0 - waterMovementEfficiency * 0.9);
        if (hasDolphinsGrace) base *= 0.3;
        this.swimCostMultiplier = Math.max(0.1, base);
    }

    public boolean isDebug() {
        return !debugChannels.isEmpty();
    }

    public void setDebug(boolean value) {
        debugChannels.clear();
        if (value) {
            debugChannels.addAll(EnumSet.allOf(DebugChannel.class));
        }
    }

    public boolean isDebugEnabled(DebugChannel channel) {
        return debugChannels.contains(channel);
    }

    public void setDebugChannel(DebugChannel channel, boolean enabled) {
        if (enabled) {
            debugChannels.add(channel);
        } else {
            debugChannels.remove(channel);
        }
    }

    public boolean toggleDebugChannel(DebugChannel channel) {
        boolean enabled = !debugChannels.contains(channel);
        setDebugChannel(channel, enabled);
        return enabled;
    }

    public Set<DebugChannel> getDebugChannels() {
        return Collections.unmodifiableSet(debugChannels);
    }

    public String describeDebug() {
        if (debugChannels.isEmpty()) return "none";
        if (debugChannels.size() == DebugChannel.values().length) return "all";
        return debugChannels.stream().map(DebugChannel::id).collect(Collectors.joining(", "));
    }

    public boolean isWithinTarget(double hDist, double vDist) {
        boolean hOk = hDist <= maxHorizontalDistance;
        boolean vOk = maxVerticalDistance < 0 || vDist <= maxVerticalDistance;
        return hOk && vOk;
    }

    public boolean isWithinNode(double hDist, double vDist) {
        boolean hOk = hDist <= nodeHorizontalDistance;
        boolean vOk = nodeVerticalDistance < 0 || vDist <= nodeVerticalDistance;
        return hOk && vOk;
    }
}
