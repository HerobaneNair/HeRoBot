package hero.bane.herobot.common.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.*;

public class HeroVoicePlugin implements VoicechatPlugin {
    public static final String PLUGIN_ID = "herobot";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            VoiceEngine.setServerApi(event.getVoicechat());
            VoiceEngine.refreshBotStates();
        });

        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            VoiceEngine.reset();
            VoiceEngine.setServerApi(null);
        });

        registration.registerEvent(ClientVoicechatInitializationEvent.class,
                event -> VoiceEngine.setClientApi(event.getVoicechat()));

        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoiceEngine.setServerApi(event.getVoicechat());

        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) return;

        VoiceEngine.onMicrophonePacket(sender.getPlayer().getUuid(), event.getPacket().getOpusEncodedData());
    }
}
