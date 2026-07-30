package hero.bane.herobot.util;

/**
 * Carries the {@code @z} flag from the parser to the selector it builds. Vanilla predicates are
 * context free, so dropping the command source can only happen once the source is known.
 */
public interface EntitySelectorExcludeSelf {
    void setExcludeSelf(boolean excludeSelf);

    boolean isExcludeSelf();
}
