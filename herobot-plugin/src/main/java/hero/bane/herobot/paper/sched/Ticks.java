package hero.bane.herobot.paper.sched;

import net.minecraft.world.entity.Entity;
import org.bukkit.craftbukkit.entity.CraftEntity;

public final class Ticks {

    private Ticks() {
    }

    public static long of(Entity entity) {
        return entity.tickCount;
    }

    public static long of(org.bukkit.entity.Entity entity) {
        return ((CraftEntity) entity).getHandle().tickCount;
    }
}
