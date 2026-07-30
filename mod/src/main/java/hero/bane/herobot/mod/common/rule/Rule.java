package hero.bane.herobot.mod.common.rule;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Rule {
    String desc();
}
