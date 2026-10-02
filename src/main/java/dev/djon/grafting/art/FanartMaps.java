package dev.djon.grafting.art;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Creates maps for fanart and reuses them for every graft, so the server does not
 * create a new map for each click. Wide grafts are split into a grid of 128 x 128
 * tiles, one map per tile. Keyed by identity so two files with the same name
 * (for example art.png and art.jpg) never share a map.
 */
public final class FanartMaps {

    private final Map<Fanart, Map<Integer, MapView>> maps = new IdentityHashMap<>();
    private final Map<Fanart, Map<Integer, BufferedImage>> scaled = new IdentityHashMap<>();

    /** The whole image as one map, used by single block grafts. */
    public MapView mapFor(Fanart art, World world) {
        return tileFor(art, 1, 0, 0, world);
    }

    /** Columns and rows of tiles for this art at a given size. See {@link ArtProcessor#grid}. */
    public static int[] gridFor(Fanart art, int width) {
        return width == 1 ? new int[]{1, 1}
                : ArtProcessor.grid(art.source().getWidth(), art.source().getHeight(), width);
    }

    /**
     * The map for one tile of the art's grid at a given size. Column 0 is the left edge
     * and row 0 is the top edge of the picture.
     */
    public MapView tileFor(Fanart art, int width, int column, int row, World world) {
        return maps.computeIfAbsent(art, key -> new HashMap<>()).computeIfAbsent(key(width, column, row), key -> {
            MapView view = Bukkit.createMap(world);
            view.getRenderers().forEach(view::removeRenderer);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.addRenderer(new ImageRenderer(tileImage(art, width, column, row)));
            return view;
        });
    }

    BufferedImage tileImage(Fanart art, int width, int column, int row) {
        if (width == 1) {
            return art.mapImage();
        }
        int size = FanartLibrary.MAP_SIZE;
        BufferedImage full = scaled.computeIfAbsent(art, key -> new HashMap<>()).computeIfAbsent(width, w -> {
            int[] grid = gridFor(art, w);
            return ArtProcessor.fitToBox(art.source(), grid[0] * size, grid[1] * size);
        });
        return full.getSubimage(column * size, row * size, size, size);
    }

    public void clear() {
        maps.clear();
        scaled.clear();
    }

    private static int key(int width, int column, int row) {
        return (width << 16) | (row << 8) | column;
    }

    /** Draws the image once; the canvas is shared because the renderer is not contextual. */
    private static final class ImageRenderer extends MapRenderer {
        private final BufferedImage image;
        private boolean drawn;

        ImageRenderer(BufferedImage image) {
            super(false);
            this.image = image;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (drawn) {
                return;
            }
            canvas.drawImage(0, 0, image);
            drawn = true;
        }
    }
}
