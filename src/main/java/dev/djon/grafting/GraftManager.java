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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies and removes grafts.
 * <p>
 * Block art is one invisible map item frame per block. Wider art is cut into a grid of
 * 128 x 128 map tiles, one frame per block, so it stays as sharp as the 1 x 1 version.
 * Every entity the plugin spawns is tagged and marked non-persistent, so nothing is
 * written to disk and a restart wipes all grafts. While the server is running, the
 * art for each target is remembered so it can be restored when a chunk reloads.
 */
public final class GraftManager {

    /** Largest graft, in blocks per side. */
    public static final int MAX_WIDTH = 4;

    /** Height in blocks of one line of text in a text display at scale 1. */
    private static final float TEXT_LINE_HEIGHT = 0.25f;

    private final FanartLibrary library;
    private final FanartMaps maps = new FanartMaps();
    private final NamespacedKey graftKey;
    private final GraftSettings settings;

    /** Block grafts, keyed by the clicked block and face. */
    private final Map<GraftId, BlockGraft> blockGrafts = new LinkedHashMap<>();
    /** Every block face covered by a block graft, to find and replace overlaps. */
    private final Map<TileKey, GraftId> coveredTiles = new HashMap<>();
    /** Frame entity to the block graft it belongs to. */
    private final Map<UUID, GraftId> frameOwners = new HashMap<>();
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

    /**
     * Result of trying to graft a block face. {@code art} is set only when grafted, and
     * {@code width} is the size actually used, which can be smaller than asked if the
     * wall was too small.
     */
    public record BlockResult(Status status, Fanart art, int width) {
        public enum Status { GRAFTED, NO_ART, NO_SPACE }

        static final BlockResult NO_ART = new BlockResult(Status.NO_ART, null, 0);
        static final BlockResult NO_SPACE = new BlockResult(Status.NO_SPACE, null, 0);
    }

    public BlockResult graftBlock(Block block, BlockFace face) {
        return graftBlock(block, face, 1);
    }

    /** Grafts random art onto a block face, as big as possible up to {@code width} blocks. */
    public BlockResult graftBlock(Block block, BlockFace face, int width) {
        return graftBlock(block, face, width, null);
    }

    /**
     * Grafts the given art (or random art if null) onto a block face, as big as possible
     * up to {@code width} blocks.
     */
    public BlockResult graftBlock(Block block, BlockFace face, int width, Fanart chosen) {
        checkWidth(width);
        Fanart art = chosen != null ? chosen : library.random();
        if (art == null) {
            return BlockResult.NO_ART;
        }
        GraftId id = new GraftId(BlockKey.of(block), face);
        BlockGraft existing = blockGrafts.get(id);
        if (existing != null && existing.width() == width && allFramesLive(block.getWorld(), existing)
                && java.util.Arrays.equals(FanartMaps.gridFor(existing.art(), width), FanartMaps.gridFor(art, width))) {
            // Same graft again: swap the art in place.
            BlockGraft swapped = new BlockGraft(art, width, new ArrayList<>());
            for (Tile tile : existing.tiles()) {
                ItemFrame frame = liveFrame(block.getWorld(), tile.frameId());
                frame.setItem(mapItem(art, width, tile.column(), tile.row(), block.getWorld()), false);
                swapped.tiles().add(tile);
            }
            blockGrafts.put(id, swapped);
            potionSwirl(block.getWorld(), face, swapped);
            GraftEffects.graftApplied(centerOf(block.getWorld(), swapped));
            return new BlockResult(BlockResult.Status.GRAFTED, art, width);
        }
        int placed = place(block, face, art, width);
        if (placed == 0) {
            return BlockResult.NO_SPACE;
        }
        potionSwirl(block.getWorld(), face, blockGrafts.get(id));
        GraftEffects.graftApplied(centerOf(block.getWorld(), blockGrafts.get(id)));
        return new BlockResult(BlockResult.Status.GRAFTED, art, placed);
    }

    /**
     * The blocks that art would cover if grafted here now, trying {@code width} and then
     * smaller sizes, exactly like {@link #graftBlock}. Empty if it cannot be grafted.
     */
    public List<Block> previewBlock(Block block, BlockFace face, int width, Fanart art) {
        checkWidth(width);
        if (art == null) {
            return List.of();
        }
        for (int size = width; size >= 1; size--) {
            List<PlannedTile> plan = plan(block, face, art, size);
            if (plan != null) {
                return plan.stream().map(PlannedTile::support).toList();
            }
        }
        return List.of();
    }

    private void potionSwirl(World world, BlockFace face, BlockGraft graft) {
        for (Tile tile : graft.tiles()) {
            GraftEffects.potionSwirlOnFace(new org.bukkit.Location(world,
                    tile.support().x() + 0.5, tile.support().y() + 0.5, tile.support().z() + 0.5), face);
        }
    }

    /**
     * Resizes the graft that covers this block face, keeping its art.
     * Returns the width used, or 0 if there is no graft there or it cannot be resized.
     */
    public int resizeBlock(Block block, BlockFace face, int width) {
        checkWidth(width);
        GraftId id = coveredTiles.get(TileKey.of(block, face));
        BlockGraft previous = id == null ? null : blockGrafts.get(id);
        if (previous == null) {
            return 0;
        }
        Block anchor = id.anchor().toBlock(block.getWorld());
        int placed = place(anchor, face, previous.art(), width);
        if (placed > 0) {
            potionSwirl(block.getWorld(), face, blockGrafts.get(id));
            GraftEffects.graftApplied(centerOf(block.getWorld(), blockGrafts.get(id)));
        }
        return placed;
    }

    /** Removes every graft that has art on this block. */
    public void removeBlockGrafts(Block block) {
        List<GraftId> touching = new ArrayList<>();
        for (BlockFace face : FACES) {
            GraftId id = coveredTiles.get(TileKey.of(block, face));
            if (id != null && !touching.contains(id)) {
                touching.add(id);
            }
        }
        for (GraftId id : touching) {
            removeGraft(block.getWorld(), id, true);
        }
    }

    /** The clicked block of the graft this frame belongs to, or null if it is not block art. */
    public Block blockOfFrame(Entity frame) {
        GraftId id = frameOwners.get(frame.getUniqueId());
        return id == null ? null : id.anchor().toBlock(frame.getWorld());
    }

    /** The face of the graft this frame belongs to, or null if it is not block art. */
    public BlockFace faceOfFrame(Entity frame) {
        GraftId id = frameOwners.get(frame.getUniqueId());
        return id == null ? null : id.face();
    }

    /** True if some graft covers this block face. */
    public boolean hasBlockGraft(Block block, BlockFace face) {
        return coveredTiles.containsKey(TileKey.of(block, face));
    }

    /**
     * Keeps block grafts in sync with the world. Runs every second.
     * Removes grafts whose blocks disappeared (pistons, water, falling blocks, anything)
     * and restores frames that vanished because their chunk was unloaded.
     */
    public void validateBlockGrafts(List<World> worlds) {
        Map<UUID, World> byId = new HashMap<>();
        worlds.forEach(world -> byId.put(world.getUID(), world));

        Iterator<Map.Entry<GraftId, BlockGraft>> grafts = new ArrayList<>(blockGrafts.entrySet()).iterator();
        while (grafts.hasNext()) {
            Map.Entry<GraftId, BlockGraft> entry = grafts.next();
            GraftId id = entry.getKey();
            World world = byId.get(id.anchor().world());
            if (world == null) {
                forgetGraft(id);
                continue;
            }
            BlockGraft graft = entry.getValue();
            if (!graft.tiles().stream().allMatch(tile -> world.isChunkLoaded(tile.support().x() >> 4, tile.support().z() >> 4))) {
                continue;
            }
            boolean broken = graft.tiles().stream().anyMatch(tile ->
                    tile.support().toBlock(world).isPassable()
                            || !tile.support().toBlock(world).getRelative(id.face()).isPassable());
            if (broken) {
                removeGraft(world, id, true);
                continue;
            }
            List<Tile> repaired = new ArrayList<>();
            for (Tile tile : graft.tiles()) {
                if (liveFrame(world, tile.frameId()) != null) {
                    repaired.add(tile);
                    continue;
                }
                ItemFrame frame = spawnFrame(tile.support().toBlock(world), id.face(),
                        mapItem(graft.art(), graft.width(), tile.column(), tile.row(), world));
                if (frame == null) {
                    repaired = null;
                    break;
                }
                frameOwners.remove(tile.frameId());
                frameOwners.put(frame.getUniqueId(), id);
                repaired.add(new Tile(tile.support(), tile.column(), tile.row(), frame.getUniqueId()));
            }
            if (repaired == null) {
                removeGraft(world, id, false);
            } else {
                blockGrafts.put(id, new BlockGraft(graft.art(), graft.width(), repaired));
            }
        }
    }

    /**
     * Places art on a block face, trying {@code width} first and then smaller sizes until
     * one fits. Replaces this graft and any grafts it overlaps. Returns the width placed, or 0.
     */
    private int place(Block anchor, BlockFace face, Fanart art, int width) {
        GraftId id = new GraftId(BlockKey.of(anchor), face);
        for (int size = width; size >= 1; size--) {
            List<PlannedTile> plan = plan(anchor, face, art, size);
            if (plan == null) {
                continue;
            }
            // Clear this graft and everything under the new art before spawning.
            removeGraft(anchor.getWorld(), id, false);
            for (PlannedTile tile : plan) {
                GraftId other = coveredTiles.get(TileKey.of(tile.support(), face));
                if (other != null) {
                    removeGraft(anchor.getWorld(), other, false);
                }
            }
            List<Tile> tiles = new ArrayList<>();
            boolean failed = false;
            for (PlannedTile tile : plan) {
                ItemFrame frame = spawnFrame(tile.support(), face,
                        mapItem(art, size, tile.column(), tile.row(), anchor.getWorld()));
                if (frame == null) {
                    failed = true;
                    break;
                }
                tiles.add(new Tile(BlockKey.of(tile.support()), tile.column(), tile.row(), frame.getUniqueId()));
            }
            if (failed) {
                tiles.forEach(tile -> removeEntity(anchor.getWorld(), tile.frameId(), false));
                continue;
            }
            remember(id, new BlockGraft(art, size, tiles));
            return size;
        }
        return 0;
    }

    /**
     * Works out which block holds each tile of a graft. The longer side of the picture
     * spans {@code size} blocks. The art is centered horizontally on the clicked block;
     * on walls it rises from the clicked block, on floors and ceilings it is centered
     * both ways. Returns null if it does not fit.
     */
    private List<PlannedTile> plan(Block anchor, BlockFace face, Fanart art, int size) {
        if (anchor.isPassable() || !anchor.getRelative(face).isPassable()) {
            return null;
        }
        int[] grid = FanartMaps.gridFor(art, size);
        int columns = grid[0];
        int rows = grid[1];
        int[] right = rightOf(face);
        int[] up = upOf(face);
        int centerColumn = (columns - 1) / 2;
        boolean wall = face.getModY() == 0;
        int baseRow = wall ? rows - 1 : (rows - 1) / 2;

        List<PlannedTile> plan = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int across = column - centerColumn;
                int above = baseRow - row;
                Block support = anchor.getRelative(
                        right[0] * across + up[0] * above,
                        right[1] * across + up[1] * above,
                        right[2] * across + up[2] * above);
                if (support.isPassable() || !support.getRelative(face).isPassable()) {
                    return null;
                }
                plan.add(new PlannedTile(support, column, row));
            }
        }
        return plan;
    }

    /**
     * World direction of the picture's right edge, matching how vanilla draws a map in an
     * item frame with no rotation. On walls this is the viewer's right.
     */
    static int[] rightOf(BlockFace face) {
        return switch (face) {
            case NORTH -> new int[]{-1, 0, 0};
            case SOUTH, UP, DOWN -> new int[]{1, 0, 0};
            case EAST -> new int[]{0, 0, -1};
            case WEST -> new int[]{0, 0, 1};
            default -> throw new IllegalArgumentException("Not a block face: " + face);
        };
    }

    /** World direction of the picture's top edge for an unrotated map in an item frame. */
    static int[] upOf(BlockFace face) {
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST -> new int[]{0, 1, 0};
            case UP -> new int[]{0, 0, -1};
            case DOWN -> new int[]{0, 0, 1};
            default -> throw new IllegalArgumentException("Not a block face: " + face);
        };
    }

    private ItemFrame spawnFrame(Block support, BlockFace face, ItemStack mapItem) {
        Block front = support.getRelative(face);
        if (support.isPassable() || !front.isPassable()) {
            return null;
        }
        try {
            return support.getWorld().spawn(front.getLocation(), ItemFrame.class, spawned -> {
                spawned.setFacingDirection(face, true);
                tag(spawned);
                spawned.setItem(mapItem, false);
                spawned.setVisible(false);
                spawned.setFixed(true);
                spawned.setInvulnerable(true);
            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    private void remember(GraftId id, BlockGraft graft) {
        blockGrafts.put(id, graft);
        for (Tile tile : graft.tiles()) {
            coveredTiles.put(new TileKey(tile.support(), id.face()), id);
            frameOwners.put(tile.frameId(), id);
        }
    }

    private void forgetGraft(GraftId id) {
        BlockGraft graft = blockGrafts.remove(id);
        if (graft == null) {
            return;
        }
        for (Tile tile : graft.tiles()) {
            coveredTiles.remove(new TileKey(tile.support(), id.face()), id);
            frameOwners.remove(tile.frameId());
        }
    }

    private void removeGraft(World world, GraftId id, boolean withEffect) {
        BlockGraft graft = blockGrafts.get(id);
        if (graft == null) {
            return;
        }
        forgetGraft(id);
        for (Tile tile : graft.tiles()) {
            removeEntity(world, tile.frameId(), withEffect);
        }
    }

    private boolean allFramesLive(World world, BlockGraft graft) {
        return graft.tiles().stream().allMatch(tile -> liveFrame(world, tile.frameId()) != null);
    }

    private org.bukkit.Location centerOf(World world, BlockGraft graft) {
        double x = 0, y = 0, z = 0;
        for (Tile tile : graft.tiles()) {
            x += tile.support().x() + 0.5;
            y += tile.support().y() + 0.5;
            z += tile.support().z() + 0.5;
        }
        int count = graft.tiles().size();
        ItemFrame any = liveFrame(world, graft.tiles().get(0).frameId());
        if (count == 1 && any != null) {
            return any.getLocation();
        }
        return new org.bukkit.Location(world, x / count, y / count, z / count);
    }

    private ItemFrame liveFrame(World world, UUID id) {
        Entity entity = findEntity(world, id);
        return entity instanceof ItemFrame frame && frame.isValid() ? frame : null;
    }

    // ---------------------------------------------------- mobs and projectiles

    /** Grafts art onto a mob, replacing any art it already has. Returns the art used, or null if none is loaded. */
    public Fanart graftMob(Entity mob) {
        return graftMob(mob, 1);
    }

    public Fanart graftMob(Entity mob, int width) {
        return graftMob(mob, width, null);
    }

    /** Grafts the given art (or random art if null) onto a mob. */
    public Fanart graftMob(Entity mob, int width, Fanart chosen) {
        checkWidth(width);
        Fanart art = chosen != null ? chosen : library.random();
        if (art == null) {
            return null;
        }
        if (!attachRider(mob, new RiderGraft(art, false, width))) return null;
        GraftEffects.potionSwirlAround(mob);
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
        if (previous == null || previous.projectile()
                || !attachRider(mob, new RiderGraft(previous.art(), false, width))) {
            return false;
        }
        GraftEffects.potionSwirlAround(mob);
        GraftEffects.graftApplied(mob.getLocation().add(0, mob.getHeight() / 2, 0));
        return true;
    }

    /** True if this entity has mob art. */
    public boolean hasMobGraft(Entity mob) {
        RiderGraft graft = riderGrafts.get(mob.getUniqueId());
        return graft != null && !graft.projectile();
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
                (graft.projectile() ? settings.projectileSize() : settings.mobSize()) * graft.width(),
                graft.projectile());
        if (attached) {
            riderGrafts.put(target.getUniqueId(), graft);
        }
        return attached;
    }

    /** Text art scaled evenly, so bigger sizes keep the picture's proportions. */
    private boolean attachTextRider(Entity vehicle, Fanart art, float sizeInBlocks, boolean centered) {
        float scale = sizeInBlocks / (art.textHeight() * TEXT_LINE_HEIGHT);
        float lift = centered ? -sizeInBlocks / 2f : 0.1f;
        TextDisplay display;
        try {
            display = vehicle.getWorld().spawn(vehicle.getLocation(), TextDisplay.class, spawned -> {
                tag(spawned);
                spawned.text(art.textArt());
                spawned.setLineWidth(Integer.MAX_VALUE / 2);
                // Opaque background in the art's average color fills the gaps between pixels.
                spawned.setBackgroundColor(Color.fromARGB(0xFF000000 | art.textBackground()));
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
        coveredTiles.clear();
        frameOwners.clear();
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
        // A newly spawned entity can already be in the chunk before a UUID lookup sees it.
        return world.getEntities().stream().filter(candidate -> candidate.getUniqueId().equals(id))
                .findFirst().orElse(null);
    }

    private void tag(Entity entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(graftKey, PersistentDataType.BOOLEAN, true);
    }

    private static void checkWidth(int width) {
        if (width < 1 || width > MAX_WIDTH) {
            throw new IllegalArgumentException("width must be between 1 and " + MAX_WIDTH);
        }
    }

    private ItemStack mapItem(Fanart art, int width, int column, int row, World world) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        item.editMeta(MapMeta.class, meta -> meta.setMapView(maps.tileFor(art, width, column, row, world)));
        return item;
    }

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    /** Block position key that does not hold a reference to the world. */
    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        Block toBlock(World world) {
            return world.getBlockAt(x, y, z);
        }
    }

    /** One face of one block. */
    private record TileKey(BlockKey block, BlockFace face) {
        static TileKey of(Block block, BlockFace face) {
            return new TileKey(BlockKey.of(block), face);
        }
    }

    /** A block graft is identified by the block and face that were clicked. */
    private record GraftId(BlockKey anchor, BlockFace face) {
    }

    /** One map tile of a block graft, hanging on its support block. */
    private record Tile(BlockKey support, int column, int row, UUID frameId) {
    }

    private record PlannedTile(Block support, int column, int row) {
    }

    /** A grafted picture on a block face, made of one or more tiles. */
    private record BlockGraft(Fanart art, int width, List<Tile> tiles) {
    }

    /** Art riding a mob or projectile. */
    private record RiderGraft(Fanart art, boolean projectile, int width) {
    }
}
