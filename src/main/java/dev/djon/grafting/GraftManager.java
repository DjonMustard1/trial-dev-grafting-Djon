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
import org.bukkit.entity.ItemDisplay;
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

    /** Result of trying to graft a block face. {@code art} is set only when grafted. */
    public record BlockResult(Status status, Fanart art) {
        public enum Status { GRAFTED, NO_ART, NO_SPACE }

        static final BlockResult NO_ART = new BlockResult(Status.NO_ART, null);
        static final BlockResult NO_SPACE = new BlockResult(Status.NO_SPACE, null);
    }

    public BlockResult graftBlock(Block block, BlockFace face) {
        return graftBlock(block, face, 1);
    }

    public BlockResult graftBlock(Block block, BlockFace face, int width) {
        checkWidth(width);
        Fanart art = library.random();
        if (art == null) {
            return BlockResult.NO_ART;
        }
        if (block.isPassable()) {
            return BlockResult.NO_SPACE;
        }
        Map<BlockFace, BlockGraft> faces = blockGrafts.get(BlockKey.of(block));
        BlockGraft existing = faces == null ? null : faces.get(face);
        Entity frame = existing == null || existing.width() != width ? null
                : liveBlockArt(block.getWorld(), existing.frameId(), width);
        if (frame != null) {
            // Same face grafted again: swap the art in place.
            if (frame instanceof ItemFrame itemFrame) itemFrame.setItem(mapItem(art, block.getWorld()), false);
            else ((ItemDisplay) frame).setItemStack(mapItem(art, block.getWorld()));
        } else {
            Entity replacement = spawnBlockArt(block, face, art, width);
            if (replacement != null && existing != null) removeEntity(block.getWorld(), existing.frameId(), false);
            frame = replacement;
            if (frame == null) {
                return BlockResult.NO_SPACE;
            }
        }
        blockGrafts.computeIfAbsent(BlockKey.of(block), k -> new EnumMap<>(BlockFace.class))
                .put(face, new BlockGraft(frame.getUniqueId(), art, width));
        GraftEffects.graftApplied(frame.getLocation());
        return new BlockResult(BlockResult.Status.GRAFTED, art);
    }

    /** Removes every graft attached to a block. */
    public void removeBlockGrafts(Block block) {
        Map<BlockFace, BlockGraft> faces = blockGrafts.remove(BlockKey.of(block));
        if (faces == null) {
            return;
        }
        for (BlockGraft graft : faces.values()) {
            Entity frame = findEntity(block.getWorld(), graft.frameId());
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

    /** Returns the attached face for either kind of block art, or null. */
    public BlockFace faceOfFrame(Entity artEntity) {
        for (Map.Entry<BlockKey, Map<BlockFace, BlockGraft>> entry : blockGrafts.entrySet()) {
            if (!entry.getKey().world().equals(artEntity.getWorld().getUID())) continue;
            for (Map.Entry<BlockFace, BlockGraft> face : entry.getValue().entrySet()) {
                if (face.getValue().frameId().equals(artEntity.getUniqueId())) return face.getKey();
            }
        }
        return null;
    }

    /** Resizes an existing face without choosing new art. Returns false if absent or blocked. */
    public boolean resizeBlock(Block block, BlockFace face, int width) {
        checkWidth(width);
        Map<BlockFace, BlockGraft> faces = blockGrafts.get(BlockKey.of(block));
        BlockGraft previous = faces == null ? null : faces.get(face);
        if (previous == null || block.isPassable()) return false;
        Entity current = previous.width() == width
                ? liveBlockArt(block.getWorld(), previous.frameId(), width) : null;
        if (current != null) return true;
        Entity replacement = spawnBlockArt(block, face, previous.art(), width);
        if (replacement == null) return false;
        removeEntity(block.getWorld(), previous.frameId(), false);
        faces.put(face, new BlockGraft(replacement.getUniqueId(), previous.art(), width));
        return true;
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
                if (liveBlockArt(world, face.getValue().frameId(), face.getValue().width()) == null) {
                    Entity frame = spawnBlockArt(block, face.getKey(), face.getValue().art(), face.getValue().width());
                    if (frame != null) {
                        face.setValue(new BlockGraft(frame.getUniqueId(), face.getValue().art(), face.getValue().width()));
                    }
                }
            }
            entry.getValue().values().removeIf(graft -> liveBlockArt(world, graft.frameId(), graft.width()) == null);
            if (entry.getValue().isEmpty()) {
                blocks.remove();
            }
        }
    }

    private Entity spawnBlockArt(Block block, BlockFace face, Fanart art, int width) {
        if (width == 1) return spawnBlockFrame(block, face, art);
        if (!block.getRelative(face).isPassable()) return null;
        org.bukkit.Location location = block.getLocation().clone().add(0.5 + face.getModX() * 0.501,
                0.5 + face.getModY() * 0.501, 0.5 + face.getModZ() * 0.501);
        // The FIXED map item model is half-scale, so double its transform for a one-block-tall plane.
        float yaw = switch (face) {
            case NORTH -> 180f;
            case EAST -> 90f;
            case WEST -> -90f;
            default -> 0f;
        };
        float pitch = face == BlockFace.UP ? -90f : face == BlockFace.DOWN ? 90f : 0f;
        location.setYaw(yaw);
        location.setPitch(pitch);
        try {
            return block.getWorld().spawn(location, ItemDisplay.class, spawned -> {
                tag(spawned);
                spawned.setItemStack(mapItem(art, block.getWorld()));
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(width * 2f, 2f, 2f), new AxisAngle4f()));
                spawned.setBrightness(new Display.Brightness(15, 15));
            });
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Entity liveBlockArt(World world, UUID id, int width) {
        Entity entity = world.getEntity(id);
        return entity != null && entity.isValid() &&
                (width == 1 ? entity instanceof ItemFrame : entity instanceof ItemDisplay) ? entity : null;
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

    /** Grafts art onto a mob, replacing any art it already has. Returns the art used, or null if none is loaded. */
    public Fanart graftMob(Entity mob) {
        return graftMob(mob, 1);
    }

    public Fanart graftMob(Entity mob, int width) {
        checkWidth(width);
        Fanart art = library.random();
        if (art == null) {
            return null;
        }
        if (!attachRider(mob, new RiderGraft(art, false, width))) return null;
        GraftEffects.graftApplied(mob.getLocation().add(0, mob.getHeight() / 2, 0));
        return art;
    }

    /** Grafts art onto a projectile. Returns the art used, or null if none is loaded. */
    public Fanart graftProjectile(Entity projectile) {
        return graftProjectile(projectile, 1);
    }

    public Fanart graftProjectile(Entity projectile, int width) {
        checkWidth(width);
        Fanart art = library.random();
        if (art == null) {
            return null;
        }
        if (!attachRider(projectile, new RiderGraft(art, true, width))) return null;
        GraftEffects.projectileGrafted(projectile.getLocation());
        return art;
    }

    /** Resizes an existing mob graft while preserving its art. */
    public boolean resizeMob(Entity mob, int width) {
        checkWidth(width);
        RiderGraft previous = riderGrafts.get(mob.getUniqueId());
        return previous != null && !previous.projectile()
                && attachRider(mob, new RiderGraft(previous.art(), false, width));
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

    private boolean attachRider(Entity target, RiderGraft graft) {
        removeRiders(target);
        boolean attached = attachTextRider(target, graft.art(),
                graft.projectile() ? settings.projectileSize() : settings.mobSize(), graft.projectile(), graft.width());
        if (attached) {
            riderGrafts.put(target.getUniqueId(), graft);
        }
        return attached;
    }

    private boolean attachTextRider(Entity vehicle, Fanart art, float sizeInBlocks, boolean centered, int width) {
        float scale = sizeInBlocks / (art.textHeight() * TEXT_LINE_HEIGHT);
        float lift = centered ? -sizeInBlocks / 2f : 0.1f;
        TextDisplay display;
        try {
            display = vehicle.getWorld().spawn(vehicle.getLocation(), TextDisplay.class, spawned -> {
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
                    new Vector3f(scale * width, scale * settings.textYStretch(), scale),
                    new AxisAngle4f()));
            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
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
        Entity entity = findEntity(world, id);
        if (entity != null) {
            if (withEffect) {
                GraftEffects.graftBroken(entity.getLocation());
            }
            entity.remove();
        }
    }

    private Entity findEntity(World world, UUID id) {
        Entity entity = world.getEntity(id);
        if (entity != null) return entity;
        // A newly spawned display can already be in the chunk before a UUID lookup sees it.
        return world.getEntities().stream().filter(candidate -> candidate.getUniqueId().equals(id))
                .findFirst().orElse(null);
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

    private static void checkWidth(int width) {
        if (width < 1 || width > 4) throw new IllegalArgumentException("width must be between 1 and 4");
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
    private record BlockGraft(UUID frameId, Fanart art, int width) {
    }

    /** Art riding a mob or projectile. */
    private record RiderGraft(Fanart art, boolean projectile, int width) {
    }
}
