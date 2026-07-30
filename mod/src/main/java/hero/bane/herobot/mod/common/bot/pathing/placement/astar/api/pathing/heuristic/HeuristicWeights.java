package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.heuristic;

public final class HeuristicWeights {
    public static final HeuristicWeights DEFAULT_WEIGHTS = create(1.0, 1.0, 1.0, 1.0);

    private final double manhattanWeight;

    private final double octileWeight;

    private final double perpendicularWeight;

    private final double heightWeight;

    private HeuristicWeights(
            double manhattanWeight,
            double octileWeight,
            double perpendicularWeight,
            double heightWeight) {
        this.manhattanWeight = manhattanWeight;
        this.octileWeight = octileWeight;
        this.perpendicularWeight = perpendicularWeight;
        this.heightWeight = heightWeight;
    }

    public static HeuristicWeights create(
            double manhattanWeight,
            double octileWeight,
            double perpendicularWeight,
            double heightWeight) {
        return new HeuristicWeights(manhattanWeight, octileWeight, perpendicularWeight, heightWeight);
    }

    public double getManhattanWeight() {
        return this.manhattanWeight;
    }

    public double getOctileWeight() {
        return this.octileWeight;
    }

    public double getPerpendicularWeight() {
        return this.perpendicularWeight;
    }

    public double getHeightWeight() {
        return this.heightWeight;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof HeuristicWeights)) return false;
        final HeuristicWeights other = (HeuristicWeights) o;
        if (Double.compare(this.getManhattanWeight(), other.getManhattanWeight()) != 0) return false;
        if (Double.compare(this.getOctileWeight(), other.getOctileWeight()) != 0) return false;
        if (Double.compare(this.getPerpendicularWeight(), other.getPerpendicularWeight()) != 0)
            return false;
        return Double.compare(this.getHeightWeight(), other.getHeightWeight()) == 0;
    }

    @Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + Double.hashCode(this.getManhattanWeight());
        result = result * PRIME + Double.hashCode(this.getOctileWeight());
        result = result * PRIME + Double.hashCode(this.getPerpendicularWeight());
        result = result * PRIME + Double.hashCode(this.getHeightWeight());
        return result;
    }

    @Override
    public String toString() {
        return "HeuristicWeights(manhattanWeight="
                + this.getManhattanWeight()
                + ", octileWeight="
                + this.getOctileWeight()
                + ", perpendicularWeight="
                + this.getPerpendicularWeight()
                + ", heightWeight="
                + this.getHeightWeight()
                + ")";
    }
}
