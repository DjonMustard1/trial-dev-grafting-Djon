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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies and removes grafts.
 * <p>
 * Every entity the plugin spawns is tagged and marked non-persistent, so nothing is
 * written to disk and a restart wipes all grafts. While the server is running, the
 * art for each target is remembered so it can be restored when a chunk reloads.
 */
public final class GraftManager {

    /** Height in blocks of one line of text in a text display at scale 1. */
    private static final float TEXT_LINE_HEIGHT = 0.25f;

    private final FanartLibrary library;
    private final FanartMaps maps = new FanartMaps();
    private final NamespacedKey graftKey;
    private final GraftSettings settings;

    /** Block grafts: anchor block to the art on each grafted face. */
    private final Map<BlockKey, Map<BlockFace, BlockGraft>> blockGrafts = new HashMap<>();
    /** Mob and projectile grafts: target entity to its art. */
    private final Map<UUID, RiderGraft> riderGrafts = new HashMap<>();

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
        if (block.isPassable()) {
            return BlockResult.NO_SPACE;
        }
        Map<BlockFace, BlockGraft> faces = blockGrafts.get(BlockKey.of(block));
        BlockGraft existing = faces == null ? null : faces.get(face);
        ItemFrame frame = existing == null ? null : liveFrame(block.getWorld(), existing.frameId());

        if (frame != null) {
            // Same face grafted again: swap the art in place.
            frame.setItem(mapItem(art, block.getWorld()), false);
        } else {
            frame = spawnBlockFrame(block, face, art);
            if (frame == null) {
                return BlockResult.NO_SPACE;
            }
        }
        blockGrafts.computeIfAbsent(BlockKey.of(block), k -> new EnumMap<>(BlockFace.class))
                .put(face, new BlockGraft(frame.getUniqueId(), art));
        GraftEffects.graftApplied(frame.getLocation());
        return BlockResult.GRAFTED;
    }

    /** Removes every graft attached to a block. */
    public void removeBlockGrafts(Block block) {
        Map<BlockFace, BlockGraft> faces = blockGrafts.remove(BlockKey.of(block));
        if (faces == null) {
            return;
        }
        for (BlockGraft graft : faces.values()) {
            Entity frame = block.getWorld().getEntity(graft.frameId());
            if (frame != null) {
                GraftEffects.graftBroken(frame.getLocation());
                frame.remove();
            }
        }
    }

    /** The block a grafted frame is attached to, or null if the frame is not a block graft. */
    public Block blockOfFrame(Entity frame) {
        for (Map.Entry<BlockKey, Map<BlockFace, BlockGraft>> entry : blockGrafts.entrySet()) {
            for (BlockGraft graft : entry.getValue().values()) {
                if (graft.frameId().equals(frame.getUniqueId())) {
                    return entry.getKey().toBlock(frame.getWorld());
                }
            }
        }
        return null;
    }

    /**
     * Keeps block grafts in sync with the world. Runs every second.
     * Removes grafts whose block disappeared (pistons, water, falling blocks, anything)
     * and restores frames that vanished because their chunk was unloaded.
     */
    public void validateBlockGrafts(List<World> worlds) {
        Map<UUID, World> byId = new HashMap<>();
        worlds.forEach(world -> byId.put(world.getUID(), world));

        Iterator<Map.Entry<BlockKey, Map<BlockFace, BlockGraft>>> blocks = blockGrafts.entrySet().iterator();
        while (blocks.hasNext()) {
            Map.Entry<BlockKey, Map<BlockFace, BlockGraft>> entry = blocks.next();
            BlockKey key = entry.getKey();
            World world = byId.get(key.world());
            if (world == null) {
                blocks.remove();
                continue;
            }
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            Block block = key.toBlock(world);
            if (block.isPassable()) {
                entry.getValue().values().forEach(graft -> removeEntity(world, graft.frameId(), true));
                blocks.remove();
                continue;
            }
            for (Map.Entry<BlockFace, BlockGraft> face : entry.getValue().entrySet()) {
                if (liveFrame(world, face.getValue().frameId()) == null) {
                    ItemFrame frame = spawnBlockFrame(block, face.getKey(), face.getValue().art());
                    if (frame != null) {
                        face.setValue(new BlockGraft(frame.getUniqueId(), face.getValue().art()));
                    }
                }
            }
            entry.getValue().values().removeIf(graft -> liveFrame(world, graft.frameId()) == null);
            if (entry.getValue().isEmpty()) {
                blocks.remove();
            }
        }
    }

    private ItemFrame spawnBlockFrame(Block block, BlockFace face, Fanart art) {
        Block front = block.getRelative(face);
        if (!front.isPassable()) {
            return null;
        }
        ItemStack mapItem = mapItem(art, block.getWorld());
        try {
            return block.getWorld().spawn(front.getLocation(), ItemFrame.class, spawned -> {
                spawned.setFacingDirection(face, true);
                setupFrame(spawned, mapItem);
            });
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ItemFrame liveFrame(World world, UUID id) {
        Entity entity = world.getEntity(id);
        return entity instanceof ItemFrame frame && frame.isValid() ? frame : null;
    }

    // ---------------------------------------------------- mobs and projectiles

    /** Grafts art onto a mob, replacing any art it already has. Returns false if no art is loaded. */
    public boolean graftMob(Entity mob) {
        Fanart art = library.random();
        if (art == null) {
            return false;
        }
        attachRider(mob, new RiderGraft(art, false));
        GraftEffects.graftApplied(mob.getLocation().add(0, mob.getHeight() / 2, 0));
        return true;
    }

    public boolean graftProjectile(Entity projectile) {
        Fanart art = library.random();
        if (art == null) {
            return false;
        }
        attachRider(projectile, new RiderGraft(art, true));
        GraftEffects.projectileGrafted(projectile.getLocation());
        return true;
    }

    /** The target has been destroyed: remove its art and forget it. */
    public boolean forgetTarget(Entity target) {
        boolean known = riderGrafts.remove(target.getUniqueId()) != null;
        return removeRiders(target) || known;
    }

    /** Restores art on a target whose chunk was reloaded. */
    public void restore(Entity target) {
        RiderGraft graft = riderGrafts.get(target.getUniqueId());
        if (graft != null && !hasGraftRider(target)) {
            attachRider(target, graft);
        }
    }

    private void attachRider(Entity target, RiderGraft graft) {
        removeRiders(target);
        boolean attached = !graft.projectile() && settings.mobMapArt() && attachFrameRider(target, graft.art());
        if (!attached) {
            attached = attachTextRider(target, graft.art(),
                    graft.projectile() ? settings.projectileSize() : settings.mobSize(), graft.projectile());
        }
        if (attached) {
            riderGrafts.put(target.getUniqueId(), graft);
        }
    }

    /** Experimental: an item frame riding the mob. Returns false so the caller can fall back. */
    private boolean attachFrameRider(Entity mob, Fanart art) {
        ItemStack mapItem = mapItem(art, mob.getWorld());
        ItemFrame frame;
        try {
            frame = mob.getWorld().spawn(mob.getLocation(), ItemFrame.class, spawned -> setupFrame(spawned, mapItem));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!mob.addPassenger(frame)) {
            frame.remove();
            return false;
        }
        return true;
    }

    private boolean attachTextRider(Entity vehicle, Fanart art, float sizeInBlocks, boolean centered) {
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
        if (!vehicle.addPassenger(display)) {
            display.remove();
            return false;
        }
        return true;
    }

    private boolean hasGraftRider(Entity vehicle) {
        return vehicle.getPassengers().stream().anyMatch(this::isGraftEntity);
    }

    private boolean removeRiders(Entity vehicle) {
        boolean removed = false;
        for (Entity passenger : new ArrayList<>(vehicle.getPassengers())) {
            if (isGraftEntity(passenger)) {
                passenger.remove();
                removed = true;
            }
        }
        return removed;
    }

    // ---------------------------------------------------------------- shared

    /** Removes every graft entity in the given worlds and forgets all grafts. */
    public int clearAll(List<World> worlds) {
        blockGrafts.clear();
        riderGrafts.clear();
        int count = 0;
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (isGraftEntity(entity)) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }

    /** Called after the fanart library is reloaded, so maps are rebuilt from the new images. */
    public void onLibraryReloaded() {
        maps.clear();
    }

    private void removeEntity(World world, UUID id, boolean withEffect) {
        Entity entity = world.getEntity(id);
        if (entity != null) {
            if (withEffect) {
                GraftEffects.graftBroken(entity.getLocation());
            }
            entity.remove();
        }
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

    /** Block position key that does not hold a reference to the world. */
    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        Block toBlock(World world) {
            return world.getBlockAt(x, y, z);
        }
    }

    /** One grafted block face. */
    private record BlockGraft(UUID frameId, Fanart art) {
    }

    /** Art riding a mob or projectile. */
    private record RiderGraft(Fanart art, boolean projectile) {
    }
}
