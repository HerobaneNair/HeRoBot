package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.AiScriptIO;
import hero.bane.herobot.client.net.ServerLink;
import hero.bane.herobot.networking.*;
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

    public static void upload(String name, AiScript script, boolean omitPositions) {
        if (!ServerLink.canSend(AiUploadPayload.TYPE)) return;
        byte[] data = ScriptCompression.compress(AiScriptIO.toJson(script, omitPositions));
        List<byte[]> chunks = ScriptCompression.chunk(data);
        int count = chunks.size();
        for (int i = 0; i < count; i++) {
            ClientPlayNetworking.send(new AiUploadPayload(name, i, count, chunks.get(i)));
        }
    }

    public static void requestList() {
        if (!ServerLink.canSend(AiListRequestPayload.TYPE)) return;
        ClientPlayNetworking.send(AiListRequestPayload.INSTANCE);
    }

    public static void requestDownload(String name) {
        if (!ServerLink.canSend(AiDownloadRequestPayload.TYPE)) return;
        ClientPlayNetworking.send(new AiDownloadRequestPayload(name));
    }

    public static void requestDelete(String name) {
        if (!ServerLink.canSend(AiDeleteRequestPayload.TYPE)) return;
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
