package dev.djon.grafting;

import dev.djon.grafting.art.FanartLibrary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * All player facing text in one place.
 */
public final class Messages {

    public static final Component GRAFTED = Component.text("Grafted.", NamedTextColor.GRAY)
            .decorate(TextDecoration.ITALIC);
    public static final Component NO_SPACE = Component.text("There is no room on that face to graft.", NamedTextColor.RED);
    public static final Component PROJECTILES_ON = Component.text("Projectile grafting: ON", NamedTextColor.LIGHT_PURPLE);
    public static final Component PROJECTILES_OFF = Component.text("Projectile grafting: OFF", NamedTextColor.GRAY);

    private Messages() {
    }

    public static Component noArt(FanartLibrary library) {
        return Component.text("No fanart loaded. Put images in " + library.folder().getPath()
                + " and run /graft reload.", NamedTextColor.RED);
    }
}
