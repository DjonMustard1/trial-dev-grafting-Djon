package dev.djon.grafting.art;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Creates one map per fanart and reuses it for every graft, so the server does not
 * create a new map for each click. Keyed by identity so two files with the same
 * name (for example art.png and art.jpg) never share a map.
 */
public final class FanartMaps {

    private final Map<Fanart, MapView> maps = new IdentityHashMap<>();

    public MapView mapFor(Fanart art, World world) {
        return maps.computeIfAbsent(art, key -> {
            MapView view = Bukkit.createMap(world);
            view.getRenderers().forEach(view::removeRenderer);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.addRenderer(new ImageRenderer(art));
            return view;
        });
    }

    public void clear() {
        maps.clear();
    }

    /** Draws the fanart once; the canvas is shared because the renderer is not contextual. */
    private static final class ImageRenderer extends MapRenderer {
        private final Fanart art;
        private boolean drawn;

        ImageRenderer(Fanart art) {
            super(false);
            this.art = art;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (drawn) {
                return;
            }
            canvas.drawImage(0, 0, art.mapImage());
            drawn = true;
        }
    }
}
