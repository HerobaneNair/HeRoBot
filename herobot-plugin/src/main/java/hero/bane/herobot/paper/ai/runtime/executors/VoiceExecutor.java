package hero.bane.herobot.paper.ai.runtime.executors;

import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.common.ai.runtime.StepResult;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.ai.runtime.Executor;
import hero.bane.herobot.paper.ai.runtime.ParamEval;
import hero.bane.herobot.paper.ai.runtime.Reporter;
import hero.bane.herobot.paper.voice.VoiceOps;
import net.minecraft.world.entity.Entity;

import java.util.Map;

public final class VoiceExecutor {
    private VoiceExecutor() {}

    public static void register(Map<BlockType, Executor> flow, Map<BlockType, Reporter> reporter) {
        flow.put(BlockType.PLAY_SOUND, (b, r, br) -> {
            String name = ParamEval.evalString(b, "sound", r, br);
            boolean loop = ParamEval.evalBool(b, "loop", r, br);
            warn(VoiceOps.play(r.player(), name, loop));
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.STOP_SOUND, (b, r, br) -> {
            warn(VoiceOps.stop(r.player()));
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.BLUETOOTH, (b, r, br) -> {
            Object sel = ParamEval.raw(b, "source", r, br);
            Entity target = SelectorExecutor.resolveFirstEntity(sel, r);
            if (target == null) {
                warn("Bluetooth source did not match any player");
                return StepResult.continueVia(0);
            }
            warn(VoiceOps.bluetooth(r.player(), target.getUUID()));
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.STOP_BLUETOOTH, (b, r, br) -> {
            warn(VoiceOps.stopBluetooth(r.player()));
            return StepResult.continueVia(0);
        });

        reporter.put(BlockType.IS_SPEAKING, (b, r, br) -> VoiceOps.isSpeaking(r.player()));
        reporter.put(BlockType.IS_BLUETOOTHED, (b, r, br) -> VoiceOps.isBluetoothed(r.player()));
    }

    private static void warn(String error) {
        if (error == null || error.equals(VoiceOps.NO_VOICECHAT)) return;
        HeroBot.LOGGER.warn("HeroScript voice block: {}", error);
    }
}
