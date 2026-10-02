package dev.djon.grafting;

import io.papermc.paper.event.player.PlayerPickEntityEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the real plugin into a mock Paper server and drives it through the same
 * events and commands a player would trigger.
 */
class GraftingPluginTest {

    private ServerMock server;
    private GraftingPlugin plugin;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock(new TestMocks.Server());
        plugin = MockBukkit.load(GraftingPlugin.class);
        world = ((TestMocks.Server) server).addTestWorld("world");
        player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 64, 0.5));
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void addFanart(String... names) throws IOException {
        File folder = new File(plugin.getDataFolder(), "fanart");
        folder.mkdirs();
        for (String name : names) {
            BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(img, "png", new File(folder, name + ".png"));
        }
        plugin.reloadFanart();
    }

    private long count(Class<? extends Entity> type) {
        return world.getEntities().stream()
                .filter(type::isInstance)
                .filter(plugin.grafts()::isGraftEntity)
                .filter(Entity::isValid)
                .count();
    }

    // ------------------------------------------------------------- loading

    @Test
    void pluginEnablesAndCreatesConfigAndFanartFolder() {
        assertTrue(plugin.isEnabled());
        assertTrue(new File(plugin.getDataFolder(), "config.yml").isFile());
        assertTrue(new File(plugin.getDataFolder(), "fanart").isDirectory());
        assertNotNull(server.getPluginCommand("graft"));
    }

    // ------------------------------------------------------------ commands

    @Test
    void reloadCommandLoadsFanartAndListShowsIt() throws IOException {
        File folder = new File(plugin.getDataFolder(), "fanart");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", new File(folder, "fool.png"));
        player.setOp(true);

        player.performCommand("graft reload");
        assertTrue(player.nextMessage().contains("Loaded 1 fanart"));

        player.performCommand("graft list");
        assertTrue(player.nextMessage().contains("(1)"));
        assertTrue(player.nextMessage().contains("fool"));
    }

    @Test
    void adminCommandsNeedPermission() {
        player.setOp(false);
        player.performCommand("graft reload");
        assertTrue(player.nextMessage().contains("permission"));
    }

    @Test
    void graftingWithNoFanartTellsThePlayer() {
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        assertTrue(player.nextMessage().contains("No fanart loaded"));
        assertEquals(0, count(ItemFrame.class));
    }

    // -------------------------------------------------------------- blocks

    @Test
    void shiftRightClickingABlockPlacesOneInvisibleFixedFrame() throws IOException {
        addFanart("a", "b");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);

        rightClickBlock(block, BlockFace.NORTH);

        assertEquals(1, count(ItemFrame.class));
        ItemFrame frame = world.getEntitiesByClass(ItemFrame.class).iterator().next();
        assertFalse(frame.isVisible());
        assertTrue(frame.isFixed());
        assertEquals(Material.FILLED_MAP, frame.getItem().getType());
        assertFalse(frame.isPersistent(), "grafts must not be saved");
    }

    @Test
    void graftingTheSameFaceAgainSwapsArtInsteadOfStacking() throws IOException {
        addFanart("a", "b");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);

        rightClickBlock(block, BlockFace.NORTH);
        rightClickBlock(block, BlockFace.NORTH);

        assertEquals(1, count(ItemFrame.class));
    }

    @Test
    void notSneakingDoesNothing() throws IOException {
        addFanart("a");
        player.setSneaking(false);
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        assertEquals(0, count(ItemFrame.class));
    }

    @Test
    void breakingTheBlockRemovesTheArt() throws IOException {
        addFanart("a");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        assertEquals(1, count(ItemFrame.class));

        server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

        assertEquals(0, count(ItemFrame.class));
    }

    @Test
    void blockRemovedByOtherMeansIsCleanedUpByTheValidator() throws IOException {
        addFanart("a");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);

        block.setType(Material.AIR); // piston, water, or anything without a break event
        world.loadChunk(0, 0);
        server.getScheduler().performTicks(25);

        assertEquals(0, count(ItemFrame.class));
    }

    // ---------------------------------------------------------------- mobs

    @Test
    void shiftRightClickingAMobAttachesArtThatRidesIt() throws IOException {
        addFanart("a");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);

        rightClickEntity(zombie);

        List<Entity> riders = zombie.getPassengers();
        assertEquals(1, riders.size());
        assertTrue(plugin.grafts().isGraftEntity(riders.get(0)));
        assertTrue(riders.get(0) instanceof TextDisplay, "mob art should be visible from either side");
        assertFalse(riders.get(0).isPersistent());
    }

    @Test
    void sneakingMiddleClickResizesMobArtWithoutReplacingTarget() throws IOException {
        addFanart("a");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        rightClickEntity(zombie);
        TextDisplay first = (TextDisplay) zombie.getPassengers().get(0);
        float originalWidth = first.getTransformation().getScale().x;
        float originalHeight = first.getTransformation().getScale().y;

        PlayerPickEntityEvent pick = new PlayerPickEntityEvent(player, zombie,
                new ItemStack(Material.ZOMBIE_SPAWN_EGG), false, 0, -1);
        server.getPluginManager().callEvent(pick);

        assertTrue(pick.isCancelled(), "middle click should not replace the player's held item");
        assertEquals(1, zombie.getPassengers().size());
        TextDisplay resized = (TextDisplay) zombie.getPassengers().get(0);
        assertEquals(originalWidth * 2, resized.getTransformation().getScale().x, 0.001f);
        assertEquals(originalHeight, resized.getTransformation().getScale().y, 0.001f);
    }

    @Test
    void chosenWidthMakesNextBlockGraftWider() throws IOException {
        addFanart("a");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        server.getPluginManager().callEvent(new PlayerPickEntityEvent(player, zombie,
                new ItemStack(Material.ZOMBIE_SPAWN_EGG), false, 0, -1));
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        assertEquals(1, count(ItemDisplay.class));
    }

    @Test
    void middleClickingExistingBlockArtWidensItAndBreakingBlockRemovesIt() throws IOException {
        addFanart("a");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        ItemFrame original = world.getEntitiesByClass(ItemFrame.class).iterator().next();

        server.getPluginManager().callEvent(new PlayerPickEntityEvent(player, original,
                original.getItem(), false, 0, -1));

        assertEquals(0, count(ItemFrame.class));
        assertEquals(1, count(ItemDisplay.class));
        ItemDisplay wide = world.getEntitiesByClass(ItemDisplay.class).iterator().next();
        Block anchor = plugin.grafts().blockOfFrame(wide);
        assertNotNull(anchor);
        assertEquals(block.getX(), anchor.getX());
        assertEquals(block.getY(), anchor.getY());
        assertEquals(block.getZ(), anchor.getZ());
        assertEquals(block.getWorld().getUID(), anchor.getWorld().getUID());
        plugin.grafts().removeBlockGrafts(block);
        assertEquals(null, plugin.grafts().blockOfFrame(wide), "block graft entry should be forgotten");
        assertEquals(0, count(ItemDisplay.class), "wide display must be removed on block break");
    }

    @Test
    void regraftingAMobReplacesItsArt() throws IOException {
        addFanart("a", "b");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        rightClickEntity(zombie);
        rightClickEntity(zombie);
        assertEquals(1, zombie.getPassengers().size());
    }

    @Test
    void mobTextFallbackIsUsedWhenConfigured() throws IOException {
        plugin.getConfig().set("mob-display", "text");
        // Settings are read on enable, so build a fresh manager path by reloading the plugin.
        plugin.saveConfig();
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        addFanart("a");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);

        rightClickEntity(zombie);

        assertEquals(1, zombie.getPassengers().size());
        assertTrue(zombie.getPassengers().get(0) instanceof TextDisplay);
    }

    @Test
    void killingTheMobRemovesItsArt() throws IOException {
        addFanart("a");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        rightClickEntity(zombie);
        Entity rider = zombie.getPassengers().get(0);

        zombie.setHealth(0);

        assertTrue(rider.isDead() || !rider.isValid());
    }

    // --------------------------------------------------------- projectiles

    @Test
    void airClickTogglesProjectileModeAndArrowsGetArt() throws IOException {
        addFanart("a");
        rightClickAir();
        assertTrue(player.nextActionBar() != null);

        Arrow arrow = world.spawn(player.getEyeLocation(), Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));

        assertEquals(1, arrow.getPassengers().size());
        assertTrue(arrow.getPassengers().get(0) instanceof TextDisplay);
    }

    @Test
    void togglingTwiceTurnsProjectileModeOff() throws IOException {
        addFanart("a");
        rightClickAir();
        rightClickAir();

        Arrow arrow = world.spawn(player.getEyeLocation(), Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));

        assertTrue(arrow.getPassengers().isEmpty());
    }

    @Test
    void airClickWhileHoldingABowDoesNotToggle() throws IOException {
        addFanart("a");
        player.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
        rightClickAir();

        Arrow arrow = world.spawn(player.getEyeLocation(), Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));

        assertTrue(arrow.getPassengers().isEmpty());
    }

    @Test
    void airClickEchoRightAfterAMobClickDoesNotToggle() throws IOException {
        addFanart("a");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        rightClickEntity(zombie);
        rightClickAir(); // same physical click, reported again as air

        Arrow arrow = world.spawn(player.getEyeLocation(), Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));

        assertTrue(arrow.getPassengers().isEmpty());
    }

    @Test
    void otherPlayersArrowsAreNotGrafted() throws IOException {
        addFanart("a");
        rightClickAir();
        PlayerMock other = server.addPlayer();

        Arrow arrow = world.spawn(other.getEyeLocation(), Arrow.class);
        arrow.setShooter(other);
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));

        assertTrue(arrow.getPassengers().isEmpty());
    }

    // ------------------------------------------------------------- cleanup

    @Test
    void clearCommandRemovesEverything() throws IOException {
        addFanart("a");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        rightClickEntity(zombie);

        player.setOp(true);
        player.performCommand("graft clear");

        assertEquals(0, count(ItemFrame.class));
        assertEquals(0, count(TextDisplay.class));
        assertTrue(zombie.getPassengers().isEmpty());
    }

    @Test
    void disablingThePluginRemovesAllGrafts() throws IOException {
        addFanart("a");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);

        server.getPluginManager().disablePlugin(plugin);

        assertEquals(0, world.getEntitiesByClass(ItemFrame.class).stream().filter(Entity::isValid).count());
    }

    // ------------------------------------------------------------- credits

    private void addCreditedFanart() throws IOException {
        File folder = new File(plugin.getDataFolder(), "fanart");
        java.nio.file.Files.writeString(new File(folder, "credits.yml").toPath(),
                "\"a.png\":\n  artist: Jane Doe\n  social: \"@janedoe on X\"\n");
        addFanart("a");
    }

    private String nextPlainMessage() {
        net.kyori.adventure.text.Component message = player.nextComponentMessage();
        return message == null ? null
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message);
    }

    @Test
    void firstRunCreatesCreditsTemplate() {
        assertTrue(new File(plugin.getDataFolder(), "fanart/credits.yml").isFile());
    }

    @Test
    void graftingABlockCreditsTheArtistInChat() throws IOException {
        addCreditedFanart();
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        assertEquals("Art by Jane Doe (@janedoe on X)", nextPlainMessage());
    }

    @Test
    void uncreditedFanartGraftsWithoutChatCredit() throws IOException {
        addFanart("a");
        Block block = world.getBlockAt(0, 100, 2);
        block.setType(Material.STONE);
        rightClickBlock(block, BlockFace.NORTH);
        assertEquals(null, nextPlainMessage());

        player.performCommand("graft list");
        player.nextMessage();
        assertEquals(" - a", nextPlainMessage());
    }

    @Test
    void uncreditedMobAndProjectilesDoNotPostChatCredits() throws IOException {
        addFanart("a");
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        rightClickEntity(zombie);
        assertEquals(null, nextPlainMessage());

        rightClickAir();
        player.nextMessage(); // projectile mode toggle
        Arrow arrow = world.spawn(player.getEyeLocation(), Arrow.class);
        arrow.setShooter(player);
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));
        assertEquals(null, nextPlainMessage());
    }

    @Test
    void graftingAMobCreditsTheArtistInChat() throws IOException {
        addCreditedFanart();
        Zombie zombie = world.spawn(new Location(world, 2, 64, 2), Zombie.class);
        rightClickEntity(zombie);
        assertEquals("Art by Jane Doe (@janedoe on X)", nextPlainMessage());
    }

    @Test
    void projectileCreditsAreRateLimited() throws IOException {
        addCreditedFanart();
        rightClickAir();
        for (int i = 0; i < 5; i++) {
            Arrow arrow = world.spawn(player.getEyeLocation(), Arrow.class);
            arrow.setShooter(player);
            server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));
        }
        assertEquals("Art by Jane Doe (@janedoe on X)", nextPlainMessage());
        assertEquals(null, nextPlainMessage(), "five quick shots should give one credit line");
    }

    @Test
    void listCommandShowsCredits() throws IOException {
        addCreditedFanart();
        player.performCommand("graft list");
        player.nextMessage();
        assertEquals(" - a: Art by Jane Doe (@janedoe on X)", nextPlainMessage());
    }

    // ------------------------------------------------------------- helpers

    private void rightClickBlock(Block block, BlockFace face) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, face, EquipmentSlot.HAND));
    }

    private void rightClickAir() {
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND));
    }

    private void rightClickEntity(Entity target) {
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, target, EquipmentSlot.HAND));
    }
}
