package dev.djon.grafting.art;

import java.awt.Color;
import java.awt.Graphics2D;
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
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        double scale = Math.min((double) size / source.getWidth(), (double) size / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, (size - width) / 2, (size - height) / 2, width, height, null);
        g.dispose();
        return out;
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
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();

        int[][] grid = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = quantize(small.getRGB(x, y) & 0xFFFFFF);
            }
        }
        return grid;
    }

    /** Rounds each channel to 16 levels (0x00, 0x11, ... 0xFF). */
    static int quantize(int rgb) {
        int r = Math.round(((rgb >> 16) & 0xFF) / 17f) * 17;
        int gr = Math.round(((rgb >> 8) & 0xFF) / 17f) * 17;
        int b = Math.round((rgb & 0xFF) / 17f) * 17;
        return (r << 16) | (gr << 8) | b;
    }
}
