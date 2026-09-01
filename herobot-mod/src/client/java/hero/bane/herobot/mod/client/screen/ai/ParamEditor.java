package hero.bane.herobot.mod.client.screen.ai;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ParamEditor {
    private static final int W = 200;
    private static final int H = 56;

    private final Font font;
    private boolean active;
    private String title = "";
    private EditBox box;
    private Consumer<String> onCommit;
    private Predicate<String> validator;
    private String actionLabel;
    private Runnable action;
    private int dialogH = H;
    private int px, py;

    public ParamEditor(Font font) {
        this.font = font;
    }

    public boolean isActive() {
        return active;
    }

    public void open(int screenW, int screenH, String title, String initial, Consumer<String> onCommit) {
        open(screenW, screenH, title, initial, null, onCommit);
    }

    public void open(int screenW, int screenH, String title, String initial,
                     Predicate<String> validator, Consumer<String> onCommit) {
        open(screenW, screenH, title, initial, validator, onCommit, null, null);
    }

    public void open(int screenW, int screenH, String title, String initial,
                     Predicate<String> validator, Consumer<String> onCommit,
                     String actionLabel, Runnable action) {
        this.title = title;
        this.onCommit = onCommit;
        this.validator = validator;
        this.actionLabel = actionLabel;
        this.action = action;
        this.dialogH = actionLabel == null ? H : H + 20;
        this.px = (screenW - W) / 2;
        this.py = (screenH - dialogH) / 2;
        this.box = new EditBox(font, px + 8, py + 22, W - 16, 16, Component.literal(title));
        this.box.setMaxLength(256);
        this.box.setValue(initial == null ? "" : initial);
        this.box.setFocused(true);
        this.active = true;
    }

    public void selectAll() {
        if (box == null) return;
        box.moveCursorToEnd(false);
        box.setHighlightPos(0);
    }

    public void close() {
        active = false;
        box = null;
        onCommit = null;
        validator = null;
        actionLabel = null;
        action = null;
        dialogH = H;
    }

    private int btnX0() { return px + 8; }
    private int btnX1() { return px + W - 8; }
    private int btnY0() { return py + 42; }
    private int btnY1() { return py + 58; }

    private void commit() {
        Consumer<String> cb = onCommit;
        String value = box == null ? "" : box.getValue();
        close();
        if (cb != null) cb.accept(value);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (!active) return;
        g.fill(0, 0, 100000, 100000, 0x99000000);
        g.fill(px, py, px + W, py + dialogH, 0xFF1E1E1E);
        boolean valid = validator == null || box == null || validator.test(box.getValue());
        int outline = validator == null ? 0xFFFFFFFF : valid ? 0xFF44BB44 : 0xFFFF5555;
        g.fill(px - 1, py - 1, px + W + 1, py, outline);
        g.fill(px - 1, py + dialogH, px + W + 1, py + dialogH + 1, outline);
        g.drawString(font, title, px + 8, py + 6, 0xFFFFFFFF, false);
        g.drawString(font, "Enter = OK   Esc = Cancel", px + 8, py + dialogH - 10, 0xFF909090, false);
        if (actionLabel != null) {
            boolean hov = mouseX >= btnX0() && mouseX < btnX1() && mouseY >= btnY0() && mouseY < btnY1();
            int col = hov ? 0xFFFFCC22 : 0xFFDD9900;
            g.fill(btnX0(), btnY0(), btnX1(), btnY0() + 1, col);
            g.fill(btnX0(), btnY1() - 1, btnX1(), btnY1(), col);
            g.fill(btnX0(), btnY0(), btnX0() + 1, btnY1(), col);
            g.fill(btnX1() - 1, btnY0(), btnX1(), btnY1(), col);
            int tw = font.width(actionLabel);
            g.drawString(font, actionLabel, px + (W - tw) / 2, btnY0() + 4,
                    hov ? 0xFFFFFFFF : 0xFFD0D0D0, false);
        }
        if (box != null) {
            box.render(g, mouseX, mouseY, pt);
            if (!valid) {
                int bx0 = px + 7, by0 = py + 21, bx1 = px + W - 7, by1 = py + 39;
                g.fill(bx0, by0, bx1, by0 + 1, 0xFFFF5555);
                g.fill(bx0, by1 - 1, bx1, by1, 0xFFFF5555);
                g.fill(bx0, by0, bx0 + 1, by1, 0xFFFF5555);
                g.fill(bx1 - 1, by0, bx1, by1, 0xFFFF5555);
            }
        }
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        if (!active) return false;
        if (actionLabel != null && e.x() >= btnX0() && e.x() < btnX1()
                && e.y() >= btnY0() && e.y() < btnY1()) {
            Runnable a = action;
            close();
            if (a != null) a.run();
            return true;
        }
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
