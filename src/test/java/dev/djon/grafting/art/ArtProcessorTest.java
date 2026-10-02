package dev.djon.grafting.art;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtProcessorTest {

    @Test
    void tileGridFollowsAspectRatio() {
        assertEquals(java.util.List.of(4, 4), asList(ArtProcessor.grid(500, 500, 4)));
        assertEquals(java.util.List.of(4, 1), asList(ArtProcessor.grid(400, 100, 4)));
        assertEquals(java.util.List.of(2, 3), asList(ArtProcessor.grid(600, 900, 3)));
        assertEquals(java.util.List.of(1, 2), asList(ArtProcessor.grid(10, 1000, 2)), "never zero columns");
    }

    @Test
    void fitToBoxKeepsRequestedSize() {
        BufferedImage out = ArtProcessor.fitToBox(new BufferedImage(40, 10, BufferedImage.TYPE_INT_RGB), 512, 128);
        assertEquals(512, out.getWidth());
        assertEquals(128, out.getHeight());
    }

    private static java.util.List<Integer> asList(int[] values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    @Test
    void fitToSquareKeepsAspectRatioAndCenters() {
        BufferedImage wide = solid(200, 100, Color.RED);
        BufferedImage out = ArtProcessor.fitToSquare(wide, 128);

        assertEquals(128, out.getWidth());
        assertEquals(128, out.getHeight());
        // Top band is empty padding, middle row is filled.
        assertEquals(0, out.getRGB(64, 5) >>> 24, "padding should be transparent");
        assertEquals(255, out.getRGB(64, 64) >>> 24, "image area should be opaque");
    }

    @Test
    void pixelGridRespectsMaxSize() {
        int[][] grid = ArtProcessor.toPixelGrid(solid(400, 100, Color.BLUE), 32);
        assertEquals(8, grid.length);
        assertEquals(32, grid[0].length);
    }

    @Test
    void smallImagesAreNotUpscaled() {
        int[][] grid = ArtProcessor.toPixelGrid(solid(10, 6, Color.BLUE), 32);
        assertEquals(6, grid.length);
        assertEquals(10, grid[0].length);
    }

    @Test
    void transparentPixelsBecomeCardColor() {
        BufferedImage clear = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        int[][] grid = ArtProcessor.toPixelGrid(clear, 32);
        assertEquals(ArtProcessor.quantize(ArtProcessor.CARD_RGB), grid[0][0]);
    }

    @Test
    void quantizeSnapsToSixteenLevels() {
        assertEquals(0x000000, ArtProcessor.quantize(0x050505));
        assertEquals(0xFFFFFF, ArtProcessor.quantize(0xFAFAFA));
        assertEquals(0x110000, ArtProcessor.quantize(0x120000));
    }

    @Test
    void invalidSizesAreRejected() {
        BufferedImage img = solid(4, 4, Color.RED);
        assertThrows(IllegalArgumentException.class, () -> ArtProcessor.fitToSquare(img, 0));
        assertThrows(IllegalArgumentException.class, () -> ArtProcessor.toPixelGrid(img, -1));
    }

    @Test
    void averageColorBlendsAllPixels() {
        int[][] grid = {{0xFF0000, 0x0000FF}, {0xFF0000, 0x0000FF}};
        assertEquals(0x7F007F, ArtProcessor.averageColor(grid));
    }

    @Test
    void downscalingKeepsAverageColorOfDetailedImages() {
        // A fine checkerboard should shrink to gray, not to random black or white pixels.
        BufferedImage checker = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 400; x++) {
                checker.setRGB(x, y, ((x + y) & 1) == 0 ? 0xFFFFFF : 0x000000);
            }
        }
        int[][] grid = ArtProcessor.toPixelGrid(checker, 40);
        int gray = (grid[20][20] >> 16) & 0xFF;
        assertTrue(gray > 0x60 && gray < 0xA0, "expected mid gray but was " + Integer.toHexString(grid[20][20]));
    }

    @Test
    void textArtHasOnePixelCharacterPerPixelAndMergesRuns() {
        int[][] grid = {
                {0xFF0000, 0xFF0000, 0x0000FF},
                {0x00FF00, 0x00FF00, 0x00FF00},
        };
        String plain = PlainTextComponentSerializer.plainText().serialize(PixelArtText.fromGrid(grid));
        String px = String.valueOf(PixelArtText.PIXEL);
        assertEquals(px.repeat(3) + "\n" + px.repeat(3), plain);
        assertEquals(3, PixelArtText.countRuns(grid));
    }

    private static BufferedImage solid(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }
}
