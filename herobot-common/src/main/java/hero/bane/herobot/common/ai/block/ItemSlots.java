package hero.bane.herobot.common.ai.block;

import java.util.ArrayList;
import java.util.List;

public final class ItemSlots {
    private ItemSlots() {}

    public static final List<String> NAMES = build();

    private static List<String> build() {
        List<String> names = new ArrayList<>(42);
        for (int i = 0; i < 9; i++) names.add("hotbar." + i);
        for (int i = 0; i < 27; i++) names.add("inventory." + i);
        names.add("weapon.mainhand");
        names.add("weapon.offhand");
        names.add("armor.head");
        names.add("armor.chest");
        names.add("armor.legs");
        names.add("armor.feet");
        return List.copyOf(names);
    }

    public static int index(Object value) {
        if (value instanceof Number n) return valid(n.intValue());
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return -1;
        int byName = NAMES.indexOf(s);
        if (byName >= 0) return byName;
        try {
            return valid(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int valid(int index) {
        return index >= 0 && index < NAMES.size() ? index : -1;
    }
}
