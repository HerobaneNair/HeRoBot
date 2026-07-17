package hero.bane.herobot.client.record;

import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.block.BlockInstance;
import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.block.EffectiveSlots;
import hero.bane.herobot.ai.block.ParamSlot;
import hero.bane.herobot.client.EditorPrefs;
import hero.bane.herobot.client.record.MovementRecorder.Frame;
import hero.bane.herobot.client.record.MovementRecorder.InvAction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class RecordingAssembler {
    private static final int LOOK_SAMPLE_TICKS = 3;
    private static final float LOOK_THRESHOLD_DEG = 1.5f;

    private static final int USE_HOLD_TICKS = 3;

    private RecordingAssembler() {}

    private record Spec(BlockType type, Map<String, Object> params) {
        static Spec of(BlockType type, Object... kv) {
            Map<String, Object> p = new LinkedHashMap<>();
            for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], kv[i + 1]);
            return new Spec(type, p);
        }
    }

    private record Event(int tick, List<Spec> specs) {}

    public static void assemble(AiScript script, List<Frame> frames, List<InvAction> invActions) {
        if (frames.isEmpty()) return;

        List<Frame> all = new ArrayList<>(frames);
        all.add(frames.getLast().released());

        List<List<Event>> channels = new ArrayList<>();
        addChannel(channels, axisChannel(all, Frame::forward, BlockType.MOVE, "forward", "backward"));
        addChannel(channels, axisChannel(all, Frame::strafe, BlockType.STRAFE, "left", "right"));
        addChannel(channels, toggleChannel(all, Frame::sneak, BlockType.SNEAK));
        addChannel(channels, toggleChannel(all, Frame::sprint, BlockType.SPRINT));
        addChannel(channels, heldActionChannel(all, Frame::jump, BlockType.JUMP, "JUMP"));
        addChannel(channels, heldActionChannel(all, Frame::attack, BlockType.ATTACK, "ATTACK"));
        addChannel(channels, useChannel(all));
        addChannel(channels, hotbarChannel(all));
        addChannel(channels, cameraChannel(all));
        addChannel(channels, inventoryChannel(invActions));
        if (channels.isEmpty()) return;

        BlockInstance start = script.addBlock(BlockType.START, 0, 0);
        if (EditorPrefs.recordSingleTree()) {
            List<Event> merged = new ArrayList<>();
            for (List<Event> channel : channels) merged.addAll(channel);
            merged.sort(Comparator.comparingInt(Event::tick));
            script.addWire(start.id(), 0, emitChain(script, merged));
        } else {
            for (List<Event> channel : channels) {
                int first = emitChain(script, channel);
                script.addWire(start.id(), 0, first);
            }
        }
    }

    private static void addChannel(List<List<Event>> channels, List<Event> channel) {
        if (!channel.isEmpty()) channels.add(channel);
    }

    private static List<Event> axisChannel(List<Frame> frames, ToIntFunction<Frame> axis,
                                           BlockType type, String pos, String neg) {
        List<Event> out = new ArrayList<>();
        int prev = 0;
        for (int t = 0; t < frames.size(); t++) {
            int v = axis.applyAsInt(frames.get(t));
            if (v != prev) {
                String dir = v > 0 ? pos : v < 0 ? neg : "stop";
                out.add(new Event(t, List.of(Spec.of(type, "direction", dir))));
            }
            prev = v;
        }
        return out;
    }

    private static List<Event> toggleChannel(List<Frame> frames, Predicate<Frame> get, BlockType type) {
        List<Event> out = new ArrayList<>();
        boolean prev = false;
        for (int t = 0; t < frames.size(); t++) {
            boolean v = get.test(frames.get(t));
            if (v != prev) out.add(new Event(t, List.of(Spec.of(type, "value", v))));
            prev = v;
        }
        return out;
    }

    private static List<Event> heldActionChannel(List<Frame> frames, Predicate<Frame> get,
                                                 BlockType type, String actionName) {
        List<Event> out = new ArrayList<>();
        boolean prev = false;
        for (int t = 0; t < frames.size(); t++) {
            boolean v = get.test(frames.get(t));
            if (v && !prev) {
                out.add(new Event(t, List.of(Spec.of(type, "mode", "continuous", "interval", 1, "ticks", 0))));
            } else if (!v && prev) {
                out.add(new Event(t, List.of(Spec.of(BlockType.STOP_ACTION, "action", actionName))));
            }
            prev = v;
        }
        return out;
    }

    private static List<Event> useChannel(List<Frame> frames) {
        List<Event> out = new ArrayList<>();
        int t = 0;
        int n = frames.size();
        while (t < n) {
            if (!frames.get(t).use()) { t++; continue; }
            int start = t;
            while (t < n && frames.get(t).use()) t++;
            int release = t;
            if (release - start > USE_HOLD_TICKS) {
                out.add(new Event(start, List.of(Spec.of(BlockType.USE, "mode", "continuous", "interval", 1, "ticks", 0))));
                out.add(new Event(release, List.of(Spec.of(BlockType.STOP_ACTION, "action", "USE"))));
            } else {
                out.add(new Event(start, List.of(Spec.of(BlockType.USE, "mode", "once"))));
            }
        }
        return out;
    }

    private static List<Event> inventoryChannel(List<InvAction> invActions) {
        List<Event> out = new ArrayList<>();
        if (invActions == null) return out;
        for (InvAction a : invActions) {
            out.add(new Event(a.tick(), List.of(new Spec(a.type(), new LinkedHashMap<>(a.params())))));
        }
        return out;
    }

    private static List<Event> hotbarChannel(List<Frame> frames) {
        List<Event> out = new ArrayList<>();
        int prev = frames.getFirst().slot();
        for (int t = 0; t < frames.size(); t++) {
            int v = frames.get(t).slot();
            if (v != prev) out.add(new Event(t, List.of(Spec.of(BlockType.SELECT_HOTBAR, "slot", v + 1))));
            prev = v;
        }
        return out;
    }

    private static List<Event> cameraChannel(List<Frame> frames) {
        List<Event> out = new ArrayList<>();
        Frame first = frames.getFirst();

        out.add(lookEvent(0, first.yaw(), first.pitch(), 0));

        List<Integer> samples = new ArrayList<>();
        float lastYaw = first.yaw(), lastPitch = first.pitch();
        for (int t = LOOK_SAMPLE_TICKS; t < frames.size(); t += LOOK_SAMPLE_TICKS) {
            Frame f = frames.get(t);
            if (rotationDelta(f.yaw(), f.pitch(), lastYaw, lastPitch) >= LOOK_THRESHOLD_DEG) {
                samples.add(t);
                lastYaw = f.yaw(); lastPitch = f.pitch();
            }
        }

        for (int i = 0; i < samples.size(); i++) {
            int t = samples.get(i);
            int next = i + 1 < samples.size() ? samples.get(i + 1) : t + LOOK_SAMPLE_TICKS;
            Frame f = frames.get(t);
            out.add(lookEvent(t, f.yaw(), f.pitch(), next - t));
        }
        return out;
    }

    private static Event lookEvent(int tick, float yaw, float pitch, int ticks) {
        return new Event(tick, List.of(Spec.of(BlockType.LOOK_DIRECTION,
                "direction", round2(Mth.wrapDegrees(yaw)) + " " + round2(pitch),
                "ticks", ticks)));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static float rotationDelta(float yaw, float pitch, float lastYaw, float lastPitch) {
        return Math.abs(Mth.wrapDegrees(yaw - lastYaw)) + Math.abs(pitch - lastPitch);
    }

    private static int emitChain(AiScript script, List<Event> events) {
        int firstId = -1;
        int prevId = -1;
        int cursor = 0;
        for (Event e : events) {
            int gap = e.tick() - cursor;
            if (gap > 0) {
                BlockInstance wait = newBlock(script, BlockType.WAIT);
                wait.setParam("ticks", gap);
                prevId = link(script, prevId, wait.id());
                if (firstId < 0) firstId = wait.id();
            }
            for (Spec s : e.specs()) {
                BlockInstance b = newBlock(script, s.type());
                s.params().forEach(b::setParam);
                prevId = link(script, prevId, b.id());
                if (firstId < 0) firstId = b.id();
            }
            cursor = e.tick();
        }
        return firstId;
    }

    private static int link(AiScript script, int prevId, int id) {
        if (prevId >= 0) script.addWire(prevId, 0, id);
        return id;
    }

    private static BlockInstance newBlock(AiScript script, BlockType type) {
        BlockInstance b = script.addBlock(type, 0, 0);
        for (ParamSlot slot : EffectiveSlots.initialSlots(type)) {
            b.setParam(slot.name(), slot.defaultValue());
        }
        return b;
    }
}
