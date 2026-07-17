package hero.bane.herobot.client;

import com.mojang.blaze3d.platform.InputConstants;
import hero.bane.herobot.HeroBotSettings;
import hero.bane.herobot.client.control.ClientOps;
import hero.bane.herobot.client.control.ClientPlayerController;
import hero.bane.herobot.client.record.MovementRecorder;
import hero.bane.herobot.client.screen.ai.AiEditorScreen;
import hero.bane.herobot.client.screen.ai.ScriptTransfer;
import hero.bane.herobot.networking.AiDownloadFailedPayload;
import hero.bane.herobot.networking.AiDownloadPayload;
import hero.bane.herobot.networking.AiListPayload;
import hero.bane.herobot.networking.ChunkReassembler;
import hero.bane.herobot.networking.ControlPlayerPayload;
import hero.bane.herobot.networking.HeroBotSyncPayload;
import hero.bane.herobot.networking.ScriptCompression;
import hero.bane.herobot.rule.RuleConfigIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class HeroBotClient implements ClientModInitializer {
    private static boolean heroBotLoaded = false;
    public static KeyMapping openAiEditorKey;

    private static final ChunkReassembler DOWNLOAD_REASSEMBLER = new ChunkReassembler();

    @SuppressWarnings("resource")
    @Override
    public void onInitializeClient() {
        HeroBotSettings.serverHasHeroBot = false;

        ClientPlayNetworking.registerGlobalReceiver(HeroBotSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    RuleConfigIO.applyRemoteSettings(payload.settingsJson());
                    heroBotLoaded = true;
                    HeroBotSettings.serverHasHeroBot = true;
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            heroBotLoaded = false;
            HeroBotSettings.serverHasHeroBot = false;
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
                        hero.bane.herobot.HeroBot.LOGGER.warn("Failed to decode HeroScript download '{}'", payload.name(), e);
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
                client.player.displayClientMessage(
                        Component.literal("§c● REC §f" + secs + "s — /herorecord stop"), true);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("herorecord")
                    .executes(ctx -> stopRecording(false))
                    .then(ClientCommandManager.literal("stop")
                            .executes(ctx -> stopRecording(false)))
                    .then(ClientCommandManager.literal("cancel")
                            .executes(ctx -> stopRecording(true))));
        });
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
