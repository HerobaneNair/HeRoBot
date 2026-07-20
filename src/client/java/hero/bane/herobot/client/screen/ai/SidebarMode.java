package hero.bane.herobot.client.screen.ai;

public enum SidebarMode {
    MAXIMIZED, MINIMIZED, HOVER;

    public static SidebarMode fromName(String name) {
        try {
            return SidebarMode.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return MAXIMIZED;
        }
    }
}
