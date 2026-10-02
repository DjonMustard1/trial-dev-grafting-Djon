package dev.djon.grafting.art;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditsTest {

    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path dir;

    @Test
    void creditsAreMatchedToImagesByFileNameIgnoringCase() throws IOException {
        image("Fors art.jpg", "jpg");
        image("audrey.png", "png");
        Files.writeString(dir.resolve("credits.yml"), """
                "fors ART.jpg":
                  artist: Jane Doe
                  social: "@janedoe on X"
                  link: https://x.com/janedoe
                """);

        FanartLibrary library = new FanartLibrary(dir.toFile(), LOG, 16);
        library.load();
        Map<String, Credit> byName = new java.util.HashMap<>();
        library.entries().forEach(a -> byName.put(a.name(), a.credit()));

        assertEquals(new Credit("Jane Doe", "@janedoe on X", "https://x.com/janedoe"), byName.get("Fors art"));
        assertEquals(Credit.UNKNOWN, byName.get("audrey"));
    }

    @Test
    void missingCreditsFileMeansUnknownArtist() throws IOException {
        image("a.png", "png");
        FanartLibrary library = new FanartLibrary(dir.toFile(), LOG, 16);
        library.load();
        assertEquals("Unknown artist", library.entries().get(0).credit().artist());
    }

    @Test
    void creditsFileIsNotLoadedAsAnImage() throws IOException {
        image("a.png", "png");
        Files.writeString(dir.resolve("credits.yml"), "\"a.png\":\n  artist: A\n");
        assertEquals(1, new FanartLibrary(dir.toFile(), LOG, 16).load());
    }

    @Test
    void blankFieldsAreNormalized() {
        Credit credit = new Credit("  ", null, " ");
        assertEquals("Unknown artist", credit.artist());
        assertFalse(credit.hasSocial());
        assertFalse(credit.hasLink());
    }

    @Test
    void onlyHttpLinksAreClickable() {
        assertTrue(new Credit("A", "", "https://x.com/a").hasLink());
        assertFalse(new Credit("A", "", "javascript:alert(1)").hasLink());
        assertFalse(new Credit("A", "", "x.com/a").hasLink());
    }

    @Test
    void chatLineShowsNameAndSocial() {
        String plain = plain(dev.djon.grafting.Messages.credit(new Credit("Jane Doe", "@janedoe on X", "")));
        assertEquals("Art by Jane Doe (@janedoe on X)", plain);
    }

    @Test
    void chatLineWithOnlyNameHasNoBrackets() {
        assertEquals("Art by Jane Doe", plain(dev.djon.grafting.Messages.credit(new Credit("Jane Doe", "", ""))));
    }

    @Test
    void chatLineFallsBackToLinkTextAndMakesItClickable() {
        Component line = dev.djon.grafting.Messages.credit(new Credit("Jane Doe", "", "https://x.com/janedoe"));
        assertEquals("Art by Jane Doe (https://x.com/janedoe)", plain(line));
        ClickEvent click = findClick(line);
        assertEquals(ClickEvent.Action.OPEN_URL, click.action());
    }

    @Test
    void unsafeLinkIsNotClickable() {
        Component line = dev.djon.grafting.Messages.credit(new Credit("A", "@a", "javascript:alert(1)"));
        assertNull(findClick(line));
    }

    private static ClickEvent findClick(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent found = findClick(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private void image(String name, String format) throws IOException {
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, dir.resolve(name).toFile());
    }
}
