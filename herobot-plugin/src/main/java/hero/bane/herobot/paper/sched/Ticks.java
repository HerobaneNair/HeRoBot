package hero.bane.herobot.paper.sched;

import org.bukkit.Bukkit;

public final class Ticks {

    private Ticks() {
    }

    public static long current() {
        return Bukkit.getCurrentTick();
    }
}
