package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.AiScriptIO;
import hero.bane.herobot.networking.AiDeleteRequestPayload;
import hero.bane.herobot.networking.AiDownloadRequestPayload;
import hero.bane.herobot.networking.AiListRequestPayload;
import hero.bane.herobot.networking.AiUploadPayload;
import hero.bane.herobot.networking.ScriptCompression;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

public final class ScriptTransfer {
    private static volatile AiEditorScreen active;

    private ScriptTransfer() {}

    public static void setActive(AiEditorScreen screen) {
        active = screen;
    }

    public static void clearActive(AiEditorScreen screen) {
        if (active == screen) active = null;
    }

    public static void upload(String name, AiScript script) {
        if (!ClientPlayNetworking.canSend(AiUploadPayload.TYPE)) return;
        byte[] data = ScriptCompression.compress(AiScriptIO.toJson(script));
        List<byte[]> chunks = ScriptCompression.chunk(data);
        int count = chunks.size();
        for (int i = 0; i < count; i++) {
            ClientPlayNetworking.send(new AiUploadPayload(name, i, count, chunks.get(i)));
        }
    }

    public static void requestList() {
        if (!ClientPlayNetworking.canSend(AiListRequestPayload.TYPE)) return;
        ClientPlayNetworking.send(AiListRequestPayload.INSTANCE);
    }

    public static void requestDownload(String name) {
        if (!ClientPlayNetworking.canSend(AiDownloadRequestPayload.TYPE)) return;
        ClientPlayNetworking.send(new AiDownloadRequestPayload(name));
    }

    public static void requestDelete(String name) {
        if (!ClientPlayNetworking.canSend(AiDeleteRequestPayload.TYPE)) return;
        ClientPlayNetworking.send(new AiDeleteRequestPayload(name));
    }

    public static void onListReceived(List<String> names) {
        AiEditorScreen s = active;
        if (s != null) s.onScriptList(names);
    }

    public static void onScriptReceived(String name, String json) {
        AiEditorScreen s = active;
        if (s != null) s.onScriptDownloaded(name, json);
    }

    public static void onScriptDownloadFailed(String name) {
        AiEditorScreen s = active;
        if (s != null) s.onScriptDownloadFailed(name);
    }
}
