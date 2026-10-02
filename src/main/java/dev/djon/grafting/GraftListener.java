package dev.djon.grafting;

import dev.djon.grafting.art.Fanart;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player input and cleanup.
 * Shift + right-click a block or mob grafts it. Shift + right-click the air toggles
 * projectile grafting. Grafts vanish when their target is destroyed.
 */
public final class GraftListener implements Listener {

    /** Items that shoot or throw on right-click; they never toggle projectile mode. */
    private static final Set<Material> LAUNCHERS = EnumSet.of(
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SNOWBALL, Material.EGG,
            Material.BLUE_EGG, Material.BROWN_EGG, Material.ENDER_PEARL, Material.SPLASH_POTION,
            Material.LINGERING_POTION, Material.EXPERIENCE_BOTTLE, Material.WIND_CHARGE,
            Material.FIRE_CHARGE, Material.FISHING_ROD);

    private final GraftManager grafts;
    private final Set<UUID> projectileMode = new HashSet<>();
    /** Width selected by sneaking and middle-clicking a block or entity. */
    private final Map<UUID, Integer> selectedWidth = new HashMap<>();
    /** Time of each player's last shift + right-click on an entity, in milliseconds. */
    private final Map<UUID, Long> lastEntityClick = new HashMap<>();

    /** Air clicks this soon after an entity click are treated as the same click. */
    private static final long ECHO_WINDOW_MS = 250;

    /** At most one projectile credit line per player in this window. */
    static final long PROJECTILE_CREDIT_COOLDOWN_MS = 2000;
    private final Map<UUID, Long> lastProjectileCredit = new HashMap<>();

    public GraftListener(GraftManager grafts) {
        this.grafts = grafts;
    }

    // ----------------------------------------------------------------- input

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking() || !player.hasPermission("grafting.use")) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            graftBlock(player, event.getClickedBlock(), event.getBlockFace());
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR
                && !LAUNCHERS.contains(player.getInventory().getItemInMainHand().getType())
                && !recentlyClickedEntity(player)) {
            toggleProjectileMode(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking() || !player.hasPermission("grafting.use")) {
            return;
        }
        // The client can also report this click as an air click; ignore that echo.
        lastEntityClick.put(player.getUniqueId(), System.currentTimeMillis());

        // Clicking existing art re-grafts whatever it is attached to.
        if (grafts.isGraftEntity(target)) {
            event.setCancelled(true);
            Block anchor = grafts.blockOfFrame(target);
            if (anchor != null) {
                BlockFace face = grafts.faceOfFrame(target);
                if (face != null) graftBlock(player, anchor, face);
            } else if (target.getVehicle() != null) {
                graftMob(player, target.getVehicle());
            }
            return;
        }

        if (!(target instanceof Player)) {
            event.setCancelled(true);
            graftMob(player, target);
        }
    }

    /** Pick-block is the client's middle mouse button. Cancel vanilla item picking. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPickBlock(PlayerPickBlockEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("grafting.use")) return;
        event.setCancelled(true);
        int width = cycleWidth(player);
        RayTraceResult hit = player.rayTraceBlocks(6);
        if (hit != null && event.getBlock().equals(hit.getHitBlock()) && hit.getHitBlockFace() != null) {
            resizeBlock(player, event.getBlock(), hit.getHitBlockFace(), width);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickEntity(PlayerPickEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("grafting.use")) return;
        event.setCancelled(true);
        int width = cycleWidth(player);
        Entity target = event.getEntity();
        Block anchor = grafts.blockOfFrame(target);
        if (anchor != null) {
            BlockFace face = grafts.faceOfFrame(target);
            if (face != null) resizeBlock(player, anchor, face, width);
        } else {
            grafts.resizeMob(target.getVehicle() != null && grafts.isGraftEntity(target)
                    ? target.getVehicle() : target, width);
        }
    }

    /** Resizes existing block art, telling the player if the wall was too small. */
    private void resizeBlock(Player player, Block block, BlockFace face, int width) {
        int placed = grafts.resizeBlock(block, face, width);
        if (placed > 0 && placed < width) {
            player.sendActionBar(Messages.shrunk(width, placed));
        }
    }

    /** Cycles the player's graft size 1, 2, 3, 4, then back to 1. */
    private int cycleWidth(Player player) {
        int width = selectedWidth.compute(player.getUniqueId(),
                (id, old) -> old == null ? 2 : old >= GraftManager.MAX_WIDTH ? 1 : old + 1);
        player.sendActionBar(Messages.size(width));
        return width;
    }

    int width(Player player) {
        return selectedWidth.getOrDefault(player.getUniqueId(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof Player player && projectileMode.contains(player.getUniqueId())) {
            Fanart art = grafts.graftProjectile(projectile, width(player));
            if (art != null) {
                creditProjectile(player, art);
            }
        }
    }

    // --------------------------------------------------------------- cleanup

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        grafts.removeBlockGrafts(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        grafts.removeBlockGrafts(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(grafts::removeBlockGrafts);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(grafts::removeBlockGrafts);
    }

    /** Graft frames can only be removed by the plugin, never knocked off. */
    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (grafts.isGraftEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (grafts.forgetTarget(event.getEntity())) {
            GraftEffects.graftBroken(event.getEntity().getLocation());
        }
    }

    /**
     * Projectile landed, mob despawned, or anything else removed the target: forget it.
     * Chunk unloads are the exception, the art comes back when the chunk loads again.
     */
    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (event.getCause() != EntityRemoveEvent.Cause.UNLOAD) {
            grafts.forgetTarget(event.getEntity());
        }
    }

    /** Restores art on reloaded targets and clears any stray graft entities. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (grafts.isGraftEntity(entity)) {
                entity.remove();
            } else {
                grafts.restore(entity);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        projectileMode.remove(event.getPlayer().getUniqueId());
        selectedWidth.remove(event.getPlayer().getUniqueId());
        lastEntityClick.remove(event.getPlayer().getUniqueId());
        lastProjectileCredit.remove(event.getPlayer().getUniqueId());
    }

    // --------------------------------------------------------------- helpers

    private boolean recentlyClickedEntity(Player player) {
        Long last = lastEntityClick.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < ECHO_WINDOW_MS;
    }

    public boolean toggleProjectileMode(Player player) {
        UUID id = player.getUniqueId();
        boolean enabled = projectileMode.add(id);
        if (!enabled) {
            projectileMode.remove(id);
        }
        player.sendActionBar(enabled ? Messages.PROJECTILES_ON : Messages.PROJECTILES_OFF);
        GraftEffects.projectileModeToggled(player, enabled);
        return enabled;
    }

    private void graftBlock(Player player, Block block, BlockFace face) {
        int width = width(player);
        GraftManager.BlockResult result = grafts.graftBlock(block, face, width);
        switch (result.status()) {
            case GRAFTED -> {
                credit(player, result.art());
                if (result.width() < width) {
                    player.sendActionBar(Messages.shrunk(width, result.width()));
                }
            }
            case NO_ART -> player.sendMessage(Messages.noArt(grafts.library()));
            case NO_SPACE -> player.sendActionBar(Messages.NO_SPACE);
        }
    }

    private void graftMob(Player player, Entity mob) {
        Fanart art = grafts.graftMob(mob, width(player));
        if (art != null) {
            credit(player, art);
        } else {
            player.sendMessage(Messages.noArt(grafts.library()));
        }
    }

    /** Credits the artist in chat. */
    private void credit(Player player, Fanart art) {
        player.sendActionBar(Messages.GRAFTED);
        if (art.credit().hasAttribution()) {
            player.sendMessage(Messages.credit(art.credit()));
        }
    }

    /**
     * Credits the artist of a grafted projectile in chat. Bows and snowballs can fire
     * several times a second, so each player gets at most one credit line per window.
     */
    private void creditProjectile(Player player, Fanart art) {
        if (!art.credit().hasAttribution()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastProjectileCredit.get(player.getUniqueId());
        if (last == null || now - last >= PROJECTILE_CREDIT_COOLDOWN_MS) {
            lastProjectileCredit.put(player.getUniqueId(), now);
            player.sendMessage(Messages.credit(art.credit()));
        }
    }
}
