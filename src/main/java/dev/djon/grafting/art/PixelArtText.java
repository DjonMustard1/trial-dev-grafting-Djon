package dev.djon.grafting.art;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * Turns a pixel grid into a text component made of colored full block characters.
 * Neighboring pixels with the same color are merged into one run to keep packets small.
 */
public final class PixelArtText {

    /** Full block character used as one pixel. */
    public static final char PIXEL = '\u2588';

    private PixelArtText() {
    }

    public static Component fromGrid(int[][] grid) {
        TextComponent.Builder root = Component.text();
        for (int y = 0; y < grid.length; y++) {
            int[] row = grid[y];
            int x = 0;
            while (x < row.length) {
                int color = row[x];
                int end = x;
                while (end < row.length && row[end] == color) {
                    end++;
                }
                root.append(Component.text(String.valueOf(PIXEL).repeat(end - x), TextColor.color(color)));
                x = end;
            }
            if (y < grid.length - 1) {
                root.append(Component.newline());
            }
        }
        return root.build();
    }

    /** Number of text runs, used by tests to confirm merging works. */
    public static int countRuns(int[][] grid) {
        int runs = 0;
        for (int[] row : grid) {
            for (int x = 0; x < row.length; x++) {
                if (x == 0 || row[x] != row[x - 1]) {
                    runs++;
                }
            }
        }
        return runs;
    }
}
