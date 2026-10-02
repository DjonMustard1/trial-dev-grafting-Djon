package dev.djon.grafting;

import dev.djon.grafting.art.Credit;
import dev.djon.grafting.art.FanartLibrary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

    /**
     * Chat credit for the artist of a grafted piece, for example:
     * "Art by Jane Doe (@janedoe on X)". The social is clickable when a link is set.
     */
    public static Component credit(Credit credit) {
        TextComponent.Builder line = Component.text()
                .append(Component.text("Art by ", NamedTextColor.GRAY))
                .append(Component.text(credit.artist(), NamedTextColor.GOLD));
        if (credit.hasSocial() || credit.hasLink()) {
            String label = credit.hasSocial() ? credit.social() : credit.link();
            Component social = Component.text(label, NamedTextColor.AQUA);
            if (credit.hasLink()) {
                social = social.decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(credit.link()))
                        .hoverEvent(HoverEvent.showText(Component.text("Open " + credit.link(), NamedTextColor.GRAY)));
            }
            line.append(Component.text(" (", NamedTextColor.GRAY))
                    .append(social)
                    .append(Component.text(")", NamedTextColor.GRAY));
        }
        return line.build();
    }
}
