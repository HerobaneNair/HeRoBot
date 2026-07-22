package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.AiScriptIO;
import hero.bane.herobot.ai.Comment;
import hero.bane.herobot.ai.FuncDecl;
import hero.bane.herobot.ai.VarType;
import hero.bane.herobot.ai.block.*;
import hero.bane.herobot.client.EditorDraft;
import hero.bane.herobot.client.EditorPrefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

public final class AiEditorScreen extends Screen {
    private AiScript script = new AiScript("untitled");
    private int selectedId = -1;
    private final Set<Integer> selection = new LinkedHashSet<>();
    private String runTarget = "@p[]";

    private ScriptCanvas canvas;
    private BlockPalette palette;
    private VariablePanel varPanel;
    private Toolbar toolbar;
    private ParamEditor paramEditor;
    private ExpressionEditor exprEditor;
    private SelectorEditor selectorEditor;

    private String clipboard;
    private static final int HISTORY_CAP = 100;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    private boolean loadDialogOpen;
    private boolean importMode;
    private boolean deleteMode;
    private boolean pendingImport;
    private List<String> serverScripts = new ArrayList<>();

    private boolean settingsOpen;
    private boolean shortcutsOpen;
    private String loadFailedName;
    private int autosaveTicks;
    private String lastSavedJson;
    private boolean positionsSorted;
    private boolean pendingTidyOnInit;
    private static final String TUTORIAL_URL = "https://www.youtube.com/playlist?list=PLPbpz2d-OZK8";

    private BlockType paletteDragType;
    private BlockType paletteHoverType;
    private BlockType canvasHoverType;
    private BlockType canvasWireTargetType;
    private double paletteDragX, paletteDragY;
    private int paletteX, paletteY, paletteW, paletteH;

    private static final int SIDE_W = 120;
    private static final int VAR_W = 140;
    private static final int ARROW = 9;
    private boolean leftCollapsed, rightCollapsed, topCollapsed;

    private static final long PEEK_OPEN_MS = 200;
    private static final long PEEK_CLOSE_MS = 2000;

    private static final class Region {
        SidebarMode mode = SidebarMode.MAXIMIZED;
        boolean peekOpen;
        long openHoverStart;
        long closeStart;

        boolean collapsed() {
            return mode == SidebarMode.MINIMIZED || (mode == SidebarMode.HOVER && !peekOpen);
        }

        void cycle(boolean toAuto) {
            if (toAuto) {
                mode = mode == SidebarMode.HOVER ? SidebarMode.MAXIMIZED : SidebarMode.HOVER;
            } else {
                mode = mode == SidebarMode.MINIMIZED ? SidebarMode.MAXIMIZED : SidebarMode.MINIMIZED;
            }
            peekOpen = false;
            openHoverStart = 0;
            closeStart = 0;
        }
    }

    private final Region leftRegion = new Region();
    private final Region rightRegion = new Region();

    private String statusMessage;
    private int statusTicks;
    private int statusFadeTicks;

    private void setStatus(String message) {
        setStatus(message, 100, 0);
    }

    private void setStatus(String message, int totalTicks, int fadeTicks) {
        this.statusMessage = message;
        this.statusTicks = totalTicks;
        this.statusFadeTicks = fadeTicks;
    }

    public AiEditorScreen() {
        super(Component.literal("HeroBot Scripter"));
        String draft = EditorDraft.load();
        if (draft != null) {
            try {
                this.script = AiScriptIO.fromJson(draft, "untitled");
                positionsSorted = AiScriptIO.wasSorted(draft);
                pendingTidyOnInit = positionsSorted;
            } catch (RuntimeException ignored) {
            }
        }
        lastSavedJson = snapshotJson();
    }

    private String snapshotJson() {
        try {
            return AiScriptIO.toJson(script, positionsSorted);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public AiScript script() { return script; }
    public int selectedId() { return selectedId; }
    public BlockType paletteDragType() { return paletteDragType; }
    public BlockType paletteHoverType() { return paletteHoverType; }
    public BlockType canvasHoverType() { return canvasHoverType; }
    public BlockType canvasWireTargetType() { return canvasWireTargetType; }

    public Set<Integer> selection() { return selection; }
    public boolean isSelected(int id) { return selection.contains(id); }

    public void select(int id) {
        selection.clear();
        if (id >= 0) selection.add(id);
        this.selectedId = id;
    }

    public void toggleSelect(int id) {
        if (id < 0) return;
        if (!selection.remove(id)) selection.add(id);
        this.selectedId = selection.contains(id) ? id
                : (selection.isEmpty() ? -1 : selection.iterator().next());
    }

    public void selectMany(Collection<Integer> ids, boolean additive) {
        if (!additive) selection.clear();
        selection.addAll(ids);
        if (!ids.isEmpty()) selectedId = ids.iterator().next();
        else if (selection.isEmpty()) selectedId = -1;
    }

    private void selectConnected() {
        if (selection.isEmpty() && selectedId < 0) {
            selectMany(new ArrayList<>(script.blocks().keySet()), false);
            return;
        }
        Set<Integer> visited = new LinkedHashSet<>(selection);
        if (selectedId >= 0) visited.add(selectedId);
        ArrayDeque<Integer> queue = new ArrayDeque<>(visited);
        while (!queue.isEmpty()) {
            int id = queue.poll();
            for (Wire w : script.wires()) {
                int other = w.fromBlockId() == id ? w.toBlockId()
                        : w.toBlockId() == id ? w.fromBlockId() : -1;
                if (other >= 0 && visited.add(other)) queue.add(other);
            }
        }
        selectMany(visited, false);
    }

    @Override
    protected void init() {
        ScriptTransfer.setActive(this);
        ScriptTransfer.requestList();

        palette = new BlockPalette(font);
        varPanel = new VariablePanel(font, this);
        canvas = new ScriptCanvas(this, font);

        toolbar = new Toolbar(font, this);

        paramEditor = new ParamEditor(font);
        exprEditor = new ExpressionEditor(font);
        selectorEditor = new SelectorEditor(font);

        leftRegion.mode = EditorPrefs.leftPanelMode();
        rightRegion.mode = EditorPrefs.rightPanelMode();
        leftCollapsed = leftRegion.collapsed();
        rightCollapsed = rightRegion.collapsed();

        if (pendingTidyOnInit) {
            BlockSorter.tidy(script, font);
            pendingTidyOnInit = false;
        }

        relayout();
    }

    private int topH() { return topCollapsed ? ARROW : Toolbar.HEIGHT; }
    private int leftW() { return leftCollapsed ? ARROW : SIDE_W; }
    private int rightW() { return rightCollapsed ? ARROW : VAR_W; }

    private void relayout() {
        int top = topH();
        paletteX = 0;
        paletteY = top;
        paletteW = leftW();
        paletteH = height - top;
        if (!leftCollapsed) palette.setBounds(0, top + ARROW, SIDE_W, height - top - ARROW);
        if (!rightCollapsed) varPanel.setBounds(width - VAR_W, top + ARROW, VAR_W, height - top - ARROW);
        canvas.setBounds(leftW(), top, width - rightW(), height);
    }

    private boolean inLeftToggle(double mx, double my) {
        int top = topH();
        return leftCollapsed
                ? (mx >= 0 && mx < ARROW && my >= top && my < height)
                : (mx >= 0 && mx < SIDE_W && my >= top && my < top + ARROW);
    }

    private boolean inRightToggle(double mx, double my) {
        int top = topH();
        return rightCollapsed
                ? (mx >= width - ARROW && mx < width && my >= top && my < height)
                : (mx >= width - VAR_W && mx < width && my >= top && my < top + ARROW);
    }

    private boolean inTopToggle(double mx, double my) {
        return topCollapsed
                ? (mx >= 0 && mx < width && my >= 0 && my < ARROW)
                : (mx >= 0 && mx < ARROW && my >= 0 && my < Toolbar.HEIGHT);
    }

    private boolean handleChromeClick(double mx, double my, boolean shift) {
        if (inTopToggle(mx, my)) { topCollapsed = !topCollapsed; relayout(); return true; }
        if (inLeftToggle(mx, my)) {
            leftRegion.cycle(shift);
            EditorPrefs.setLeftPanelMode(leftRegion.mode);
            syncRegions();
            if (leftCollapsed) palette.unfocusSearch();
            return true;
        }
        if (inRightToggle(mx, my)) {
            rightRegion.cycle(shift);
            EditorPrefs.setRightPanelMode(rightRegion.mode);
            syncRegions();
            return true;
        }
        return false;
    }

    private void syncRegions() {
        boolean l = leftRegion.collapsed(), r = rightRegion.collapsed();
        if (l != leftCollapsed || r != rightCollapsed) {
            leftCollapsed = l;
            rightCollapsed = r;
            relayout();
        }
    }

    private void updateRegions(int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        boolean dragging = paletteDragType != null
                || canvas.draggingBlockId() >= 0 || canvas.isDraggingComments()
                || canvas.isDraggingBlackHole();
        updatePeek(leftRegion, inLeftFootprint(mouseX, mouseY), mouseX, mouseY, dragging, now);
        updatePeek(rightRegion, inRightFootprint(mouseX, mouseY), mouseX, mouseY, dragging, now);
        syncRegions();
    }

    private void updatePeek(Region r, boolean inFootprint, int mouseX, int mouseY, boolean dragging, long now) {
        if (r.mode != SidebarMode.HOVER) return;
        if (!r.peekOpen) {
            boolean overNothing = inFootprint && !dragging && !canvas.overBlockOrComment(mouseX, mouseY);
            if (overNothing) {
                if (r.openHoverStart == 0) r.openHoverStart = now;
                else if (now - r.openHoverStart >= PEEK_OPEN_MS) { r.peekOpen = true; r.closeStart = 0; }
            } else {
                r.openHoverStart = 0;
            }
        } else {
            if (inFootprint || dragging) {
                r.closeStart = 0;
            } else {
                if (r.closeStart == 0) r.closeStart = now;
                else if (now - r.closeStart >= PEEK_CLOSE_MS) {
                    r.peekOpen = false;
                    r.closeStart = 0;
                    r.openHoverStart = 0;
                }
            }
        }
    }

    private boolean inLeftFootprint(int mx, int my) {
        return mx >= 0 && mx < SIDE_W && my >= topH() && my < height;
    }

    private boolean inRightFootprint(int mx, int my) {
        return mx >= width - VAR_W && mx < width && my >= topH() && my < height;
    }

    private float regionTransition(Region r) {
        if (r.mode != SidebarMode.HOVER) return 0f;
        long now = System.currentTimeMillis();
        if (!r.peekOpen && r.openHoverStart != 0) {
            return clamp01((now - r.openHoverStart) / (float) PEEK_OPEN_MS);
        }
        if (r.peekOpen && r.closeStart != 0) {
            return clamp01((now - r.closeStart) / (float) PEEK_CLOSE_MS);
        }
        return 0f;
    }

    private static float clamp01(float t) {
        return t < 0f ? 0f : (Math.min(t, 1f));
    }

    private static int lerpColor(int from, int to, float t) {
        int a = ((from >>> 24) & 0xFF) + Math.round((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = ((from >>> 16) & 0xFF) + Math.round((((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = ((from >>> 8) & 0xFF) + Math.round((((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (from & 0xFF) + Math.round(((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void pushUndo() {
        positionsSorted = false;
        undoStack.push(AiScriptIO.toJson(script));
        while (undoStack.size() > HISTORY_CAP) undoStack.removeLast();
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        if (canvas != null) canvas.cancelCommentEdit();
        redoStack.push(AiScriptIO.toJson(script));
        script = AiScriptIO.fromJson(undoStack.pop(), script.name());
        select(-1);
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        if (canvas != null) canvas.cancelCommentEdit();
        undoStack.push(AiScriptIO.toJson(script));
        script = AiScriptIO.fromJson(redoStack.pop(), script.name());
        select(-1);
    }

    public boolean hasClipboard() {
        return clipboard != null;
    }

    public void copySelection() {
        if (selection.isEmpty()) {
            if (selectedId >= 0) clipboard = AiScriptIO.copyBlocks(script, Set.of(selectedId));
            return;
        }
        clipboard = AiScriptIO.copyBlocks(script, selection);
    }

    public void paste() {
        if (clipboard == null) return;
        pushUndo();
        List<Integer> ids = AiScriptIO.pasteBlocks(script, clipboard, 16, 16);
        normalizeDefineNames(ids);
        if (!ids.isEmpty()) selectMany(ids, false);
    }

    public void pasteAt(double worldX, double worldY) {
        if (clipboard == null) return;
        double[] mn = AiScriptIO.minCorner(clipboard);
        if (mn == null) return;
        pushUndo();
        List<Integer> ids = AiScriptIO.pasteBlocks(script, clipboard, worldX - mn[0], worldY - mn[1]);
        normalizeDefineNames(ids);
        if (!ids.isEmpty()) selectMany(ids, false);
    }

    public void duplicateSelection() {
        copySelection();
        if (clipboard == null) return;
        double[] b = canvas.selectionWorldBounds();
        double dx = 16, dy = (b != null) ? (b[3] - b[1]) + 16 : 16;
        pushUndo();
        List<Integer> ids = AiScriptIO.pasteBlocks(script, clipboard, dx, dy);
        normalizeDefineNames(ids);
        if (!ids.isEmpty()) selectMany(ids, false);
    }

    public void addComment(double worldX, double worldY, int attachTo) {
        pushUndo();
        if (attachTo >= 0) {
            BlockInstance b = script.block(attachTo);
            Comment c = script.addComment(worldX, worldY, "");
            if (b != null) {
                double h = canvas.measureComment(c);
                c.setAttachedTo(b.id());
                c.setOffset(0, -h);
            }
            canvas.startEditingComment(c);
            return;
        }
        double[] bounds = canvas.selectionWorldBounds();
        double x = (bounds != null) ? bounds[0] : worldX;
        double y = (bounds != null) ? bounds[1] - 22 : worldY;
        Comment c = script.addComment(x, y, "");
        canvas.startEditingComment(c);
    }

    public void dropVariableBlock(String qualifiedName, double screenX, double screenY) {
        if (!canvas.inside(screenX, screenY)) return;
        double[] w = canvas.screenToWorld(screenX, screenY);
        Object[] tgt = canvas.slotTargetAt(w[0], w[1]);
        BlockInstance nb = createBlockAt(BlockType.READ_VAR, w[0], w[1]);
        nb.setParam("name", qualifiedName);
        if (tgt != null) {
            BlockInstance target = (BlockInstance) tgt[0];
            String slot = (String) tgt[1];
            if (canvas.canNest(target, slot, nb)) {
                script.blocks().remove(nb.id());
                target.setReporter(slot, nb);
                ternarySlotChanged(target, slot);
                select(target.id());
            }
        }
    }

    public void dropFunctionBlock(String qualifiedName, double screenX, double screenY) {
        if (!canvas.inside(screenX, screenY)) return;
        if (script.function(qualifiedName) == null) return;
        double[] w = canvas.screenToWorld(screenX, screenY);
        boolean defined = false;
        for (BlockInstance b : script.blocks().values()) {
            if (b.type() == BlockType.FUNC_DEFINE && qualifiedName.equals(EffectiveSlots.funcName(b))) {
                defined = true;
                break;
            }
        }
        BlockType type = defined ? BlockType.FUNC_CALL : BlockType.FUNC_DEFINE;
        BlockInstance nb = createBlockAt(type, w[0], w[1]);
        nb.setParam("name", qualifiedName);
        select(nb.id());
    }

    public void editComment(Comment c) {
        canvas.startEditingComment(c);
    }

    public void deleteComment(int id) {
        pushUndo();
        script.removeComment(id);
    }

    private List<String> variableNames() {
        List<String> names = new ArrayList<>();
        for (hero.bane.herobot.ai.VarDecl v : script.variables()) names.add(v.qualifiedName());
        return names;
    }

    public void newScript() {
        if (canvas != null) canvas.cancelCommentEdit();
        script = new AiScript("untitled");
        select(-1);
        loadDialogOpen = false;
    }

    public boolean hasScriptContent() {
        return !(script.blocks().isEmpty()
                && script.variables().isEmpty()
                && script.varFolders().isEmpty()
                && isUnnamed());
    }

    public void saveScript() {
        if (isUnnamed()) { saveScriptAs(); return; }
        ScriptTransfer.upload(script.name(), script, positionsSorted);
        toolbar.flashFile();
        autosaveTicks = 0;
        lastSavedJson = snapshotJson();
        setStatus("Saved '" + script.name() + "'", 20, 15);
    }

    public void saveScriptAs() {
        promptText("Save script as", isUnnamed() ? "" : script.name(), name -> {
            if (name == null || name.isBlank()) return;
            String cleaned = name.trim();
            if (cleaned.equalsIgnoreCase("untitled")) {
                setStatus("'untitled' is reserved - pick another name");
                return;
            }
            script.setName(cleaned);
            ScriptTransfer.upload(script.name(), script, positionsSorted);
            toolbar.flashFile();
            autosaveTicks = 0;
            lastSavedJson = snapshotJson();
            setStatus("Saved as '" + script.name() + "'", 25, 20);
        });
    }

    private void autosave() {
        String json = snapshotJson();
        if (json != null && json.equals(lastSavedJson)) return;
        lastSavedJson = json;
        if (isUnnamed()) {
            persistDraft();
            setStatus("Autosaved draft", 80, 20);
            return;
        }
        persistDraft();
        ScriptTransfer.upload(script.name(), script, positionsSorted);
        toolbar.flashFile();
        setStatus("Autosaved '" + script.name() + "'", 80, 20);
    }

    public void copyJson() {
        try {
            String json = AiScriptIO.toJson(script);
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            setStatus("Copied AI JSON to clipboard (" + json.length() + " chars)");
        } catch (RuntimeException e) {
            setStatus("Failed to copy JSON: " + e.getMessage());
        }
    }

    public void pasteJson() {
        String json = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (json.isBlank()) { setStatus("Clipboard is empty"); return; }
        AiScript decoded;
        try {
            decoded = AiScriptIO.fromJson(json, script.name());
        } catch (RuntimeException e) {
            setStatus("Clipboard is not valid AI JSON: " + e.getMessage());
            return;
        }
        if (canvas != null) canvas.cancelCommentEdit();
        pushUndo();
        this.script = decoded;
        select(-1);
        setStatus("Loaded AI script from JSON");
    }

    public void loadDialog() {
        ScriptTransfer.requestList();
        loadDialogOpen = true;
        importMode = false;
        deleteMode = false;
    }

    public void importDialog() {
        ScriptTransfer.requestList();
        loadDialogOpen = true;
        importMode = true;
        deleteMode = false;
    }

    public void deleteDialog() {
        ScriptTransfer.requestList();
        loadDialogOpen = true;
        importMode = false;
        deleteMode = true;
    }

    private void closeLoadDialog() {
        loadDialogOpen = false;
        importMode = false;
        deleteMode = false;
        pendingImport = false;
    }

    public void openSettings() {
        closeLoadDialog();
        toolbar.closeMenu();
        settingsOpen = true;
    }

    public void openShortcuts() {
        closeLoadDialog();
        toolbar.closeMenu();
        settingsOpen = false;
        shortcutsOpen = true;
    }

    public void openTutorial() {
        ConfirmLinkScreen.confirmLinkNow(this, TUTORIAL_URL);
    }

    private int settingsRowY(int y) { return y + 30; }
    private int settingsRecordTreeRowY(int y) { return y + 50; }
    private int settingsAutosaveRowY(int y) { return y + 70; }

    private void renderSettings(GuiGraphics g, int mouseX, int mouseY) {
        int w = 180, h = 104;
        int x = (width - w) / 2, y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(x, y, x + w, y + h, 0xFF1E1E1E);
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF);
        g.drawString(font, "Settings", x + 8, y + 6, 0xFFFFFFFF, false);

        int rowY = settingsRowY(y);
        if (mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= rowY - 2 && mouseY < rowY + 12) {
            g.fill(x + 6, rowY - 2, x + w - 6, rowY + 12, 0x66FFFFFF);
        }

        int cbX = x + 12, cbSize = 10;
        g.fill(cbX, rowY, cbX + cbSize, rowY + cbSize, 0xFF555555);
        g.fill(cbX + 1, rowY + 1, cbX + cbSize - 1, rowY + cbSize - 1, 0xFF1E1E1E);
        if (EditorPrefs.cometsEnabled()) {
            g.fill(cbX + 2, rowY + 2, cbX + cbSize - 2, rowY + cbSize - 2, 0xFF55D07A);
        }
        g.drawString(font, "Comets naturally spawn", cbX + cbSize + 6, rowY + 1, 0xFFE0E0E0, false);

        int autosaveY = settingsAutosaveRowY(y);
        if (mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= autosaveY - 2 && mouseY < autosaveY + 12) {
            g.fill(x + 6, autosaveY - 2, x + w - 6, autosaveY + 12, 0x66FFFFFF);
        }
        int seconds = EditorPrefs.autosaveSeconds();
        g.drawString(font, "Autosave", x + 12, autosaveY + 1, 0xFFCFC030, false);
        String autosaveRest = seconds == 0 ? ": off" : ": " + seconds + " s";
        g.drawString(font, autosaveRest, x + 12 + font.width("Autosave"), autosaveY + 1, 0xFFE0E0E0, false);

        int recordY = settingsRecordTreeRowY(y);
        if (mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= recordY - 2 && mouseY < recordY + 12) {
            g.fill(x + 6, recordY - 2, x + w - 6, recordY + 12, 0x66FFFFFF);
        }
        g.fill(cbX, recordY, cbX + cbSize, recordY + cbSize, 0xFF555555);
        g.fill(cbX + 1, recordY + 1, cbX + cbSize - 1, recordY + cbSize - 1, 0xFF1E1E1E);
        if (EditorPrefs.recordSingleTree()) {
            g.fill(cbX + 2, recordY + 2, cbX + cbSize - 2, recordY + cbSize - 2, 0xFF55D07A);
        }
        g.drawString(font, "Record", cbX + cbSize + 6, recordY + 1, 0xFFD84C4C, false);
        g.drawString(font, " as one tree", cbX + cbSize + 6 + font.width("Record"), recordY + 1, 0xFFE0E0E0, false);

        g.drawString(font, "Esc to close", x + 8, y + h - 12, 0xFF909090, false);
    }

    @SuppressWarnings("SameReturnValue")
    private boolean settingsClick(double mx, double my) {
        int w = 180, h = 104;
        int x = (width - w) / 2, y = (height - h) / 2;
        int rowY = settingsRowY(y);
        if (mx >= x + 6 && mx <= x + w - 6 && my >= rowY - 2 && my < rowY + 12) {
            EditorPrefs.setCometsEnabled(!EditorPrefs.cometsEnabled());
            return true;
        }
        int autosaveY = settingsAutosaveRowY(y);
        if (mx >= x + 6 && mx <= x + w - 6 && my >= autosaveY - 2 && my < autosaveY + 12) {
            promptText("Autosave seconds (0 = off, add m for minutes)", String.valueOf(EditorPrefs.autosaveSeconds()),
                    value -> EditorPrefs.setAutosaveSeconds(parseAutosave(value, EditorPrefs.autosaveSeconds())));
            return true;
        }
        int recordY = settingsRecordTreeRowY(y);
        if (mx >= x + 6 && mx <= x + w - 6 && my >= recordY - 2 && my < recordY + 12) {
            EditorPrefs.setRecordSingleTree(!EditorPrefs.recordSingleTree());
            return true;
        }
        if (mx < x || mx > x + w || my < y || my > y + h) settingsOpen = false;
        return true;
    }

    private static final String[][] shortcutLegend = {
            {"", "Keyboard"},
            {"Ctrl+S", "Save"},
            {"Ctrl+D", "Sort"},
            {"Ctrl+Z", "Undo"},
            {"Ctrl+Y", "Redo"},
            {"Ctrl+C", "Copy"},
            {"Ctrl+V", "Paste"},
            {"Ctrl+A", "Select all [connected]"},
            {"Del/Backspace", "Delete selection"},
            {"Shift+Del", "Smart delete [bridge connections]"},
            {"Esc", "Close/cancel"},
            {"", "Mouse"},
            {"Left Click", "Select block/comment, drag to move"},
            {"Shift+Left or Middle Drag", "Move the canvas"},
            {"Left Drag", "Box-select [marquee] multiple blocks"},
            {"Ctrl+Click", "Add/remove from selection"},
            {"Double-Click comment", "Edit comment text"},
            {"Left Drag port", "Connect two ports together"},
            {"Double-Click port/block", "Auto-connect that port/all of that block's ports to the nearest valid one"},
            {"Sidebar Toggle", "Expand/Collapse sidebar"},
            {"Shift+Sidebar Toggle", "Switch to showing sidebar only on hover"},
            {"Right Click", "Open context [right click] menu"},
            {"Shift+Right Click", "Randomly spot a comet"},
            {"Shift+Right Drag", "Witness a meteor shower"},
            {"Scroll", "Zoom"},
    };

    private static final int[] SHORTCUT_RAINBOW = {
            0xFF5599FF, 0xFF7A66FF, 0xFFCC66FF, 0xFFFF5555, 0xFFFFAA33, 0xFFFFFF55, 0xFF55FF55,
    };

    private void renderShortcuts(GuiGraphics g) {
        int keyW = 0, descW = 0;
        for (String[] row : shortcutLegend) {
            keyW = Math.max(keyW, font.width(row[0]));
            descW = Math.max(descW, font.width(row[1]));
        }
        int w = Math.max(180, 12 + keyW + 12 + descW + 12);
        int h = 30 + shortcutLegend.length * 12 + 18;
        int x = (width - w) / 2;
        int y = Math.clamp((height - h) / 2, 4, height - h - 4);
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(x, y, x + w, y + h, 0xFF1E1E1E);
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF);
        g.drawString(font, "Shortcuts", x + 8, y + 6, 0xFFFFFFFF, false);

        int keyRow = 0;
        for (int i = 0; i < shortcutLegend.length; i++) {
            int rowY = y + 24 + i * 12;
            if (shortcutLegend[i][0].isEmpty()) {
                int tw = font.width(shortcutLegend[i][1]);
                g.drawString(font, shortcutLegend[i][1], x + (w - tw) / 2, rowY, 0xFFCFC030, false);
            } else {
                int keyColor = SHORTCUT_RAINBOW[keyRow++ % SHORTCUT_RAINBOW.length];
                g.drawString(font, shortcutLegend[i][0], x + 12, rowY, keyColor, false);
                g.drawString(font, shortcutLegend[i][1], x + 12 + keyW + 12, rowY, 0xFFE0E0E0, false);
            }
        }

        g.drawString(font, "Esc to close", x + 8, y + h - 12, 0xFF909090, false);
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void runOnTarget() {
        promptText("Run on (target selector / bot name)", runTarget, target -> {
            if (target == null || target.isBlank()) return;
            runTarget = target.trim();
            if (isUnnamed()) { saveScriptAs(); return; }
            ScriptTransfer.upload(script.name(), script, positionsSorted);
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.sendCommand("player " + runTarget + " ai set " + script.name());
                connection.sendCommand("player " + runTarget + " ai run");
            }
        });
    }

    public void sortBlocks() {
        BlockSorter.tidy(script, font);
        positionsSorted = true;
        select(-1);
        canvas.fitView();
        toolbar.flashSort();
    }

    public void startRecording() {
        hero.bane.herobot.client.record.MovementRecorder.INSTANCE.start(this);
    }

    public void finishRecording(List<hero.bane.herobot.client.record.MovementRecorder.Frame> frames,
                                List<hero.bane.herobot.client.record.MovementRecorder.InvAction> invActions,
                                double px, double py, double pz) {
        hero.bane.herobot.client.record.RecordingAssembler.assemble(script, frames, invActions);
        sortBlocks();
        addRecordingTeleportNote(px, py, pz);
    }

    private void addRecordingTeleportNote(double px, double py, double pz) {
        String cmd = "execute align xyz run teleport @s ~" + frac(px) + " ~" + frac(py) + " ~" + frac(pz);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        for (BlockInstance b : script.blocks().values()) {
            minX = Math.min(minX, b.x());
            minY = Math.min(minY, b.y());
        }
        if (minX == Double.POSITIVE_INFINITY) { minX = 0; minY = 0; }
        script.addComment(minX, minY - 30, cmd);
    }

    private static String frac(double v) {
        return String.format(Locale.ROOT, "%.5f", v - Math.floor(v));
    }

    private boolean isUnnamed() {
        return script.name() == null || script.name().isBlank() || script.name().equalsIgnoreCase("untitled");
    }

    public BlockInstance createBlockAt(BlockType type, double worldX, double worldY) {
        pushUndo();
        BlockInstance b = script.addBlock(type, worldX, worldY);
        for (ParamSlot slot : EffectiveSlots.initialSlots(type)) {
            b.setParam(slot.name(), slot.defaultValue());
        }
        if (type == BlockType.SET_VAR || type == BlockType.CHANGE_VAR || type == BlockType.READ_VAR) {
            List<String> names = variableNames();
            if (!names.isEmpty()) b.setParam("name", names.getFirst());
        }
        if (type == BlockType.FUNC_DEFINE) {
            b.setParam("name", freeFunctionName(b.id()));
        } else if (type == BlockType.FUNC_CALL) {
            List<String> names = EffectiveSlots.functionNames(script);
            if (!names.isEmpty()) b.setParam("name", names.getFirst());
        }
        if (isContainer(type)) {
            BlockInstance end = script.addBlock(BlockType.BLOCK_END, worldX, worldY + 80);
            b.setPairedId(end.id());
            end.setPairedId(b.id());
        }
        select(b.id());
        return b;
    }

    public static boolean isContainer(BlockType type) {
        return type == BlockType.IF || type == BlockType.ELSE_IF
                || type == BlockType.FOR || type == BlockType.WHILE;
    }

    public void spawnElseIf(BlockInstance source) {
        if (source.type() != BlockType.IF && source.type() != BlockType.ELSE_IF) return;
        Integer oldTarget = null;
        for (Wire w : script.wires()) {
            if (w.fromBlockId() == source.id() && w.outPort() == 1) { oldTarget = w.toBlockId(); break; }
        }
        BlockRenderer.Layout l = BlockRenderer.layout(BlockDefRegistry.get(source.type()), source, font, script);
        BlockInstance elif = createBlockAt(BlockType.ELSE_IF, source.x() + l.w + 60, source.y());
        script.wires().removeIf(w -> w.fromBlockId() == source.id() && w.outPort() == 1);
        script.addWire(source.id(), 1, elif.id(), 0);
        if (oldTarget != null) script.addWire(elif.id(), 1, oldTarget, 0);
    }

    public void toggleLookExpand(BlockInstance b) {
        if (!EffectiveSlots.isLookBlock(b.type())) return;
        pushUndo();
        if (EffectiveSlots.isLookExpanded(b)) {
            b.params().remove("expanded");
            int step = 0;
            for (String slot : EffectiveSlots.lookExtraSlots()) {
                b.params().remove(slot);
                BlockInstance child = b.reporterParams().remove(slot);
                if (child == null) continue;
                step += 20;
                child.setPos(b.x() + step, b.y() + step);
                script.putBlock(child);
            }
        } else {
            b.setParam("expanded", true);
        }
    }

    public void toggleLoopIter(BlockInstance b) {
        if (!EffectiveSlots.isLoopBlock(b.type())) return;
        pushUndo();
        if (Boolean.TRUE.equals(b.getParam("iterShown"))) {
            b.params().remove("iterShown");
        } else {
            b.setParam("iterShown", true);
        }
    }

    public BlockInstance spawnLoopIterator(BlockInstance loop, double worldX, double worldY) {
        if (!EffectiveSlots.isLoopBlock(loop.type())) return null;
        pushUndo();
        loop.setParam("iterShown", true);
        BlockInstance iter = script.addBlock(BlockType.LOOP_ITER, worldX, worldY);
        iter.setPairedId(loop.id());
        return iter;
    }

    public BlockInstance spawnMessageRef(BlockInstance hat, double worldX, double worldY) {
        if (hat.type() != BlockType.ON_MESSAGE) return null;
        pushUndo();
        BlockInstance ref = script.addBlock(BlockType.MSG_TEXT, worldX, worldY);
        ref.setPairedId(hat.id());
        return ref;
    }

    public void cycleVarBlock(BlockInstance b) {
        BlockType next = switch (b.type()) {
            case SET_VAR -> BlockType.CHANGE_VAR;
            case CHANGE_VAR -> BlockType.READ_VAR;
            case READ_VAR -> BlockType.SET_VAR;
            default -> null;
        };
        if (next == null) return;
        pushUndo();
        replaceVarBlock(b, next);
    }

    private void replaceVarBlock(BlockInstance old, BlockType next) {
        boolean topLevel = script.blocks().containsKey(old.id());
        ejectReporters(old);
        double x = old.x(), y = old.y();
        if (!topLevel) {
            BlockInstance parent = detachFromParent(old);
            if (parent != null) { x = parent.x() + 24; y = parent.y() + 40; }
        }
        BlockInstance nb = new BlockInstance(old.id(), next, x, y);
        Object name = old.getParam("name");
        if (name != null) nb.setParam("name", name);
        for (ParamSlot s : EffectiveSlots.forBlock(nb, script)) {
            if (!s.name().equals("name")) nb.setParam(s.name(), s.defaultValue());
        }
        script.blocks().put(nb.id(), nb);
        if (BlockDefRegistry.get(next).isReporter()) {
            script.wires().removeIf(w -> w.fromBlockId() == nb.id() || w.toBlockId() == nb.id());
        }
        select(nb.id());
    }

    private void ejectReporters(BlockInstance b) {
        int step = 0;
        for (BlockInstance child : new ArrayList<>(b.reporterParams().values())) {
            step += 24;
            child.setPos(b.x() + step, b.y() + step);
            script.putBlock(child);
        }
        b.reporterParams().clear();
    }

    private BlockInstance detachFromParent(BlockInstance target) {
        for (BlockInstance top : new ArrayList<>(script.blocks().values())) {
            BlockInstance parent = detachFrom(top, target);
            if (parent != null) return parent;
        }
        return null;
    }

    private BlockInstance detachFrom(BlockInstance host, BlockInstance target) {
        for (String slot : new ArrayList<>(host.reporterParams().keySet())) {
            BlockInstance child = host.getReporter(slot);
            if (child == target) {
                host.reporterParams().remove(slot);
                return host;
            }
            if (child != null) {
                BlockInstance p = detachFrom(child, target);
                if (p != null) return p;
            }
        }
        return null;
    }

    public void addCalcInput(BlockInstance b) {
        if (!EffectiveSlots.isCalcBlock(b.type())) return;
        int n = EffectiveSlots.calcInputCount(b);
        if (n >= EffectiveSlots.MAX_CALC_INPUTS) return;
        pushUndo();
        b.setParam("inputs", n + 1);
    }

    public void removeCalcInput(BlockInstance b) {
        if (!EffectiveSlots.isCalcBlock(b.type())) return;
        int n = EffectiveSlots.calcInputCount(b);
        if (n <= 0) return;
        pushUndo();
        b.setParam("inputs", n - 1);
        b.params().remove("Input" + n);
        popHiddenReporters(b);
    }

    public void toggleSensorTarget(BlockInstance b) {
        if (!EffectiveSlots.sensorTakesTarget(b.type())) return;
        pushUndo();
        String subject = EffectiveSlots.sensorSubjectSlot(b.type());
        if (EffectiveSlots.isSensorTargetShown(b)) {
            b.params().remove("targetOther");
            b.params().remove(subject);
            BlockInstance child = b.reporterParams().remove(subject);
            if (child != null) {
                child.setPos(b.x() + 20, b.y() + 20);
                script.putBlock(child);
            }
        } else {
            b.setParam("targetOther", true);
        }
    }

    public void toggleSendOp(BlockInstance b) {
        if (!EffectiveSlots.sendTakesOp(b.type())) return;
        pushUndo();
        if (EffectiveSlots.isOpShown(b)) {
            b.params().remove("op");
            BlockInstance child = b.reporterParams().remove("op");
            if (child != null) {
                child.setPos(b.x() + 20, b.y() + 20);
                script.putBlock(child);
            }
        } else {
            b.setParam("op", false);
        }
    }

    public void ternarySlotChanged(BlockInstance target, String slot) {
        if (target == null || target.type() != BlockType.TERNARY) return;
        if (!EffectiveSlots.isTernaryValueSlot(slot)) return;
        String other = "trueValue".equals(slot) ? "falseValue" : "trueValue";
        if (target.getReporter(other) == null) target.params().remove(other);
        if (target.getReporter(slot) == null) target.params().remove(slot);
    }

    public void editParam(BlockInstance b, String paramName) {
        ParamSlot slot = null;
        for (ParamSlot s : EffectiveSlots.forBlock(b, script)) if (s.name().equals(paramName)) { slot = s; break; }
        if (slot == null) return;
        Object cur = b.getParam(paramName);
        if (cur == null) cur = slot.defaultValue();

        if (EffectiveSlots.isCalcBlock(b.type()) && paramName.equals("expression")) {
            String initial = BlockRenderer.chipText(cur);
            openCalcEditor(b, paramName, initial);
            return;
        }
        if (slot.type() == ParamType.UUID) {
            final String pn = paramName;
            String initial = BlockRenderer.chipText(cur);
            selectorEditor.open(width, height, "Edit selector", initial, true,
                    value -> { pushUndo(); b.setParam(pn, value); });
            return;
        }
        if (slot.type() == ParamType.VAR_REF) {
            cycleVarRef(b, paramName, String.valueOf(cur));
            return;
        }
        if (slot.type() == ParamType.BOOLEAN) {
            if (b.type() == BlockType.ELSE_IF && paramName.equals("condition")) {
                if (!Boolean.TRUE.equals(cur)) {
                    pushUndo();
                    b.setParam(paramName, true);
                }
                return;
            }
            pushUndo();
            boolean v = cur instanceof Boolean bb ? bb : Boolean.TRUE.equals(slot.defaultValue());
            b.setParam(paramName, !v);
            return;
        }
        if (slot.type() == ParamType.ENUM && !slot.enumChoices().isEmpty()) {
            pushUndo();
            List<String> choices = slot.enumChoices();
            int idx = Math.max(0, choices.indexOf(String.valueOf(cur)));
            b.setParam(paramName, choices.get((idx + 1) % choices.size()));
            popHiddenReporters(b);
            return;
        }
        final ParamType ptype = slot.type();
        String initial = BlockRenderer.chipText(cur);
        if (EffectiveSlots.isMinOneSlot(b.type(), paramName)) {
            paramEditor.open(width, height, "Edit " + paramName + " (min 1)", initial,
                    s -> parseIntOr(s.trim(), 0) >= 1,
                    value -> { pushUndo(); b.setParam(paramName, Math.max(1, parseIntOr(value.trim(), 1))); });
            return;
        }
        if (b.type() == BlockType.ITEM_IN_SLOT && paramName.equals("slot")) {
            paramEditor.open(width, height, "Edit " + paramName + " (0-35)", initial,
                    AiEditorScreen::isValidInventorySlot,
                    value -> { pushUndo(); b.setParam(paramName, coerce(ptype, value)); });
            return;
        }
        promptText("Edit " + paramName, initial, value -> { pushUndo(); b.setParam(paramName, coerce(ptype, value)); });
    }

    private void openCalcEditor(BlockInstance b, String paramName, String initial) {
        Consumer<String> commit = value -> { pushUndo(); b.setParam(paramName, value); };
        List<String> vars = variableNames();
        switch (b.type()) {
            case STRING_CALC -> exprEditor.open(width, height, "Edit string expression", initial, vars,
                    hero.bane.herobot.ai.expr.StrEval::isValid,
                    hero.bane.herobot.ai.expr.StrEval.OPS_LEGEND,
                    hero.bane.herobot.ai.expr.StrEval.OPS_LEGEND_2,
                    "Join \"text\" and {vars} with +, slice like python", commit);
            case BOOL_CALC -> exprEditor.open(width, height, "Edit boolean expression", initial, vars,
                    expr -> hero.bane.herobot.ai.expr.BoolEval.isValid(expr, calcOperandTypes(b)),
                    hero.bane.herobot.ai.expr.BoolEval.OPS_LEGEND,
                    hero.bane.herobot.ai.expr.BoolEval.OPS_LEGEND_2,
                    "Compared types must match; strings order by length", commit);
            case POS_CALC -> exprEditor.open(width, height, "Edit position expression", initial, vars,
                    expr -> hero.bane.herobot.ai.expr.VecEval.isValid(expr, 3, calcOperandTypes(b)),
                    hero.bane.herobot.ai.expr.VecEval.OPS_LEGEND,
                    hero.bane.herobot.ai.expr.VecEval.OPS_LEGEND_2,
                    "Math is per-component; climb/fall snap to terrain", commit);
            case DIR_CALC -> exprEditor.open(width, height, "Edit direction expression", initial, vars,
                    expr -> hero.bane.herobot.ai.expr.VecEval.isValid(expr, 2, calcOperandTypes(b)),
                    "dir(yaw,pitch)  +  -  *  /  %  ^",
                    "floor(dir)  abs  sin  {var}  Input1",
                    "Math is applied per-component", commit);
            default -> exprEditor.open(width, height, "Edit expression", initial, vars,
                    expr -> hero.bane.herobot.ai.expr.ExprEval.isValid(expr, calcOperandTypes(b)),
                    hero.bane.herobot.ai.expr.ExprEval.OPS_LEGEND,
                    hero.bane.herobot.ai.expr.ExprEval.OPS_LEGEND_2,
                    "Variables must be in braces, e.g. 5*{x}*3", commit);
        }
    }

    private java.util.function.Function<String, ParamType> calcOperandTypes(BlockInstance b) {
        return name -> {
            if (hero.bane.herobot.ai.expr.ExprEval.isInputRef(name)) {
                String canonical = hero.bane.herobot.ai.expr.ExprEval.canonicalInput(name);
                BlockInstance rep = b.getReporter(canonical);
                return rep == null ? null : EffectiveSlots.reporterOutputType(rep, script);
            }
            return EffectiveSlots.paramTypeOf(EffectiveSlots.varType(script, name));
        };
    }

    private static boolean isValidInventorySlot(String s) {
        try {
            double v = Double.parseDouble(s.trim());
            return v >= 0 && v <= 35 && v == Math.floor(v);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void setVarRef(BlockInstance b, String paramName, String varName) {
        pushUndo();
        b.setParam(paramName, varName);
        popHiddenReporters(b);
    }

    public void setEnumChoice(BlockInstance b, String paramName, String value) {
        pushUndo();
        b.setParam(paramName, value);
        popHiddenReporters(b);
    }

    private void cycleVarRef(BlockInstance b, String paramName, String cur) {
        List<String> names = variableNames();
        if (names.isEmpty()) return;
        int idx = names.indexOf(cur);
        setVarRef(b, paramName, names.get((idx + 1) % names.size()));
    }

    public void onVariableTypeChanged(String varName) {
        for (BlockInstance b : new ArrayList<>(script.blocks().values())) {
            if ((b.type() == BlockType.SET_VAR || b.type() == BlockType.CHANGE_VAR)
                    && varName.equals(varRefName(b))) {
                for (ParamSlot s : EffectiveSlots.forBlock(b, script)) {
                    if (!s.name().equals("name")) b.setParam(s.name(), s.defaultValue());
                }
            }
        }
        for (BlockInstance b : new ArrayList<>(script.blocks().values())) {
            refitReportersDeep(b);
        }
    }

    private String varRefName(BlockInstance b) {
        Object p = b.getParam("name");
        return p == null ? "" : p.toString();
    }

    private FuncDecl declFor(BlockInstance b) {
        return script.function(EffectiveSlots.funcName(b));
    }

    private String freeFunctionName(int exceptBlockId) {
        Set<String> taken = new HashSet<>();
        for (BlockInstance b : script.blocks().values()) {
            if (b.type() != BlockType.FUNC_DEFINE || b.id() == exceptBlockId) continue;
            String n = EffectiveSlots.funcName(b);
            if (!n.isEmpty()) taken.add(n);
        }
        for (FuncDecl f : script.functions()) {
            if (!taken.contains(f.qualifiedName())) return f.qualifiedName();
        }
        return "";
    }

    private boolean definedElsewhere(String name, int exceptId) {
        for (BlockInstance b : script.blocks().values()) {
            if (b.type() == BlockType.FUNC_DEFINE && b.id() != exceptId
                    && name.equals(EffectiveSlots.funcName(b))) return true;
        }
        return false;
    }

    public void normalizeAllDefineNames() {
        List<Integer> ids = new ArrayList<>();
        for (BlockInstance b : script.blocks().values()) {
            if (b.type() == BlockType.FUNC_DEFINE) ids.add(b.id());
        }
        normalizeDefineNames(ids);
    }

    public void normalizeDefineNames(List<Integer> ids) {
        for (int id : ids) {
            BlockInstance b = script.block(id);
            if (b == null || b.type() != BlockType.FUNC_DEFINE) continue;
            String n = EffectiveSlots.funcName(b);
            if (n.isEmpty() || definedElsewhere(n, b.id())) {
                b.setParam("name", freeFunctionName(b.id()));
            }
        }
    }

    public void addFuncParam(BlockInstance b) {
        FuncDecl decl = declFor(b);
        if (decl == null || decl.numParams() >= EffectiveSlots.MAX_FUNC_PARAMS) return;
        pushUndo();
        setFunction(decl, decl.withParamAdded(VarType.INT));
    }

    public void removeLastFuncParam(BlockInstance b) {
        FuncDecl decl = declFor(b);
        if (decl == null || decl.numParams() == 0) return;
        pushUndo();
        int last = decl.numParams() - 1;
        setFunction(decl, decl.withParamRemoved(last));
        dropFuncParamRefs(decl.qualifiedName(), last);
        dropArgValues(decl.qualifiedName(), last);
        refitAllReporters();
    }

    private void dropFuncParamRefs(String func, int fromIndex) {
        List<Integer> gone = new ArrayList<>();
        for (BlockInstance b : script.blocks().values()) {
            if (isStaleParamRef(b, func, fromIndex)) gone.add(b.id());
            else pruneStaleParamRefs(b, func, fromIndex);
        }
        for (int id : gone) deleteBlock(id);
    }

    private boolean isStaleParamRef(BlockInstance b, String func, int fromIndex) {
        if (b == null || b.type() != BlockType.FUNC_PARAM || !func.equals(b.getParam("func"))) return false;
        Object idx = b.getParam("index");
        return idx instanceof Number n && n.intValue() >= fromIndex;
    }

    private void pruneStaleParamRefs(BlockInstance host, String func, int fromIndex) {
        host.reporterParams().entrySet().removeIf(e -> isStaleParamRef(e.getValue(), func, fromIndex));
        for (BlockInstance child : host.reporterParams().values()) {
            if (child != null) pruneStaleParamRefs(child, func, fromIndex);
        }
    }

    private void dropOwnedRefs(int ownerId) {
        List<Integer> gone = new ArrayList<>();
        for (BlockInstance b : script.blocks().values()) {
            if (b.type().refsOwner() && b.pairedId() == ownerId) gone.add(b.id());
            else pruneNestedOwnedRefs(b, ownerId);
        }
        for (int id : gone) deleteBlock(id);
    }

    private void pruneNestedOwnedRefs(BlockInstance host, int ownerId) {
        host.reporterParams().entrySet().removeIf(e -> {
            BlockInstance child = e.getValue();
            return child != null && child.type().refsOwner() && child.pairedId() == ownerId;
        });
        for (BlockInstance child : host.reporterParams().values()) {
            if (child != null) pruneNestedOwnedRefs(child, ownerId);
        }
    }

    public void setFuncParamType(BlockInstance b, int index, VarType type) {
        FuncDecl decl = declFor(b);
        if (decl == null || decl.paramType(index) == type) return;
        pushUndo();
        setFunction(decl, decl.withParamType(index, type));
        refitAllReporters();
    }

    private void setFunction(FuncDecl oldDecl, FuncDecl next) {
        int i = script.functionIndex(oldDecl.qualifiedName());
        if (i >= 0) script.functions().set(i, next);
    }

    private void dropArgValues(String func, int newArity) {
        for (BlockInstance b : script.blocks().values()) {
            if (b.type() != BlockType.FUNC_CALL || !func.equals(b.getParam("name"))) continue;
            for (int i = newArity; i < EffectiveSlots.MAX_FUNC_PARAMS; i++) {
                b.params().remove("Arg" + (i + 1));
            }
        }
    }

    private void refitAllReporters() {
        for (BlockInstance b : new ArrayList<>(script.blocks().values())) refitReportersDeep(b);
    }

    public BlockInstance spawnFuncParam(BlockInstance define, int index, double worldX, double worldY) {
        FuncDecl decl = declFor(define);
        if (decl == null || index < 0 || index >= decl.numParams()) return null;
        pushUndo();
        BlockInstance nb = createBlockAt(BlockType.FUNC_PARAM, worldX, worldY);
        nb.setParam("func", decl.qualifiedName());
        nb.setParam("index", index);
        nb.setPairedId(define.id());
        return nb;
    }

    public void refactorFunctionRef(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        for (BlockInstance b : script.blocks().values()) refactorFuncRefDeep(b, oldName, newName);
    }

    private void refactorFuncRefDeep(BlockInstance b, String oldName, String newName) {
        if (b.type() == BlockType.FUNC_DEFINE || b.type() == BlockType.FUNC_CALL) {
            if (oldName.equals(b.getParam("name"))) b.setParam("name", newName);
        } else if (b.type() == BlockType.FUNC_PARAM) {
            if (oldName.equals(b.getParam("func"))) b.setParam("func", newName);
        }
        for (BlockInstance child : b.reporterParams().values()) refactorFuncRefDeep(child, oldName, newName);
    }

    public void refactorVariableRef(String oldQ, String newQ) {
        if (oldQ == null || newQ == null || oldQ.equals(newQ)) return;
        for (BlockInstance b : script.blocks().values()) refactorRefDeep(b, oldQ, newQ);
    }

    private void refactorRefDeep(BlockInstance b, String oldQ, String newQ) {
        if (b.type() == BlockType.SET_VAR || b.type() == BlockType.CHANGE_VAR || b.type() == BlockType.READ_VAR) {
            if (oldQ.equals(varRefName(b))) b.setParam("name", newQ);
        } else if (EffectiveSlots.isCalcBlock(b.type())) {
            Object e = b.getParam("expression");
            if (e instanceof String s) {
                String replaced = s.replace("{" + oldQ + "}", "{" + newQ + "}");
                if (!replaced.equals(s)) b.setParam("expression", replaced);
            }
        }
        for (BlockInstance child : b.reporterParams().values()) refactorRefDeep(child, oldQ, newQ);
    }

    private void refitReportersDeep(BlockInstance b) {
        List<BlockInstance> children = new ArrayList<>(b.reporterParams().values());
        popHiddenReporters(b);
        for (BlockInstance child : children) refitReportersDeep(child);
    }

    private void popHiddenReporters(BlockInstance b) {
        Set<String> visible = new HashSet<>();
        java.util.Map<String, ParamType> types = new java.util.HashMap<>();
        for (ParamSlot s : BlockRenderer.visibleSlots(b, script)) {
            visible.add(s.name());
            types.put(s.name(), s.type());
        }
        List<String> hidden = new ArrayList<>();
        for (String slot : b.reporterParams().keySet()) {
            if (!visible.contains(slot)) { hidden.add(slot); continue; }
            ParamType st = types.get(slot);
            BlockInstance rep = b.getReporter(slot);
            if (st != null && rep != null
                    && !EffectiveSlots.accepts(b.type(), slot, st, EffectiveSlots.reporterOutputType(rep, script))) {
                hidden.add(slot);
            }
        }
        int step = 0;
        for (String slot : hidden) {
            BlockInstance child = b.reporterParams().remove(slot);
            if (child == null) continue;
            step += 20;
            child.setPos(b.x() + step, b.y() + step);
            script.putBlock(child);
        }
    }

    private Object coerce(ParamType type, String value) {
        try {
            return switch (type) {
                case INT -> (int) Math.round(Double.parseDouble(value.trim()));
                case DOUBLE -> Double.parseDouble(value.trim());
                case BOOLEAN -> Boolean.parseBoolean(value.trim());
                default -> value;
            };
        } catch (NumberFormatException e) {
            return type == ParamType.INT ? 0 : type == ParamType.DOUBLE ? 0.0 : value;
        }
    }

    public void promptText(String title, String initial, Consumer<String> onCommit) {
        paramEditor.open(width, height, title, initial, onCommit);
    }

    public void convertCommandAt(double worldX, double worldY) {
        paramEditor.open(width, height, "Convert command", "/player herosbot ",
                s -> CommandToBlock.parse(s) != null,
                s -> {
                    CommandToBlock.Result r = CommandToBlock.parse(s);
                    if (r == null) return;
                    BlockInstance b = createBlockAt(r.type(), worldX, worldY);
                    r.params().forEach(b::setParam);
                },
                "Paste clipboard as blocks",
                () -> convertClipboardAt(worldX, worldY));
    }

    private void convertClipboardAt(double worldX, double worldY) {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip.isBlank()) { setStatus("Clipboard is empty"); return; }

        pushUndo();
        List<Integer> created = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        double y = worldY;
        int prevId = -1;
        BlockInstance lastBlock = null;

        for (String raw : clip.split("\\R", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                pending.add(line.substring(1).strip());
                continue;
            }
            BlockInstance b = spawnCommandBlock(line, worldX, y);
            created.add(b.id());
            if (prevId >= 0) script.addWire(prevId, 0, b.id());
            attachPendingComments(pending, b);
            pending.clear();
            prevId = b.id();
            lastBlock = b;
            y += blockHeight(b) + 8;
        }

        double cx = lastBlock != null ? lastBlock.x() : worldX;
        double cy = lastBlock != null ? lastBlock.y() + blockHeight(lastBlock) + 8 : y;
        for (String text : pending) {
            Comment c = script.addComment(cx, cy, text);
            cy += canvas.measureComment(c) + 4;
        }

        if (!created.isEmpty()) selectMany(created, false);
        setStatus("Converted " + created.size() + " block" + (created.size() == 1 ? "" : "s") + " from clipboard");
    }

    private BlockInstance spawnCommandBlock(String line, double x, double y) {
        CommandToBlock.Result r = CommandToBlock.parse(line);
        BlockType type = r != null ? r.type() : BlockType.SEND_MESSAGE;
        BlockInstance b = script.addBlock(type, x, y);
        for (ParamSlot slot : EffectiveSlots.initialSlots(type)) {
            b.setParam(slot.name(), slot.defaultValue());
        }
        if (r != null) {
            r.params().forEach(b::setParam);
        } else {
            b.setParam("message", line.startsWith("/") ? line : "/" + line);
        }
        return b;
    }

    private void attachPendingComments(List<String> texts, BlockInstance b) {
        double offY = 0;
        for (int i = texts.size() - 1; i >= 0; i--) {
            Comment c = script.addComment(b.x(), b.y(), texts.get(i));
            offY -= canvas.measureComment(c);
            c.setAttachedTo(b.id());
            c.setOffset(0, offY);
            offY -= 2;
        }
    }

    private double blockHeight(BlockInstance b) {
        return BlockRenderer.layout(BlockDefRegistry.get(b.type()), b, font, script).h;
    }

    public void deleteSelected() {
        if (selection.isEmpty() && selectedId < 0) return;
        pushUndo();
        if (selection.isEmpty()) {
            if (selectedId >= 0) deleteEntity(selectedId);
            return;
        }
        for (int id : new ArrayList<>(selection)) deleteEntity(id);
        selection.clear();
        selectedId = -1;
    }

    public void deleteSelectedBridging() {
        if (selection.isEmpty() && selectedId < 0) return;
        pushUndo();
        List<Integer> ids = selection.isEmpty() ? List.of(selectedId) : new ArrayList<>(selection);
        for (int id : ids) {
            if (script.block(id) != null) bridgeWires(id);
            deleteEntity(id);
        }
        selection.clear();
        selectedId = -1;
    }

    private void bridgeWires(int id) {
        Wire in = null, out = null;
        int ins = 0, outs = 0;
        for (Wire w : script.wires()) {
            if (w.toBlockId() == id) { ins++; in = w; }
            if (w.fromBlockId() == id) { outs++; out = w; }
        }
        if (ins == 1 && outs == 1 && in.fromBlockId() != out.toBlockId()) {
            script.addWire(in.fromBlockId(), in.outPort(), out.toBlockId(), out.toPort());
        }
    }

    private void deleteEntity(int id) {
        if (script.block(id) != null) {
            deleteBlock(id);
        } else {
            script.removeComment(id);
            selection.remove(id);
            if (selectedId == id) selectedId = -1;
        }
    }

    public void deleteBlock(int id) {
        BlockInstance b = script.block(id);
        int paired = b != null && !b.type().refsOwner() ? b.pairedId() : -1;
        if (b != null) dropOwnedRefs(id);
        script.comments().removeIf(c -> c.attachedTo() == id);
        script.wires().removeIf(w -> w.fromBlockId() == id || w.toBlockId() == id);
        script.removeBlock(id);
        selection.remove(id);
        if (selectedId == id) selectedId = -1;
        if (paired >= 0 && script.block(paired) != null) {
            BlockInstance pb = script.block(paired);
            pb.setPairedId(-1);
            deleteBlock(paired);
        }
    }

    public void onScriptList(List<String> names) {
        this.serverScripts = new ArrayList<>(names);
    }

    public List<String> serverScriptNames() {
        return serverScripts;
    }

    public void onScriptDownloaded(String name, String json) {
        if (canvas != null) canvas.cancelCommentEdit();
        try {
            if (pendingImport) {
                AiScript foreign = AiScriptIO.fromJson(json, name);
                String clip = AiScriptIO.copyBlocks(foreign, new HashSet<>(foreign.blocks().keySet()));
                pushUndo();
                List<Integer> ids = AiScriptIO.pasteBlocks(script, clip, 0, 0);
                normalizeDefineNames(ids);
                selectMany(ids, false);
            } else {
                this.script = AiScriptIO.fromJson(json, name);
                positionsSorted = AiScriptIO.wasSorted(json);
                if (positionsSorted) BlockSorter.tidy(script, font);
                select(-1);
                lastSavedJson = snapshotJson();
            }
        } catch (Exception e) {
            openLoadFailedDialog(name);
            return;
        }
        closeLoadDialog();
    }

    public void onScriptDownloadFailed(String name) {
        openLoadFailedDialog(name);
    }

    private void openLoadFailedDialog(String name) {
        closeLoadDialog();
        shortcutsOpen = false;
        settingsOpen = false;
        loadFailedName = name;
    }

    @Override
    public void tick() {
        super.tick();
        if (canvas != null) canvas.tickStars();
        if (toolbar != null) toolbar.tick();
        if (statusTicks > 0 && --statusTicks == 0) statusMessage = null;
        int period = EditorPrefs.autosaveSeconds() * 20;
        if (period > 0 && ++autosaveTicks >= period) { autosaveTicks = 0; autosave(); }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateRegions(mouseX, mouseY);
        g.fill(0, 0, width, height, 0xFF0E0E12);
        canvas.render(g, mouseX, mouseY);

        if (leftCollapsed) {
            paletteHoverType = null;
        } else {
            paletteHoverType = palette.hovered(mouseX, mouseY);
            palette.render(g, mouseX, mouseY);
        }
        boolean draggingSomething = paletteDragType != null || canvas.draggingBlockId() >= 0
                || canvas.isDraggingComments() || canvas.isDraggingBlackHole();
        canvasHoverType = draggingSomething ? null : canvas.hoveredType(mouseX, mouseY);
        canvasWireTargetType = canvas.wireTargetType(mouseX, mouseY);

        if (!rightCollapsed) varPanel.render(g, mouseX, mouseY);
        if (!topCollapsed) {
            toolbar.render(g, width, mouseX, mouseY);
        }
        renderChrome(g, mouseX, mouseY);

        int chromeX = leftW() + 4;
        g.drawString(font, "https://discord.gg/4ta7pM4bnr",
                chromeX, height - 11, 0xFF707070, false);

        if (statusMessage != null) {
            float alpha = statusFadeTicks > 0 ? Math.clamp(statusTicks / (float) statusFadeTicks, 0f, 1f) : 1f;
            int color = (Math.round(alpha * 255) << 24) | 0x00B0FFB0;
            g.drawString(font, statusMessage, chromeX, height - 22, color, false);
        }

        String dropReason = canvas.dropReason();
        if (dropReason != null) {
            g.drawString(font, dropReason, chromeX, height - 33, 0xFFFF6060, false);
        }

        if (draggingSomething && overLeftPanel(mouseX, mouseY)) {
            g.fill(paletteX, paletteY, paletteX + paletteW, paletteY + paletteH, 0x66FF3030);
            if (!leftCollapsed) g.drawString(font, "Release to delete", paletteX + 6, paletteY + paletteH / 2, 0xFFFFFFFF, false);
        }
        if (draggingSomething && overRightPanel(mouseX, mouseY)) {
            int rx = width - rightW();
            g.fill(rx, topH(), width, height, 0x66FF3030);
            if (!rightCollapsed) g.drawString(font, "Release to delete", rx + 6, topH() + (height - topH()) / 2, 0xFFFFFFFF, false);
        }

        if (paletteDragType != null) renderPaletteGhost(g);
        if (loadDialogOpen) renderLoadDialog(g, mouseX, mouseY);
        if (settingsOpen) renderSettings(g, mouseX, mouseY);
        if (shortcutsOpen) renderShortcuts(g);
        if (loadFailedName != null) renderLoadFailedDialog(g);
        paramEditor.render(g, mouseX, mouseY, partialTick);
        exprEditor.render(g, mouseX, mouseY, partialTick);
        selectorEditor.render(g, mouseX, mouseY, partialTick);
    }

    private boolean overLeftPanel(double mx, double my) {
        return mx >= 0 && mx < leftW() && my >= topH() && my < height;
    }

    private boolean overRightPanel(double mx, double my) {
        return mx >= width - rightW() && mx < width && my >= topH() && my < height;
    }

    private void renderChrome(GuiGraphics g, int mouseX, int mouseY) {
        int top = topH();
        int panel = 0xFF1A1A1A;
        int border = 0xFF000000;

        int flash = 0xFF444444;

        boolean topHover = inTopToggle(mouseX, mouseY);
        int topCol = topHover ? 0xFFD0D0D0 : 0xFF808080;
        if (topCollapsed) {
            g.fill(0, 0, width, ARROW, 0xFF2A2A2A);
            g.fill(0, ARROW, width, ARROW + 1, border);
            g.drawString(font, "▾", 2, 1, topCol, false);
        } else {
            g.fill(0, 0, ARROW, Toolbar.HEIGHT, topHover ? 0xFF454545 : 0xFF2A2A2A);
            g.drawString(font, "▴", 2, 7, topCol, false);
        }

        int leftCol = inLeftToggle(mouseX, mouseY) ? 0xFFD0D0D0 : 0xFF808080;
        int leftBg = lerpColor(panel, flash, regionTransition(leftRegion));
        if (leftCollapsed) {
            g.fill(0, top, ARROW, height, leftBg);
            g.fill(ARROW, top, ARROW + 1, height, border);
            g.drawString(font, leftRegion.mode == SidebarMode.HOVER ? "\uD83D\uDC7B" : "▸", 1, top + 1, leftCol, false);
        } else {
            g.fill(0, top, SIDE_W, top + ARROW, leftBg);
            g.drawString(font, leftRegion.mode == SidebarMode.HOVER ? "\uD83D\uDC7B" : "◂", SIDE_W - ARROW + 1, top + 1, leftCol, false);
        }

        int rightCol = inRightToggle(mouseX, mouseY) ? 0xFFD0D0D0 : 0xFF808080;
        int rightBg = lerpColor(panel, flash, regionTransition(rightRegion));
        if (rightCollapsed) {
            g.fill(width - ARROW, top, width, height, rightBg);
            g.fill(width - ARROW - 1, top, width - ARROW, height, border);
            g.drawString(font, rightRegion.mode == SidebarMode.HOVER ? "\uD83D\uDC7B" : "◂", width - ARROW + 1, top + 1, rightCol, false);
        } else {
            g.fill(width - VAR_W, top, width, top + ARROW, rightBg);
            g.drawString(font, rightRegion.mode == SidebarMode.HOVER ? "\uD83D\uDC7B" : "▸", width - VAR_W + 2, top + 1, rightCol, false);
        }
    }

    private void renderPaletteGhost(GuiGraphics g) {
        BlockDef def = BlockDefRegistry.get(paletteDragType);
        String label = def.label();
        int w = font.width(label) + 12;
        int gx = (int) paletteDragX + 8, gy = (int) paletteDragY - 8;
        g.fill(gx - 1, gy - 1, gx + w + 1, gy + 20, 0xFFFFFFFF);
        g.fill(gx, gy, gx + w, gy + 19, (def.category().color() & 0x00FFFFFF) | 0xDD000000);
        g.fill(gx, gy, gx + w, gy + 9, (def.category().color() & 0x00FFFFFF) | 0xDD000000);
        g.drawString(font, label, gx + 6, gy + 6, 0xFFFFFFFF, false);
    }

    private void renderLoadDialog(GuiGraphics g, int mouseX, int mouseY) {
        int w = 220, h = 140;
        int x = (width - w) / 2, y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(x, y, x + w, y + h, 0xFF1E1E1E);
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF);
        String title = deleteMode ? "Delete script (Esc to cancel)"
                : importMode ? "Import blocks from (Esc to cancel)"
                : "Load script (Esc to cancel)";
        g.drawString(font, title, x + 8, y + 6, deleteMode ? 0xFFFF7070 : 0xFFFFFFFF, false);
        if (serverScripts.isEmpty()) {
            g.drawString(font, "No scripts on server", x + 8, y + 24, 0xFF909090, false);
        }
        for (int i = 0; i < serverScripts.size() && i < 9; i++) {
            int ry = y + 22 + i * 12;
            boolean hover = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= ry && mouseY < ry + 12;
            if (hover) g.fill(x + 6, ry, x + w - 6, ry + 12, 0x66FFFFFF);
            g.drawString(font, serverScripts.get(i), x + 10, ry + 2, 0xFFE0E0E0, false);
        }
    }

    @SuppressWarnings("SameReturnValue")
    private boolean loadDialogClick(double mx, double my) {
        int w = 220, h = 140;
        int x = (width - w) / 2, y = (height - h) / 2;
        for (int i = 0; i < serverScripts.size() && i < 9; i++) {
            int ry = y + 22 + i * 12;
            if (mx >= x + 6 && mx <= x + w - 6 && my >= ry && my < ry + 12) {
                String name = serverScripts.get(i);
                if (deleteMode) {
                    ScriptTransfer.requestDelete(name);
                    serverScripts.remove(i);
                    setStatus("Deleted script '" + name + "'");
                    if (serverScripts.isEmpty()) closeLoadDialog();
                    return true;
                }
                if (importMode) pendingImport = true;
                ScriptTransfer.requestDownload(name);
                return true;
            }
        }

        if (mx < x || mx > x + w || my < y || my > y + h) closeLoadDialog();
        return true;
    }

    private void renderLoadFailedDialog(GuiGraphics g) {
        String title = "Failed to load '" + loadFailedName + "'";
        String line = "The script may be outdated or corrupted, sorry";
        int w = Math.max(220, 16 + Math.max(font.width(title), font.width(line)));
        int h = 76;
        int x = (width - w) / 2, y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(x, y, x + w, y + h, 0xFF1E1E1E);
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF);
        g.drawString(font, title, x + 8, y + 6, 0xFFFF7070, false);
        g.drawString(font, line, x + 8, y + 20, 0xFFE0E0E0, false);
        g.drawString(font, "Delete", x + 8, y + h - 24, 0xFFFF7070, false);
        g.drawString(font, "Cancel", x + 8, y + h - 12, 0xFFE0E0E0, false);
    }

    @SuppressWarnings("SameReturnValue")
    private boolean loadFailedClick(double mx, double my) {
        String title = "Failed to load '" + loadFailedName + "'";
        String line = "The script may use an unsupported or outdated format.";
        int w = Math.max(220, 16 + Math.max(font.width(title), font.width(line)));
        int h = 76;
        int x = (width - w) / 2, y = (height - h) / 2;
        if (mx >= x + 6 && mx <= x + w - 6 && my >= y + h - 26 && my < y + h - 14) {
            ScriptTransfer.requestDelete(loadFailedName);
            serverScripts.remove(loadFailedName);
            setStatus("Deleted script '" + loadFailedName + "'");
            loadFailedName = null;
            return true;
        }
        if (mx >= x + 6 && mx <= x + w - 6 && my >= y + h - 14 && my < y + h - 2) {
            loadFailedName = null;
            return true;
        }
        if (mx < x || mx > x + w || my < y || my > y + h) loadFailedName = null;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (paramEditor.isActive()) return paramEditor.mouseClicked(event, doubled);
        if (exprEditor.isActive()) return exprEditor.mouseClicked(event, doubled);
        if (selectorEditor.isActive()) return selectorEditor.mouseClicked(event, doubled);
        if (loadFailedName != null) return loadFailedClick(mx, my);
        if (loadDialogOpen) return loadDialogClick(mx, my);
        if (settingsOpen) return settingsClick(mx, my);
        if (shortcutsOpen) { shortcutsOpen = false; return true; }

        if (handleChromeClick(mx, my, (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0)) return true;

        if (!topCollapsed && toolbar.mouseClicked(mx, my)) return true;
        if (!leftCollapsed) {
            if (palette.clickedSearch(event, doubled)) return true;
            BlockType t = palette.clicked(mx, my);
            if (t != null) {
                paletteDragType = t;
                paletteDragX = mx; paletteDragY = my;
                return true;
            }
            if (palette.inside(mx, my)) return true;
        }
        if (!rightCollapsed && (varPanel.menuOpen() || varPanel.inside(mx, my))) return varPanel.mousePressed(mx, my);
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        return canvas.mouseClicked(mx, my, button, ctrl, shift, doubled);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (paramEditor.isActive() || exprEditor.isActive() || selectorEditor.isActive() || loadDialogOpen || loadFailedName != null) return true;
        if (paletteDragType != null) {
            paletteDragX = event.x(); paletteDragY = event.y();
            return true;
        }
        if (palette.mouseDragged(event.x(), event.y())) return true;
        if (!rightCollapsed && varPanel.mouseDragged(event.x(), event.y())) return true;
        return canvas.mouseDragged(event.x(), event.y());
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (paramEditor.isActive() || exprEditor.isActive() || selectorEditor.isActive() || loadDialogOpen || loadFailedName != null) return true;
        if (palette.mouseReleased(event.y())) return true;
        if (!rightCollapsed && varPanel.mouseReleased(event.x(), event.y())) return true;
        if (paletteDragType != null) {
            BlockType t = paletteDragType;
            paletteDragType = null;
            double mx = event.x(), my = event.y();
            if (canvas.inside(mx, my)) {
                double[] w = canvas.screenToWorld(mx, my);
                if (BlockDefRegistry.get(t).isReporter()) {
                    Object[] tgt = canvas.slotTargetAt(w[0], w[1]);
                    if (tgt != null) {
                        BlockInstance target = (BlockInstance) tgt[0];
                        String slot = (String) tgt[1];
                        if (canvas.canNestType(target, slot, t)) {
                            BlockInstance nb = createBlockAt(t, w[0], w[1]);
                            script.blocks().remove(nb.id());
                            target.setReporter(slot, nb);
                            ternarySlotChanged(target, slot);
                            select(target.id());
                            return true;
                        }
                    }
                }
                BlockInstance b = createBlockAt(t, w[0], w[1]);
                canvas.trySplice(b, w[0], w[1]);
            }
            return true;
        }

        int dragId = canvas.draggingBlockId();
        boolean overDeletePanel = overLeftPanel(event.x(), event.y()) || overRightPanel(event.x(), event.y());
        if (dragId >= 0 && overDeletePanel) {
            pushUndo();
            for (int id : canvas.draggedBlockIds()) deleteBlock(id);
            canvas.cancelDrag();
            return true;
        }
        if (canvas.isDraggingBlackHole() && overDeletePanel) {
            canvas.deleteBlackHole();
            return true;
        }
        if (canvas.isDraggingComments() && overDeletePanel) {
            pushUndo();
            for (int id : canvas.draggedCommentIds()) script.removeComment(id);
            canvas.cancelDrag();
            return true;
        }
        return canvas.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (paramEditor.isActive() || exprEditor.isActive() || selectorEditor.isActive() || loadDialogOpen || loadFailedName != null) return true;
        if (!leftCollapsed && palette.inside(mouseX, mouseY)) return palette.scrolled(scrollY);
        if (!rightCollapsed && varPanel.inside(mouseX, mouseY)) return varPanel.scrolled(scrollY);
        return canvas.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (paramEditor.isActive()) return paramEditor.keyPressed(event);
        if (exprEditor.isActive()) return exprEditor.keyPressed(event);
        if (selectorEditor.isActive()) return selectorEditor.keyPressed(event);
        if (canvas.isEditingComment()) {
            boolean cmtCtrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean cmtShift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            return canvas.commentKeyPressed(event.key(), cmtCtrl, cmtShift);
        }

        if (palette.isSearchFocused() && palette.keyPressed(event)) return true;

        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (toolbar.isMenuOpen() && key == GLFW.GLFW_KEY_ESCAPE) { toolbar.closeMenu(); return true; }
        if (loadFailedName != null && key == GLFW.GLFW_KEY_ESCAPE) { loadFailedName = null; return true; }
        if (loadDialogOpen && key == GLFW.GLFW_KEY_ESCAPE) { closeLoadDialog(); return true; }
        if (settingsOpen && key == GLFW.GLFW_KEY_ESCAPE) { settingsOpen = false; return true; }
        if (shortcutsOpen && key == GLFW.GLFW_KEY_ESCAPE) { shortcutsOpen = false; return true; }
        if (ctrl && key == GLFW.GLFW_KEY_A) { selectConnected(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_C) { copySelection(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_V) { paste(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_Y) { redo(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_S) { saveScript(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_D) { sortBlocks(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            if (shift) deleteSelectedBridging(); else deleteSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (paramEditor.isActive()) return paramEditor.charTyped(event);
        if (exprEditor.isActive()) return exprEditor.charTyped(event);
        if (selectorEditor.isActive()) return selectorEditor.charTyped(event);
        if (canvas.isEditingComment()) return canvas.commentCharTyped(event.codepoint());
        if (palette.charTyped(event)) return true;
        return super.charTyped(event);
    }

    private static int parseAutosave(String value, int fallback) {
        if (value == null) return fallback;
        String v = value.trim().toLowerCase(Locale.ROOT);
        int mult = 1;
        if (v.endsWith("m")) {
            mult = 60;
            v = v.substring(0, v.length() - 1).trim();
        }
        try {
            return Math.max(0, Integer.parseInt(v) * mult);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void removed() {
        persistDraft();
        super.removed();
    }

    @Override
    public void onClose() {
        persistDraft();
        ScriptTransfer.clearActive(this);
        super.onClose();
    }

    public void persistDraft() {
        boolean empty = script.blocks().isEmpty()
                && script.comments().isEmpty()
                && script.variables().isEmpty();
        if (isUnnamed() && empty) {
            EditorDraft.clear();
        } else {
            try {
                EditorDraft.save(AiScriptIO.toJson(script, positionsSorted));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
