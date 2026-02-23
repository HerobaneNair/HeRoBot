package hero.bane.herobot.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class DistanceCalculator {
    public static List<Component> findDistanceBetweenTwoPoints(Vec3 pos1, Vec3 pos2, int result) {
        double dx = Mth.abs((float) pos1.x - (float) pos2.x);
        double dy = Mth.abs((float) pos1.y - (float) pos2.y);
        double dz = Mth.abs((float) pos1.z - (float) pos2.z);

        double manhattan = dx + dy + dz;
        double spherical = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double cylindrical = Math.sqrt(dx * dx + dz * dz);

        List<Component> res = new ArrayList<>();

        res.add(Component.literal(
                "Distance between " + posToString(pos1) +
                        " and " + posToString(pos2) + ":"
        ));

        res.add(Component.literal(" - Spherical: " + String.format("%.2f", spherical))
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xAAFFFF))));
        res.add(Component.literal("   - Cylindrical: " + String.format("%.2f", cylindrical)));
        res.add(Component.literal("   - Manhattan: " + String.format("%.1f", manhattan)));
        res.add(Component.literal("> Output: " + result)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFAA))));

        return res;
    }

    private static double sphericalDistanceDouble(Vec3 pos1, Vec3 pos2) {
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static int distance(CommandSourceStack source, Vec3 pos1, Vec3 pos2, int exponent) {
        double dist = sphericalDistanceDouble(pos1, pos2);
        int scale = exponent <= 0 ? 1 : (int) Math.pow(10, exponent);
        int result = (int) Math.round(dist * scale);

        List<Component> distances = findDistanceBetweenTwoPoints(pos1, pos2, result);

        for (Component c : distances) {
            source.sendSuccess(() -> c, false);
        }

        return result;
    }

    private static String posToString(Vec3 pos) {
        return String.format("(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
    }
}
