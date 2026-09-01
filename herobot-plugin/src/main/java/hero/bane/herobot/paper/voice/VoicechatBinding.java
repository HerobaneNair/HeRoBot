package hero.bane.herobot.paper.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import hero.bane.herobot.common.voice.HeroVoicePlugin;
import hero.bane.herobot.common.voice.VoiceEngine;
import hero.bane.herobot.paper.HeroBot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

final class VoicechatBinding {

    private VoicechatBinding() {
    }

    static boolean bind(Plugin plugin) {
        BukkitVoicechatService service =
                Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            plugin.getLogger().warning("Simple Voice Chat is installed but did not expose its API service");
            return false;
        }

        service.registerPlugin(new HeroVoicePlugin());
        VoiceEngine.setEntityResolver(PluginVoice::playerByUuid);
        VoiceEngine.setOnlinePlayers(PluginVoice::onlinePlayers);
        VoiceEngine.setBotPlayers(PluginVoice::botPlayers);
        HeroBot.LOGGER.info("Registered HeroBot with Simple Voice Chat");
        return true;
    }

    static void unbind() {
        VoiceEngine.reset();
    }

    static void refreshBotStates() {
        VoiceEngine.refreshBotStates();
    }
}
