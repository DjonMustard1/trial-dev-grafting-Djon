package dev.djon.grafting;

import dev.djon.grafting.art.Fanart;
import dev.djon.grafting.art.FanartLibrary;
import dev.djon.grafting.art.FanartMaps;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies and removes grafts. Every entity the plugin spawns is tagged and marked
 * non-persistent, so grafts never survive a server restart.
 */
public final class GraftManager {

    /** Rough height in blocks of one line of text in a text display at scale 1. */
    private static final float TEXT_LINE_HEIGHT = 0.25f;

    private final FanartLibrary library;
    private final FanartMaps maps = new FanartMaps();
    private final NamespacedKey graftKey;
    private final GraftSettings settings;

    /** Block grafts: anchor block to the frame on each grafted face. */
    private final Map<BlockKey, Map<BlockFace, UUID>> blockGrafts = new HashMap<>();
    /** Frames placed on blocks, pointing back to the block they are grafted onto. */
    private final Map<UUID, BlockKey> frameToBlock = new HashMap<>();

    public GraftManager(FanartLibrary library, NamespacedKey graftKey, GraftSettings settings) {
        this.library = library;
        this.graftKey = graftKey;
        this.settings = settings;
    }

    public FanartLibrary library() {
        return library;
    }

    /** True if the entity was spawned by this plugin. */
    public boolean isGraftEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(graftKey);
    }

    // ---------------------------------------------------------------- blocks

    /** Result of trying to graft a block face. */
    public enum BlockResult { GRAFTED, NO_ART, NO_SPACE }

    public BlockResult graftBlock(Block block, BlockFace face) {
        Fanart art = library.random();
        if (art == null) {
            return BlockResult.NO_ART;
        }
        BlockKey key = BlockKey.of(block);
        ItemStack mapItem = mapItem(art, block.getWorld());

        // Same face grafted again: swap the art in place.
        ItemFrame existing = existingFrame(key, face, block.getWorld());
        if (existing != null) {
            existing.setItem(mapItem, false);
            GraftEffects.graftApplied(existing.getLocation());
            return BlockResult.GRAFTED;
        }

        Block front = block.getRelative(face);
        if (!front.isPassable()) {
            return BlockResult.NO_SPACE;
        }

        ItemFrame frame;
        try {
            frame = block.getWorld().spawn(front.getLocation(), ItemFrame.class, spawned -> {
                spawned.setFacingDirection(face, true);
                setupFrame(spawned, mapItem);
            });
        } catch (IllegalArgumentException e) {
            return BlockResult.NO_SPACE;
        }

        blockGrafts.computeIfAbsent(key, k -> new EnumMap<>(BlockFace.class)).put(face, frame.getUniqueId());
        frameToBlock.put(frame.getUniqueId(), key);
        GraftEffects.graftApplied(frame.getLocation());
        return BlockResult.GRAFTED;
    }

    /** Removes every graft attached to a block. Returns true if something was removed. */
    public boolean removeBlockGrafts(Block block) {
        Map<BlockFace, UUID> faces = blockGrafts.remove(BlockKey.of(block));
        if (faces == null) {
            return false;
        }
        for (UUID id : faces.values()) {
            frameToBlock.remove(id);
            Entity frame = block.getWorld().getEntity(id);
            if (frame != null) {
                GraftEffects.graftBroken(frame.getLocation());
                frame.remove();
            }
        }
        return true;
    }

    /** The block a grafted frame is attached to, or null if the frame is not a block graft. */
    public Block blockOfFrame(Entity frame) {
        BlockKey key = frameToBlock.get(frame.getUniqueId());
        return key == null ? null : key.toBlock(frame.getWorld());
    }

    private ItemFrame existingFrame(BlockKey key, BlockFace face, World world) {
        Map<BlockFace, UUID> faces = blockGrafts.get(key);
        if (faces == null || !faces.containsKey(face)) {
            return null;
        }
        Entity entity = world.getEntity(faces.get(face));
        return entity instanceof ItemFrame frame && frame.isValid() ? frame : null;
    }

    // ------------------------------------------------------------------ mobs

    /** Grafts art onto a mob, replacing any art it already has. Returns false if no art is loaded. */
    public boolean graftMob(Entity mob) {
        Fanart art = library.random();
        if (art == null) {
            return false;
        }
        removeRiders(mob);

        boolean attached = false;
        if (settings.mobMapArt()) {
            attached = attachFrameRider(mob, art);
        }
        if (!attached) {
            attachTextRider(mob, art, settings.mobSize(), false);
        }
        GraftEffects.graftApplied(mob.getLocation().add(0, mob.getHeight() / 2, 0));
        return true;
    }

    /** Experimental: an item frame riding the mob. Falls back to text art if the server refuses. */
    private boolean attachFrameRider(Entity mob, Fanart art) {
        ItemStack mapItem = mapItem(art, mob.getWorld());
        ItemFrame frame;
        try {
            frame = mob.getWorld().spawn(mob.getLocation(), ItemFrame.class,
                    spawned -> setupFrame(spawned, mapItem));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!mob.addPassenger(frame)) {
            frame.remove();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ projectiles

    public boolean graftProjectile(Entity projectile) {
        Fanart art = library.random();
        if (art == null) {
            return false;
        }
        attachTextRider(projectile, art, settings.projectileSize(), true);
        GraftEffects.projectileGrafted(projectile.getLocation());
        return true;
    }

    // ---------------------------------------------------------------- shared

    /** Removes all graft entities riding this entity. Returns true if any were removed. */
    public boolean removeRiders(Entity vehicle) {
        boolean removed = false;
        for (Entity passenger : new ArrayList<>(vehicle.getPassengers())) {
            if (isGraftEntity(passenger)) {
                passenger.remove();
                removed = true;
            }
        }
        return removed;
    }

    /** Removes every graft entity in the given worlds and forgets all block grafts. */
    public int clearAll(List<World> worlds) {
        int count = 0;
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (isGraftEntity(entity)) {
                    entity.remove();
                    count++;
                }
            }
        }
        blockGrafts.clear();
        frameToBlock.clear();
        return count;
    }

    /** Called after the fanart library is reloaded, so stale maps are rebuilt. */
    public void onLibraryReloaded() {
        maps.clear();
    }

    private void attachTextRider(Entity vehicle, Fanart art, float sizeInBlocks, boolean centered) {
        float scale = sizeInBlocks / (art.textHeight() * TEXT_LINE_HEIGHT);
        float lift = centered ? -sizeInBlocks / 2f : 0.1f;
        TextDisplay display = vehicle.getWorld().spawn(vehicle.getLocation(), TextDisplay.class, spawned -> {
            tag(spawned);
            spawned.text(art.textArt());
            spawned.setLineWidth(Integer.MAX_VALUE / 2);
            spawned.setBackgroundColor(Color.fromARGB(0));
            spawned.setDefaultBackground(false);
            spawned.setShadowed(false);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(new Transformation(
                    new Vector3f(0, lift, 0),
                    new AxisAngle4f(),
                    new Vector3f(scale, scale * settings.textYStretch(), scale),
                    new AxisAngle4f()));
        });
        vehicle.addPassenger(display);
    }

    private void setupFrame(ItemFrame frame, ItemStack mapItem) {
        tag(frame);
        frame.setItem(mapItem, false);
        frame.setVisible(false);
        frame.setFixed(true);
        frame.setInvulnerable(true);
    }

    private void tag(Entity entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(graftKey, PersistentDataType.BOOLEAN, true);
    }

    private ItemStack mapItem(Fanart art, World world) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        item.editMeta(MapMeta.class, meta -> meta.setMapView(maps.mapFor(art, world)));
        return item;
    }

    /** Lightweight block position key that does not hold a reference to the world. */
    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        Block toBlock(World world) {
            return world.getBlockAt(x, y, z);
        }
    }
}
