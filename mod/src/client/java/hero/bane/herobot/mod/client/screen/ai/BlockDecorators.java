package hero.bane.herobot.mod.client.screen.ai;

import hero.bane.herobot.mod.common.ai.AiScript;
import hero.bane.herobot.mod.common.ai.block.BlockInstance;
import hero.bane.herobot.mod.common.ai.block.BlockType;
import hero.bane.herobot.mod.common.ai.block.EffectiveSlots;
import net.minecraft.client.gui.Font;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

// Per-BlockType layout extras that BlockRenderer used to branch on inline.
// To give a block its own expander/chip/suffix, register it here instead of adding a type check in BlockRenderer.
final class BlockDecorators {
    private BlockDecorators() {}

    @FunctionalInterface
    interface ButtonDecorator {
        void apply(BlockRenderer.Layout L, BlockInstance inst, Font font, int ox, int oy, AiScript script);
    }

    @FunctionalInterface
    interface SuffixDecorator {
        int apply(BlockRenderer.Layout L, int cx, int contentRight, Font font, BlockInstance inst, AiScript script);
    }

    private static final Map<BlockType, ButtonDecorator> BUTTONS = new EnumMap<>(BlockType.class);
    private static final Map<BlockType, SuffixDecorator> SUFFIXES = new EnumMap<>(BlockType.class);

    static {
        onButtons(EffectiveSlots::isLookBlock, expander(EffectiveSlots::isLookExpanded));
        onButtons(EffectiveSlots::sensorTakesTarget, expander(EffectiveSlots::isSensorTargetShown));
        onButtons(EffectiveSlots::sendTakesOp, expander(EffectiveSlots::isOpShown));
        onButtons(EffectiveSlots::isCalcBlock, BlockDecorators::calcButtons);
        onButtons(EffectiveSlots::isLoopBlock, BlockDecorators::loopIterChip);
        onButtons(EffectiveSlots::isVarBlock, BlockDecorators::cycleButton);
        BUTTONS.put(BlockType.FUNC_DEFINE, BlockRenderer::layoutParamColumns);
        BUTTONS.put(BlockType.ON_MESSAGE, BlockDecorators::messageChip);

        SUFFIXES.put(BlockType.ELSE_IF, (L, cx, contentRight, font, inst, script) -> {
            L.suffix = ")";
            L.suffixX = cx - 2;
            return L.suffixX + font.width(")");
        });
        SuffixDecorator tickSuffix = (L, cx, contentRight, font, inst, script) -> {
            L.suffix = BlockRenderer.tickWord(inst);
            L.suffixX = cx;
            return cx + font.width(L.suffix);
        };
        SUFFIXES.put(BlockType.WAIT, tickSuffix);
        SUFFIXES.put(BlockType.EVERY_X_TICKS, tickSuffix);
        SUFFIXES.put(BlockType.BREAK, (L, cx, contentRight, font, inst, script) -> {
            L.suffix = BlockRenderer.loopWord(inst);
            L.suffixX = cx;
            return cx + font.width(L.suffix);
        });
        SUFFIXES.put(BlockType.LOOP_ITER, (L, cx, contentRight, font, inst, script) -> {
            int loopNo = EffectiveSlots.loopDisplayId(script, inst.pairedId());
            L.suffix = loopNo <= 0 ? "?" : String.valueOf(loopNo);
            L.suffixX = contentRight;
            return contentRight + font.width(L.suffix);
        });
    }

    static ButtonDecorator buttonsFor(BlockType type) {
        return BUTTONS.get(type);
    }

    static SuffixDecorator suffixFor(BlockType type) {
        return SUFFIXES.get(type);
    }

    private static void onButtons(Predicate<BlockType> matcher, ButtonDecorator decorator) {
        for (BlockType t : BlockType.values()) {
            if (matcher.test(t)) BUTTONS.put(t, decorator);
        }
    }

    private static ButtonDecorator expander(Predicate<BlockInstance> shown) {
        return (L, inst, font, ox, oy, script) -> {
            L.w += 13;
            L.expander = new int[]{ox + L.w - 12, oy + 3, 9, 9};
            L.expanderMinus = shown.test(inst);
        };
    }

    private static void calcButtons(BlockRenderer.Layout L, BlockInstance inst, Font font, int ox, int oy, AiScript script) {
        boolean canRemove = EffectiveSlots.calcInputCount(inst) > 0;
        L.w += canRemove ? 26 : 13;
        L.plus = new int[]{ox + L.w - 12, oy + 3, 9, 9};
        if (canRemove) {
            L.expander = new int[]{ox + L.w - 24, oy + 3, 9, 9};
            L.expanderMinus = true;
        }
    }

    private static void loopIterChip(BlockRenderer.Layout L, BlockInstance inst, Font font, int ox, int oy, AiScript script) {
        boolean shown = EffectiveSlots.isLoopIterShown(script, inst);
        L.iterId = EffectiveSlots.loopDisplayId(script, inst.id());
        if (shown) {
            String tag = BlockRenderer.iterTag(L.iterId);
            int tw = font.width(tag);
            L.w += 15 + tw;
            L.iterChip = new int[]{ox + L.w - 12 - tw - 2, oy + 3, tw + 3, 9};
        } else {
            L.w += 13;
        }
        L.expander = new int[]{ox + L.w - 12, oy + 3, 9, 9};
        L.expanderMinus = shown;
    }

    private static void messageChip(BlockRenderer.Layout L, BlockInstance inst, Font font, int ox, int oy, AiScript script) {
        String chip = "message";
        int cw = font.width(chip) + 8;
        int chipX = ox + L.w - BlockRenderer.PAD + 4;
        int chipY = oy + (L.h - BlockRenderer.CHIP_H) / 2;
        L.msgChip = new int[]{chipX, chipY, cw, BlockRenderer.CHIP_H};
        L.w = (chipX + cw - ox) + BlockRenderer.PAD;
    }

    private static void cycleButton(BlockRenderer.Layout L, BlockInstance inst, Font font, int ox, int oy, AiScript script) {
        L.w += 12;
        L.cycleButton = new int[]{ox + L.w - 12, oy + 3, 9, 9};
    }
}
