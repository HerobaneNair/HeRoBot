package hero.bane.herobot.mod.common.ai.expr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class ExprLog {
    private ExprLog() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("HeroBot/expr");
    private static final int LIMIT = 256;
    private static final Set<String> REPORTED = Collections.synchronizedSet(new LinkedHashSet<>());

    static void badExpression(String expression, String reason) {
        if (REPORTED.size() >= LIMIT) REPORTED.clear();
        if (!REPORTED.add(expression + " " + reason)) return;
        LOGGER.warn("Invalid expression \"{}\" ({}) - it will evaluate to a constant", expression,
                reason == null ? "parse error" : reason);
    }
}
