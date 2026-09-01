package hero.bane.herobot.mod.client.record;

import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.mod.client.screen.ai.AiEditorScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class MovementRecorder {
    public static final MovementRecorder INSTANCE = new MovementRecorder();

    private static final int K_FORWARD = 0;
    private static final int K_BACK = 1;
    private static final int K_LEFT = 2;
    private static final int K_RIGHT = 3;
    private static final int K_JUMP = 4;
    private static final int K_SNEAK = 5;
    private static final int K_ATTACK = 6;
    private static final int K_USE = 7;
    private static final int KEY_COUNT = 8;

    private MovementRecorder() {}

    public record Frame(int forward, int strafe,
                        boolean sneak, boolean sprint, boolean jump,
                        boolean attack, boolean use,
                        int slot, float yaw, float pitch) {
        public Frame released() {
            return new Frame(0, 0, false, false, false, false, false, slot, yaw, pitch);
        }
    }

    public record InvAction(int tick, BlockType type, Map<String, Object> params) {}

    private boolean recording;
    private AiEditorScreen editor;
    private final Map<KeyMapping, Integer> watched = new IdentityHashMap<>();
    private final boolean[] held = new boolean[KEY_COUNT];
    private final boolean[] tapped = new boolean[KEY_COUNT];
    private final List<Frame> frames = new ArrayList<>();
    private final List<InvAction> invActions = new ArrayList<>();
    private final List<Integer> dragSlots = new ArrayList<>();
    private String dragMenu;
    private String dragButton;
    private boolean prevInvScreen;
    private boolean prevMenuOpen;
    private int lastSelectedTrade;

    private double startX, startY, startZ;

    private AiEditorScreen pendingTarget;
    private List<Frame> pendingFrames;
    private List<InvAction> pendingInvActions;
    private double pendingX, pendingY, pendingZ;

    public boolean isRecording() { return recording; }

    public int recordedTicks() { return frames.size(); }

    public void start(AiEditorScreen editor) {
        this.editor = editor;
        frames.clear();
        invActions.clear();
        dragSlots.clear();
        dragMenu = null;
        dragButton = null;
        prevInvScreen = false;
        prevMenuOpen = false;
        lastSelectedTrade = -1;
        bindKeys(Minecraft.getInstance().options);
        LocalPlayer p = Minecraft.getInstance().player;
        startX = p != null ? p.getX() : 0;
        startY = p != null ? p.getY() : 0;
        startZ = p != null ? p.getZ() : 0;
        recording = true;
        Minecraft.getInstance().setScreen(null);
    }

    private void bindKeys(Options o) {
        watched.clear();
        Arrays.fill(held, false);
        Arrays.fill(tapped, false);
        if (o == null) return;
        watch(o.keyUp, K_FORWARD);
        watch(o.keyDown, K_BACK);
        watch(o.keyLeft, K_LEFT);
        watch(o.keyRight, K_RIGHT);
        watch(o.keyJump, K_JUMP);
        watch(o.keyShift, K_SNEAK);
        watch(o.keyAttack, K_ATTACK);
        watch(o.keyUse, K_USE);
    }

    private void watch(KeyMapping mapping, int index) {
        watched.put(mapping, index);
        held[index] = mapping.isDown();
    }

    public void onKeyStateChanged(KeyMapping mapping, boolean down) {
        if (!recording) return;
        Integer index = watched.get(mapping);
        if (index == null) return;
        held[index] = down;
        if (down) tapped[index] = true;
    }

    public void sampleTick() {
        if (recording) sample();
    }

    public void clientTick() {
        if (recording) return;
        if (pendingTarget != null) {
            AiEditorScreen target = pendingTarget;
            List<Frame> captured = pendingFrames;
            List<InvAction> capturedInv = pendingInvActions;
            double px = pendingX, py = pendingY, pz = pendingZ;
            pendingTarget = null;
            pendingFrames = null;
            pendingInvActions = null;
            Minecraft.getInstance().setScreen(target);
            if (captured != null) target.finishRecording(captured, capturedInv, px, py, pz);
        }
    }

    private void sample() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { cancel(); return; }
        sampleScreens(mc);
        int forward = (held[K_FORWARD] ? 1 : 0) - (held[K_BACK] ? 1 : 0);
        int strafe = (held[K_LEFT] ? 1 : 0) - (held[K_RIGHT] ? 1 : 0);
        frames.add(new Frame(
                forward, strafe,
                held[K_SNEAK], p.isSprinting(), held[K_JUMP],
                held[K_ATTACK] || tapped[K_ATTACK],
                held[K_USE] || tapped[K_USE],
                p.getInventory().getSelectedSlot(),
                p.getYRot(), p.getXRot()));
        Arrays.fill(tapped, false);
    }

    private void sampleScreens(Minecraft mc) {
        boolean invScreen = mc.screen instanceof InventoryScreen;
        boolean menuOpen = mc.screen instanceof AbstractContainerScreen<?>;
        if (invScreen && !prevInvScreen) {
            invActions.add(new InvAction(frames.size(), BlockType.OPEN_INVENTORY, Map.of()));
        }
        if (!menuOpen && prevMenuOpen) {
            invActions.add(new InvAction(frames.size(), BlockType.CLOSE_SCREEN, Map.of()));
        }
        prevInvScreen = invScreen;
        prevMenuOpen = menuOpen;
    }

    public void onSlotClick(int containerId, int slotId, int button, ClickType type) {
        if (!recording) return;
        int tick = frames.size();
        String menu = containerId == 0 ? "inventory" : "container";
        switch (type) {
            case PICKUP -> {
                if (slotId == -999) invActions.add(new InvAction(tick, BlockType.INV_HELD_THROW, Map.of("menu", menu)));
                else invClick(tick, menu, button == 1 ? "rightClick" : "click", slotId);
            }
            case QUICK_MOVE -> invClick(tick, menu, "shiftClick", slotId);
            case THROW -> invClick(tick, menu, button == 0 ? "throw" : "throwAll", slotId);
            case SWAP -> {
                if (button == 40) {
                    invActions.add(new InvAction(tick, BlockType.INV_SWAP_HOTBAR,
                            Map.of("menu", menu, "slot", slotId, "with", "offhand")));
                } else if (button >= 0 && button <= 8) {
                    invActions.add(new InvAction(tick, BlockType.INV_SWAP_HOTBAR,
                            Map.of("menu", menu, "slot", slotId, "with", String.valueOf(button + 1))));
                }
            }
            case QUICK_CRAFT -> {
                int header = AbstractContainerMenu.getQuickcraftHeader(button);
                if (header == 0) {
                    dragSlots.clear();
                    dragMenu = menu;
                    dragButton = AbstractContainerMenu.getQuickcraftType(button) == 1 ? "right" : "left";
                } else if (header == 1) {
                    if (slotId >= 0) dragSlots.add(slotId);
                } else if (header == 2 && !dragSlots.isEmpty()) {
                    StringJoiner slots = new StringJoiner(",");
                    for (int s : dragSlots) slots.add(String.valueOf(s));
                    invActions.add(new InvAction(tick, BlockType.INV_HELD_DRAG,
                            Map.of("menu", dragMenu != null ? dragMenu : menu,
                                    "button", dragButton != null ? dragButton : "left",
                                    "slots", slots.toString())));
                    dragSlots.clear();
                    dragMenu = null;
                    dragButton = null;
                }
            }
            default -> {}
        }
    }

    public void onTradeButton(int offer) {
        if (!recording) return;
        int tick = frames.size();
        if (offer == lastSelectedTrade) {
            invActions.add(new InvAction(tick, BlockType.TRADE_RESTOCK, Map.of()));
        } else {
            lastSelectedTrade = offer;
            invActions.add(new InvAction(tick, BlockType.TRADE_SELECT, Map.of("index", offer + 1)));
        }
    }

    private void invClick(int tick, String menu, String mode, int slot) {
        if (slot < 0) return;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("menu", menu);
        params.put("mode", mode);
        params.put("slot", slot);
        invActions.add(new InvAction(tick, BlockType.INV_CLICK, params));
    }

    public void stop() {
        if (!recording) return;
        recording = false;
        pendingTarget = editor;
        pendingFrames = new ArrayList<>(frames);
        pendingInvActions = new ArrayList<>(invActions);
        pendingX = startX; pendingY = startY; pendingZ = startZ;
        editor = null;
        frames.clear();
        invActions.clear();
        watched.clear();
    }

    public void cancel() {
        if (!recording) return;
        recording = false;
        pendingTarget = editor;
        pendingFrames = null;
        pendingInvActions = null;
        editor = null;
        frames.clear();
        invActions.clear();
        watched.clear();
    }
}
