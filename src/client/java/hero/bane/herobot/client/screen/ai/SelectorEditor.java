package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.SelectorValidation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public final class SelectorEditor {
    private static final int W = 380;
    private static final int H = 132;
    private static final int BOX_H = 24;

    private static final int VALID = 0xFFAA55FF;
    private static final int INVALID = 0xFFFF5555;

    private final Font font;
    private boolean active;
    private String title = "";
    private boolean allowUuid;
    private MultiLineEditBox box;
    private Consumer<String> onCommit;
    private int px, py;

    public SelectorEditor(Font font) {
        this.font = font;
    }

    public boolean isActive() {
        return active;
    }

    public void open(int screenW, int screenH, String title, String initial,
                     boolean allowUuid, Consumer<String> onCommit) {
        this.title = title;
        this.allowUuid = allowUuid;
        this.onCommit = onCommit;
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

    private boolean valid() {
        if (box == null) return true;
        String v = box.getValue();
        if (SelectorValidation.isValidSingle(v)) return true;
        return allowUuid && SelectorValidation.isUuid(v);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!active) return;
        g.fill(0, 0, 100000, 100000, 0x99000000);
        g.fill(px, py, px + W, py + H, 0xFF1E1E1E);
        boolean valid = valid();
        int outline = valid ? VALID : INVALID;
        g.fill(px - 1, py - 1, px + W + 1, py, outline);
        g.fill(px - 1, py + H, px + W + 1, py + H + 1, outline);
        g.drawString(font, title, px + 8, py + 6, 0xFFFFFFFF, false);
        if (box != null) {
            box.render(g, mouseX, mouseY, pt);
            int bx0 = px + 8, by0 = py + 34, bx1 = px + W - 8, by1 = py + 34 + BOX_H;
            int edge = valid ? VALID : INVALID;
            g.fill(bx0, by0, bx1, by0 + 1, edge);
            g.fill(bx0, by1 - 1, bx1, by1, edge);
            g.fill(bx0, by0, bx0 + 1, by1, edge);
            g.fill(bx1 - 1, by0, bx1, by1, edge);
        }

        String help1 = "Must select exactly one entity";
        String help2 = "Use @n, @p, @r, @s - or @e/@a with limit=1";
        g.drawString(font, help1, px + 8, py + 70, 0xFFCBA6FF, false);
        g.drawString(font, help2, px + 8, py + 82, 0xFFCBA6FF, false);
        if (allowUuid) {
            g.drawString(font, "A dashed UUID is also accepted", px + 8, py + 94, 0xFFCBA6FF, false);
        }
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
        if (box != null) box.keyPressed(e);
        return true;
    }

    public boolean charTyped(CharacterEvent e) {
        if (!active) return false;
        if (box != null) box.charTyped(e);
        return true;
    }
}
