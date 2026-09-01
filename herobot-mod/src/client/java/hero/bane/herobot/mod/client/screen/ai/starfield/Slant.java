package hero.bane.herobot.mod.client.screen.ai.starfield;

import java.util.Random;

final class Slant {
    private static final int MIN_DEG = -25;
    private static final int MAX_DEG = 25;

    private static final Random RNG = new Random();

    private Slant() {
    }

    static int slanter(double noSlantChance) {
        if (RNG.nextDouble() < noSlantChance) return 0;
        return MIN_DEG + RNG.nextInt(MAX_DEG - MIN_DEG + 1);
    }
}
