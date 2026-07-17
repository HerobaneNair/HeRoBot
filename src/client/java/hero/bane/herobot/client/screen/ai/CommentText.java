package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.Comment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class CommentText {
    private CommentText() {}

    static int width(Font font, Comment c, int from, int to) {
        String t = c.text();
        from = Math.clamp(from, 0, t.length());
        to = Math.clamp(to, from, t.length());
        int total = 0;
        int i = from;
        while (i < to) {
            int style = c.styleAt(i);
            int j = i + 1;
            while (j < to && c.styleAt(j) == style) j++;
            total += font.width(run(t.substring(i, j), style));
            i = j;
        }
        return total;
    }

    static int charWidth(Font font, Comment c, int i) {
        return font.width(run(String.valueOf(c.text().charAt(i)), c.styleAt(i)));
    }

    static void draw(GuiGraphics g, Font font, Comment c, int from, int to, int x, int y, int color) {
        String t = c.text();
        from = Math.clamp(from, 0, t.length());
        to = Math.clamp(to, from, t.length());
        int i = from;
        while (i < to) {
            int style = c.styleAt(i);
            int j = i + 1;
            while (j < to && c.styleAt(j) == style) j++;
            Component comp = run(t.substring(i, j), style);
            g.drawString(font, comp, x, y, color, false);
            x += font.width(comp);
            i = j;
        }
    }

    private static Component run(String s, int style) {
        return Component.literal(s).withStyle(st -> st
                .withBold((style & Comment.BOLD) != 0)
                .withItalic((style & Comment.ITALIC) != 0)
                .withUnderlined((style & Comment.UNDERLINE) != 0)
                .withStrikethrough((style & Comment.STRIKE) != 0));
    }
}
