package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.expr.ExprEval;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ExpressionEditor {
    private static final int W = 380;
    private static final int H = 148;
    private static final int BOX_H = 38;

    private final Font font;
    private boolean active;
    private String title = "";
    private MultiLineEditBox box;
    private Consumer<String> onCommit;
    private List<String> varNames = List.of();
    private int px, py;

    private String tabPrefix;
    private int tabIndex;

    private Predicate<String> validator = ExprEval::isValid;
    private String legend1 = ExprEval.OPS_LEGEND;
    private String legend2 = ExprEval.OPS_LEGEND_2;
    private String help1 = "Variables must be in braces, e.g. 5*{x}*3";

    public ExpressionEditor(Font font) {
        this.font = font;
    }

    public boolean isActive() {
        return active;
    }

    public void open(int screenW, int screenH, String title, String initial,
                     List<String> varNames, Consumer<String> onCommit) {
        open(screenW, screenH, title, initial, varNames,
                ExprEval::isValid, ExprEval.OPS_LEGEND, ExprEval.OPS_LEGEND_2,
                "Variables must be in braces, e.g. 5*{x}*3", onCommit);
    }

    public void open(int screenW, int screenH, String title, String initial,
                     List<String> varNames, Predicate<String> validator,
                     String legend1, String legend2, String help1, Consumer<String> onCommit) {
        this.validator = validator;
        this.legend1 = legend1;
        this.legend2 = legend2;
        this.help1 = help1;
        this.title = title;
        this.onCommit = onCommit;
        this.varNames = varNames == null ? List.of() : varNames;
        this.px = (screenW - W) / 2;
        this.py = (screenH - H) / 2;
        this.box = MultiLineEditBox.builder()
                .setX(px + 8)
                .setY(py + 34)
                .setShowDecorations(false)
                .build(font, W - 16, BOX_H, Component.literal(title));
        this.box.setCharacterLimit(256);
        this.box.setValue(initial == null ? "" : initial);
        this.box.setFocused(true);
        this.tabPrefix = null;
        this.tabIndex = 0;
        this.active = true;
    }

    public void close() {
        active = false;
        box = null;
        onCommit = null;
    }

    private void commit() {
        Consumer<String> cb = onCommit;
        String value = box == null ? "" : box.getValue();
        close();
        if (cb != null) cb.accept(value);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!active) return;
        g.fill(0, 0, 100000, 100000, 0x99000000);
        g.fill(px, py, px + W, py + H, 0xFF1E1E1E);
        boolean valid = box == null || validator.test(box.getValue());
        int outline = valid ? 0xFF44BB44 : 0xFFFF5555;
        g.fill(px - 1, py - 1, px + W + 1, py, outline);
        g.fill(px - 1, py + H, px + W + 1, py + H + 1, outline);
        g.drawString(font, title, px + 8, py + 6, 0xFFFFFFFF, false);
        if (box != null) {
            box.render(g, mouseX, mouseY, pt);
            if (!valid) {
                int bx0 = px + 8, by0 = py + 34, bx1 = px + W - 8, by1 = py + 34 + BOX_H;
                g.fill(bx0, by0, bx1, by0 + 1, 0xFFFF5555);
                g.fill(bx0, by1 - 1, bx1, by1, 0xFFFF5555);
                g.fill(bx0, by0, bx0 + 1, by1, 0xFFFF5555);
                g.fill(bx1 - 1, by0, bx1, by1, 0xFFFF5555);
            }
        }

        g.drawString(font, legend1, px + 8, py + 78, 0xFFB9C6FF, false);
        g.drawString(font, legend2, px + 8, py + 90, 0xFFB9C6FF, false);
        String help2 = "press Tab to autocomplete";
        g.drawString(font, help1, px + W - 8 - font.width(help1), py + 104, 0xFF9AD49A, false);
        g.drawString(font, help2, px + W - 8 - font.width(help2), py + 116, 0xFF9AD49A, false);
        g.drawString(font, "Enter = OK   Esc = Cancel", px + 8, py + H - 11, 0xFF909090, false);
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (!active) return false;
        if (box != null) box.mouseClicked(e, doubled);
        return true;
    }

    public boolean keyPressed(KeyEvent e) {
        if (!active) return false;
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            autocomplete();
            return true;
        }
        if (box != null) box.keyPressed(e);
        tabPrefix = null;
        return true;
    }

    public boolean charTyped(CharacterEvent e) {
        if (!active) return false;
        if (box != null) box.charTyped(e);
        tabPrefix = null;
        return true;
    }

    private void autocomplete() {
        if (box == null || varNames.isEmpty()) return;
        String value = box.getValue();
        int end = value.length();
        if (end > 0 && value.charAt(end - 1) == '}') end--;
        int start = end;
        while (start > 0) {
            char c = value.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_') start--;
            else break;
        }
        boolean braceOpen = start > 0 && value.charAt(start - 1) == '{';
        String word = value.substring(start, end);
        String prefix = (tabPrefix != null) ? tabPrefix : word;

        List<String> matches = varNames.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
        if (matches.isEmpty()) return;

        if (!prefix.equals(tabPrefix)) {
            tabPrefix = prefix;
            tabIndex = 0;
        } else {
            tabIndex = (tabIndex + 1) % matches.size();
        }
        String completion = matches.get(tabIndex);
        int from = braceOpen ? start - 1 : start;
        box.setValue(value.substring(0, from) + "{" + completion + "}");
    }
}
