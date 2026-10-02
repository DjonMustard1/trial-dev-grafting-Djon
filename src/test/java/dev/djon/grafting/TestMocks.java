package dev.djon.grafting;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.TextDisplayMock;
import org.mockbukkit.mockbukkit.map.MapViewMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fills in the few Paper methods MockBukkit 4.116.1 leaves unimplemented, so the
 * real plugin code can run unchanged in tests. Each override mirrors vanilla behavior.
 */
final class TestMocks {

    private TestMocks() {
    }

    /** Server whose maps accept tracking settings. */
    static final class Server extends ServerMock {
        private final AtomicInteger mapIds = new AtomicInteger();

        @Override
        public MapViewMock createMap(World world) {
            return new MapViewMock(world, mapIds.incrementAndGet()) {
                @Override
                public void setTrackingPosition(boolean tracking) {
                }

                @Override
                public void setUnlimitedTracking(boolean unlimited) {
                }
            };
        }

        WorldMock addTestWorld(String name) {
            World created = new TestWorld(this, name);
            addWorld((WorldMock) created);
            return (WorldMock) created;
        }
    }

    /** World whose blocks know if they are passable and whose text displays accept every setter. */
    static final class TestWorld extends WorldMock {
        private final ServerMock server;

        TestWorld(ServerMock server, String name) {
            super(org.bukkit.Material.GRASS_BLOCK, 64);
            this.server = server;
            setName(name);
        }

        @Override
        public BlockMock getBlockAt(int x, int y, int z) {
            BlockMock real = super.getBlockAt(x, y, z);
            return new PassableAwareBlock(real);
        }

        @Override
        public <T extends Entity> T spawn(Location location, Class<T> clazz, Consumer<? super T> function,
                                          CreatureSpawnEvent.SpawnReason reason) {
            if (clazz == TextDisplay.class) {
                TextDisplayMock display = new LenientTextDisplay(server, UUID.randomUUID());
                display.setLocation(location.clone());
                server.registerEntity(display);
                @SuppressWarnings("unchecked")
                T typed = (T) display;
                if (function != null) {
                    function.accept(typed);
                }
                return typed;
            }
            return super.spawn(location, clazz, function, reason);
        }
    }

    /** Passable means the block can be walked through: air, plants, and so on. */
    static final class PassableAwareBlock extends BlockMock {
        private final BlockMock delegate;

        PassableAwareBlock(BlockMock delegate) {
            super(delegate.getType(), delegate.getLocation());
            this.delegate = delegate;
        }

        @Override
        public boolean isPassable() {
            return !getType().isSolid();
        }

        @Override
        public org.bukkit.Material getType() {
            // The super constructor calls this before delegate is assigned.
            return delegate == null ? super.getType() : delegate.getType();
        }

        @Override
        public void setType(org.bukkit.Material type) {
            if (delegate == null) {
                super.setType(type);
            } else {
                delegate.setType(type);
            }
        }
    }

    /** Text display that stores billboard and brightness instead of throwing. */
    static final class LenientTextDisplay extends TextDisplayMock {
        private Display.Billboard billboard = Display.Billboard.FIXED;
        private Display.Brightness brightness;

        LenientTextDisplay(ServerMock server, UUID uuid) {
            super(server, uuid);
        }

        @Override
        public void setBillboard(Display.Billboard billboard) {
            this.billboard = billboard;
        }

        @Override
        public Display.Billboard getBillboard() {
            return billboard;
        }

        @Override
        public void setBrightness(Display.Brightness brightness) {
            this.brightness = brightness;
        }

        @Override
        public Display.Brightness getBrightness() {
            return brightness;
        }
    }
}
