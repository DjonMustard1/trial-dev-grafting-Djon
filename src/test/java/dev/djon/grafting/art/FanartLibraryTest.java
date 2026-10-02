package dev.djon.grafting.art;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FanartLibraryTest {

    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path dir;

    @Test
    void emptyFolderLoadsNothing() {
        FanartLibrary library = new FanartLibrary(dir.toFile(), LOG, 32);
        assertEquals(0, library.load());
        assertTrue(library.isEmpty());
        assertNull(library.random());
    }

    @Test
    void missingFolderIsCreated() {
        File folder = dir.resolve("fanart").toFile();
        FanartLibrary library = new FanartLibrary(folder, LOG, 32);
        library.load();
        assertTrue(folder.isDirectory());
    }

    @Test
    void loadsSupportedImagesAndSkipsOthers() throws IOException {
        write("fool.png", "png", 300, 200, Color.RED);
        write("castle.jpg", "jpg", 64, 64, Color.BLUE);
        Files.writeString(dir.resolve("notes.txt"), "not an image");
        Files.writeString(dir.resolve("broken.png"), "not really a png");

        FanartLibrary library = new FanartLibrary(dir.toFile(), LOG, 32);
        assertEquals(2, library.load());
        assertEquals(Set.of("castle", "fool"),
                Set.copyOf(library.entries().stream().map(Fanart::name).toList()));
    }

    @Test
    void processedArtHasExpectedSizes() {
        Fanart art = FanartLibrary.process("wide", image(300, 150, Color.GREEN), 32);
        assertEquals(FanartLibrary.MAP_SIZE, art.mapImage().getWidth());
        assertEquals(FanartLibrary.MAP_SIZE, art.mapImage().getHeight());
        assertEquals(32, art.textWidth());
        assertEquals(16, art.textHeight());
        assertNotNull(art.textArt());
    }

    @Test
    void randomNeverRepeatsBackToBackWhenThereIsAChoice() throws IOException {
        write("a.png", "png", 8, 8, Color.RED);
        write("b.png", "png", 8, 8, Color.BLUE);
        write("c.png", "png", 8, 8, Color.GREEN);
        FanartLibrary library = new FanartLibrary(dir.toFile(), LOG, 32);
        library.load();

        Set<String> seen = new HashSet<>();
        Fanart previous = library.random();
        for (int i = 0; i < 200; i++) {
            Fanart next = library.random();
            assertNotEquals(previous, next);
            seen.add(next.name());
            previous = next;
        }
        assertEquals(3, seen.size(), "every fanart should be picked eventually");
    }

    @Test
    void singleFanartIsAlwaysReturned() throws IOException {
        write("only.png", "png", 8, 8, Color.RED);
        FanartLibrary library = new FanartLibrary(dir.toFile(), LOG, 32);
        library.load();
        assertEquals("only", library.random().name());
        assertEquals("only", library.random().name());
    }

    @Test
    void extensionCheckIsCaseInsensitive() {
        assertTrue(FanartLibrary.isSupported("ART.PNG"));
        assertTrue(FanartLibrary.isSupported("art.jpeg"));
        assertFalse(FanartLibrary.isSupported("art.psd"));
    }

    private void write(String name, String format, int w, int h, Color color) throws IOException {
        ImageIO.write(image(w, h, color), format, dir.resolve(name).toFile());
    }

    private static BufferedImage image(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }
}
