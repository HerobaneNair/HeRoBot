package hero.bane.herobot.paper.ping;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import hero.bane.herobot.common.ping.PingDelayOptions;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PingBoostHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "herobot_ping_boost";

    public static final int MAX_ADDED_DELAY_MS = 2000;

    private static final int BASE_FILTER_WINDOW = 3;
    private static final double BASE_PING_ALPHA = 0.3;
    private static final long MIN_RESCHEDULE_NANOS = TimeUnit.MICROSECONDS.toNanos(250);
    private static final long KEEP_ALIVE_MAX_AGE_NANOS = TimeUnit.SECONDS.toNanos(60);

    private volatile PingDelayOptions options = new PingDelayOptions();

    private record Outbound(Object msg, ChannelPromise promise, long sendAtNanos) {}

    private record Inbound(Object msg, long deliverAtNanos) {}

    private final ConcurrentLinkedQueue<Outbound> outboundQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Inbound> inboundQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainingOutbound = new AtomicBoolean();
    private final AtomicBoolean drainingInbound = new AtomicBoolean();
    private final Map<Long, Long> pendingKeepAlives = new ConcurrentHashMap<>();

    private final int[] baseWindow = new int[BASE_FILTER_WINDOW];
    private int baseCount;
    private int baseIndex;

    private volatile ChannelHandlerContext savedContext;
    private volatile boolean active = true;
    private volatile boolean suspendAfterDrain;
    private volatile int targetPingMs;
    private volatile int delayMs;
    private volatile double smoothedBaseMs = -1;
    private volatile int lastSampleMs = -1;

    public void setTarget(int targetMs) {
        this.targetPingMs = Math.max(0, targetMs);
        recomputeDelay();
    }

    public int target() {
        return targetPingMs;
    }

    public int addedDelayMs() {
        return delayMs;
    }

    public int baseMs() {
        double smoothed = smoothedBaseMs;
        return smoothed < 0 ? 0 : (int) Math.round(smoothed);
    }

    public boolean hasMeasuredBase() {
        return lastSampleMs >= 0;
    }

    public int displayPingMs() {
        return baseMs() + delayMs;
    }

    public void seedBase(int estimateMs) {
        if (estimateMs < 0 || smoothedBaseMs >= 0) return;
        smoothedBaseMs = estimateMs;
        recomputeDelay();
    }

    public void reactivate() {
        if (!active) {
            suspendAfterDrain = false;
            active = true;
        }
    }

    public void shutdown() {
        targetPingMs = 0;
        delayMs = 0;
        active = false;
        flushEverything();
    }

    private void recomputeDelay() {
        int target = targetPingMs;
        if (target <= 0) {
            delayMs = 0;
            return;
        }
        double smoothed = smoothedBaseMs;
        int base = smoothed < 0 ? 0 : (int) Math.round(smoothed);
        delayMs = Math.clamp(target - base, 0, MAX_ADDED_DELAY_MS);
    }

    private long outboundDelayMs() {
        return delayMs / 2L;
    }

    private long inboundDelayMs() {
        long total = delayMs;
        return total - (total / 2L);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.savedContext = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        flushEverything();
        this.savedContext = null;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        active = false;
        outboundQueue.clear();
        inboundQueue.clear();
        pendingKeepAlives.clear();
        super.channelInactive(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!active || !(msg instanceof Packet<?> packet)) {
            super.write(ctx, msg, promise);
            return;
        }

        boolean terminal = packet.isTerminal();
        if (terminal) suspendAfterDrain = true;

        if (terminal || !(packet instanceof ClientboundKeepAlivePacket)) {
            super.write(ctx, msg, promise);
            maybeSuspend();
            return;
        }

        long delay = outboundDelayMs();
        if (delay <= 0 && outboundQueue.isEmpty()) {
            stampKeepAlive(msg);
            super.write(ctx, msg, promise);
            maybeSuspend();
            return;
        }

        outboundQueue.offer(new Outbound(msg, promise, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay)));
        drainOutbound(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!active || !(msg instanceof Packet<?> packet)) {
            super.channelRead(ctx, msg);
            return;
        }

        if (packet instanceof ServerboundKeepAlivePacket keepAlive) {
            observeKeepAlive(keepAlive.getId());
        }

        boolean terminal = packet.isTerminal();
        if (terminal) suspendAfterDrain = true;

        if (terminal || !(delays(packet) || packet instanceof ServerboundKeepAlivePacket)) {
            super.channelRead(ctx, msg);
            maybeSuspend();
            return;
        }

        Object delayed = msg;
        if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition() && move.hasRotation()) {

            super.channelRead(ctx, new ServerboundMovePlayerPacket.Pos(
                    move.getX(0), move.getY(0), move.getZ(0), move.isOnGround(), move.horizontalCollision()));
            delayed = new ServerboundMovePlayerPacket.Rot(
                    move.getYRot(0), move.getXRot(0), move.isOnGround(), move.horizontalCollision());
        }

        long delay = inboundDelayMs();
        if (delay <= 0 && inboundQueue.isEmpty()) {
            super.channelRead(ctx, delayed);
            maybeSuspend();
            return;
        }

        inboundQueue.offer(new Inbound(delayed, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay)));
        drainInbound(ctx);
    }

    private void drainOutbound(ChannelHandlerContext ctx) {
        if (!drainingOutbound.compareAndSet(false, true)) return;
        if (ctx.executor().inEventLoop()) {
            doDrainOutbound(ctx);
        } else {
            ctx.executor().execute(() -> doDrainOutbound(ctx));
        }
    }

    private void doDrainOutbound(ChannelHandlerContext ctx) {
        boolean wrote = false;
        try {
            while (true) {
                Outbound task = outboundQueue.peek();
                if (task == null) {
                    if (wrote && ctx.channel().isOpen()) ctx.flush();
                    drainingOutbound.set(false);
                    if (!outboundQueue.isEmpty()) drainOutbound(ctx);
                    maybeSuspend();
                    return;
                }

                long remaining = task.sendAtNanos() - System.nanoTime();
                if (remaining > 0) {
                    if (wrote && ctx.channel().isOpen()) ctx.flush();
                    long wait = Math.max(remaining, MIN_RESCHEDULE_NANOS);
                    ctx.executor().schedule(() -> drainOutbound(ctx), wait, TimeUnit.NANOSECONDS);
                    drainingOutbound.set(false);
                    return;
                }

                outboundQueue.poll();
                stampKeepAlive(task.msg());
                ctx.write(task.msg(), task.promise());
                wrote = true;
            }
        } catch (RuntimeException e) {
            drainingOutbound.set(false);
            throw e;
        }
    }

    private void drainInbound(ChannelHandlerContext ctx) {
        if (!drainingInbound.compareAndSet(false, true)) return;
        if (ctx.executor().inEventLoop()) {
            doDrainInbound(ctx);
        } else {
            ctx.executor().execute(() -> doDrainInbound(ctx));
        }
    }

    private void doDrainInbound(ChannelHandlerContext ctx) {
        try {
            while (true) {
                Inbound task = inboundQueue.peek();
                if (task == null) {
                    drainingInbound.set(false);
                    if (!inboundQueue.isEmpty()) drainInbound(ctx);
                    maybeSuspend();
                    return;
                }

                long remaining = task.deliverAtNanos() - System.nanoTime();
                if (remaining > 0) {
                    long wait = Math.max(remaining, MIN_RESCHEDULE_NANOS);
                    ctx.executor().schedule(() -> drainInbound(ctx), wait, TimeUnit.NANOSECONDS);
                    drainingInbound.set(false);
                    return;
                }

                inboundQueue.poll();
                ctx.fireChannelRead(task.msg());
            }
        } catch (RuntimeException e) {
            drainingInbound.set(false);
            throw e;
        }
    }

    public void setOptions(PingDelayOptions options) {
        this.options = options;
    }

    private static PingDelayOptions.Category categoryOf(Packet<?> packet) {
        if (packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundUseItemOnPacket) {
            return PingDelayOptions.Category.USE;
        }
        if (packet instanceof ServerboundChatPacket || packet instanceof ServerboundChatAckPacket) {
            return PingDelayOptions.Category.CHAT;
        }
        if (packet instanceof ServerboundMovePlayerPacket move && move.hasRotation()) {
            return PingDelayOptions.Category.LOOK;
        }
        if (packet instanceof ServerboundInteractPacket interact) {
            return attackOrUse(interact);
        }
        return null;
    }

    private static PingDelayOptions.Category attackOrUse(ServerboundInteractPacket interact) {
        InteractionKind kind = new InteractionKind();
        interact.dispatch(kind);
        return kind.attack ? PingDelayOptions.Category.ATTACK : PingDelayOptions.Category.USE;
    }

    private static final class InteractionKind implements ServerboundInteractPacket.Handler {
        boolean attack;

        @Override
        public void onInteraction(@NonNull InteractionHand hand) {
        }

        @Override
        public void onInteraction(@NonNull InteractionHand hand, @NonNull Vec3 pos) {
        }

        @Override
        public void onAttack() {
            attack = true;
        }
    }

    private boolean delays(Packet<?> packet) {
        PingDelayOptions.Category category = categoryOf(packet);
        return category != null && options.isEnabled(category);
    }

    private void maybeSuspend() {
        if (!suspendAfterDrain) return;
        if (!outboundQueue.isEmpty() || !inboundQueue.isEmpty()) return;
        suspendAfterDrain = false;
        active = false;
        pendingKeepAlives.clear();
    }

    private void stampKeepAlive(Object msg) {
        if (!(msg instanceof ClientboundKeepAlivePacket keepAlive)) return;
        long now = System.nanoTime();
        expirePendingKeepAlives(now);
        pendingKeepAlives.put(keepAlive.getId(), now);
    }

    private void observeKeepAlive(long id) {
        Long sentAt = pendingKeepAlives.remove(id);
        if (sentAt == null) return;
        long rttNanos = System.nanoTime() - sentAt;
        if (rttNanos < 0) return;
        pushBaseSample((int) TimeUnit.NANOSECONDS.toMillis(rttNanos));
    }

    private void expirePendingKeepAlives(long nowNanos) {
        if (pendingKeepAlives.size() <= BASE_FILTER_WINDOW) return;
        pendingKeepAlives.entrySet().removeIf(e -> nowNanos - e.getValue() > KEEP_ALIVE_MAX_AGE_NANOS);
    }

    private synchronized void pushBaseSample(int sampleMs) {
        baseWindow[baseIndex] = sampleMs;
        baseIndex = (baseIndex + 1) % BASE_FILTER_WINDOW;
        if (baseCount < BASE_FILTER_WINDOW) baseCount++;

        int[] sorted = Arrays.copyOf(baseWindow, baseCount);
        Arrays.sort(sorted);
        int median = sorted[baseCount / 2];

        lastSampleMs = median;
        smoothedBaseMs = smoothedBaseMs < 0
                ? median
                : smoothedBaseMs * (1.0 - BASE_PING_ALPHA) + median * BASE_PING_ALPHA;
        recomputeDelay();
    }

    private void flushEverything() {
        ChannelHandlerContext ctx = savedContext;
        if (ctx == null) {
            outboundQueue.clear();
            inboundQueue.clear();
            drainingOutbound.set(false);
            drainingInbound.set(false);
            return;
        }
        if (ctx.executor().inEventLoop()) {
            flushEverythingNow(ctx);
        } else {
            ctx.executor().execute(() -> flushEverythingNow(ctx));
        }
    }

    private void flushEverythingNow(ChannelHandlerContext ctx) {
        boolean open = ctx.channel().isOpen();
        boolean wrote = false;
        Outbound out;
        while ((out = outboundQueue.poll()) != null) {
            if (open) {
                ctx.write(out.msg(), out.promise());
                wrote = true;
            }
        }
        if (wrote) ctx.flush();
        drainingOutbound.set(false);

        Inbound in;
        while ((in = inboundQueue.poll()) != null) {
            if (open) ctx.fireChannelRead(in.msg());
        }
        drainingInbound.set(false);
    }
}
