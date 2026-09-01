package hero.bane.herobot.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.mod.client.control.ClientOps;
import hero.bane.herobot.mod.client.control.ClientPlayerController;
import hero.bane.herobot.mod.client.net.ServerLink;
import hero.bane.herobot.mod.client.record.MovementRecorder;
import hero.bane.herobot.mod.client.screen.ai.AiEditorScreen;
import hero.bane.herobot.mod.client.screen.ai.ScriptTransfer;
import hero.bane.herobot.mod.common.networking.*;
import hero.bane.herobot.mod.common.rule.RuleConfigIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;
import hero.bane.herobot.common.networking.ChunkReassembler;
import hero.bane.herobot.common.networking.ScriptCompression;

public class HeroBotClient implements ClientModInitializer {
    private static boolean heroBotLoaded = false;
    public static KeyMapping openAiEditorKey;

    private static final int REC_TICKS = 30;
    private static final int REC_FADE_TICKS = 10;
    private static int recIndicatorTicks;
    private static Component recIndicatorText = Component.empty();

    private static final ChunkReassembler DOWNLOAD_REASSEMBLER = new ChunkReassembler();

    @SuppressWarnings("resource")
    @Override
    public void onInitializeClient() {
        HeroBotSettings.serverHasHeroBot = false;

        ClientPlayNetworking.registerGlobalReceiver(HeroBotSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.protocol() != HeroBotSyncPayload.PROTOCOL) {
                        hero.bane.herobot.mod.common.HeroBot.LOGGER.warn(
                                "HeroBot server protocol {} does not match client protocol {}; disabling",
                                payload.protocol(), HeroBotSyncPayload.PROTOCOL);
                        return;
                    }
                    RuleConfigIO.applyRemoteSettings(payload.settingsJson());
                    heroBotLoaded = true;
                    HeroBotSettings.serverHasHeroBot = true;
                    ServerLink.onHandshake();
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            heroBotLoaded = false;
            HeroBotSettings.serverHasHeroBot = false;
            ServerLink.reset();
            RuleConfigIO.reapplyLayers();
            ClientOps.INSTANCE.reset();
            ClientPlayerController.INSTANCE.reset();
        });

        ClientPlayNetworking.registerGlobalReceiver(ControlPlayerPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (heroBotLoaded && context.client().player != null) {
                        if (!ClientOps.INSTANCE.handle(payload.op())) {
                            payload.op().apply(ClientPlayerController.INSTANCE);
                        }
                    }
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (heroBotLoaded && client.player != null) {
                ClientOps.INSTANCE.tick();
                ClientPlayerController.INSTANCE.clientTick();
            }
        });

        registerAiEditor();
    }

    private void registerAiEditor() {
        ClientPlayNetworking.registerGlobalReceiver(AiListPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ScriptTransfer.onListReceived(payload.names())));
        ClientPlayNetworking.registerGlobalReceiver(AiDownloadPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    byte[] full = DOWNLOAD_REASSEMBLER.accept(
                            payload.name(), payload.index(), payload.count(), payload.data());
                    if (full == null) return;
                    try {
                        ScriptTransfer.onScriptReceived(payload.name(), ScriptCompression.decompress(full));
                    } catch (RuntimeException e) {
                        hero.bane.herobot.mod.common.HeroBot.LOGGER.warn("Failed to decode HeroScript download '{}'", payload.name(), e);
                        ScriptTransfer.onScriptDownloadFailed(payload.name());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(AiDownloadFailedPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ScriptTransfer.onScriptDownloadFailed(payload.name())));

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("herobot", "general"));
        openAiEditorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.herobot.open_ai_editor",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                category));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (client.screen instanceof AiEditorScreen editor) editor.persistDraft();
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> MovementRecorder.INSTANCE.sampleTick());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openAiEditorKey.consumeClick()) {
                if (MovementRecorder.INSTANCE.isRecording()) {
                    MovementRecorder.INSTANCE.stop();
                } else if (client.screen == null && client.player != null) {
                    client.setScreen(new AiEditorScreen());
                }
            }

            MovementRecorder.INSTANCE.clientTick();
            if (MovementRecorder.INSTANCE.isRecording() && client.player != null) {
                int secs = MovementRecorder.INSTANCE.recordedTicks() / 20;
                recIndicatorText = Component.literal("§c⬤ REC §f" + secs + "s - /herorecord stop");
                recIndicatorTicks = REC_TICKS;
            } else if (recIndicatorTicks > 0) {
                recIndicatorTicks--;
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("herobot", "rec_indicator"), (graphics, deltaTracker) -> {
            if (recIndicatorTicks <= 0) return;
            float remaining = recIndicatorTicks - deltaTracker.getGameTimeDeltaPartialTick(false);
            int alpha = (int) (remaining * 255.0F / REC_FADE_TICKS);
            if (alpha > 255) alpha = 255;
            if (alpha <= 0) return;

            Font font = Minecraft.getInstance().font;
            int width = font.width(recIndicatorText);
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) graphics.guiWidth() / 2, graphics.guiHeight() - 68);
            graphics.drawStringWithBackdrop(font, recIndicatorText, -width / 2, -4, width, ARGB.white(alpha));
            graphics.pose().popMatrix();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("herorecord")
                .executes(ctx -> stopRecording(false))
                .then(ClientCommandManager.literal("stop")
                        .executes(ctx -> stopRecording(false)))
                .then(ClientCommandManager.literal("cancel")
                        .executes(ctx -> stopRecording(true)))));
    }

    private static int stopRecording(boolean cancel) {
        if (!MovementRecorder.INSTANCE.isRecording()) return 0;
        if (cancel) MovementRecorder.INSTANCE.cancel();
        else MovementRecorder.INSTANCE.stop();
        return 1;
    }

    public static boolean isHeroBotLoaded() {
        return heroBotLoaded;
    }
}
