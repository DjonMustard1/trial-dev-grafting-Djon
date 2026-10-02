package dev.djon.grafting.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Pure image helpers with no Minecraft dependencies, so they can be unit tested.
 */
public final class ArtProcessor {

    /** Card color used behind transparent pixels in text art. */
    public static final int CARD_RGB = 0x1A1A1F;

    private ArtProcessor() {
    }

    /**
     * Scales an image to fit inside a size x size square, keeping its aspect ratio,
     * centered on a transparent background.
     */
    public static BufferedImage fitToSquare(BufferedImage source, int size) {
        return fitToBox(source, size, size);
    }

    /**
     * Scales an image to fit inside a boxWidth x boxHeight box, keeping its aspect ratio,
     * centered on a transparent background.
     */
    public static BufferedImage fitToBox(BufferedImage source, int boxWidth, int boxHeight) {
        if (boxWidth <= 0 || boxHeight <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        double scale = Math.min((double) boxWidth / source.getWidth(), (double) boxHeight / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage out = new BufferedImage(boxWidth, boxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, (boxWidth - width) / 2, (boxHeight - height) / 2, width, height, null);
        g.dispose();
        return out;
    }

    /**
     * Tile grid for art at a given size: the longer side of the picture spans {@code size}
     * blocks and the shorter side as many blocks as its aspect ratio needs (at least 1).
     * Returns {columns, rows}.
     */
    public static int[] grid(int imageWidth, int imageHeight, int size) {
        if (imageWidth >= imageHeight) {
            int rows = (int) Math.round((double) size * imageHeight / imageWidth);
            return new int[]{size, Math.max(1, Math.min(size, rows))};
        }
        int columns = (int) Math.round((double) size * imageWidth / imageHeight);
        return new int[]{Math.max(1, Math.min(size, columns)), size};
    }

    /**
     * Shrinks an image to at most maxSize pixels on its longest side and returns
     * a grid of opaque RGB colors (row major). Transparent areas are blended onto
     * the card color, and colors are quantized so neighbors merge into fewer text runs.
     */
    public static int[][] toPixelGrid(BufferedImage source, int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        double scale = Math.min(1.0, Math.min((double) maxSize / source.getWidth(), (double) maxSize / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setColor(new Color(CARD_RGB));
        g.fillRect(0, 0, width, height);
        // Area averaging blends every source pixel into the result. Bilinear scaling only
        // samples a few pixels per output pixel, which turns big images into noise.
        g.drawImage(source.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();

        int[][] grid = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = quantize(small.getRGB(x, y) & 0xFFFFFF);
            }
        }
        return grid;
    }

    /**
     * Average color of a pixel grid. Used as the text display background so the thin
     * gaps the font leaves between pixel characters blend into the picture instead of
     * showing the world behind it as grid lines.
     */
    public static int averageColor(int[][] grid) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int[] row : grid) {
            for (int rgb : row) {
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
                n++;
            }
        }
        if (n == 0) {
            return CARD_RGB;
        }
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /** Rounds each channel to 16 levels (0x00, 0x11, ... 0xFF). */
    static int quantize(int rgb) {
        int r = Math.round(((rgb >> 16) & 0xFF) / 17f) * 17;
        int gr = Math.round(((rgb >> 8) & 0xFF) / 17f) * 17;
        int b = Math.round((rgb & 0xFF) / 17f) * 17;
        return (r << 16) | (gr << 8) | b;
    }
}
