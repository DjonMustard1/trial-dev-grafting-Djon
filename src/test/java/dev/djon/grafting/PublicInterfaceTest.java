package dev.djon.grafting;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the public surface: command help, size validation, tab completion, and config defaults. */
class PublicInterfaceTest {

    private ServerMock server;
    private GraftingPlugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestMocks.Server());
        plugin = MockBukkit.load(GraftingPlugin.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void helpMentionsEverySubcommand() {
        player.performCommand("graft");
        StringBuilder all = new StringBuilder();
        String line;
        while ((line = player.nextMessage()) != null) {
            all.append(line).append('\n');
        }
        for (String sub : List.of("list", "reload", "clear", "projectiles", "size")) {
            assertTrue(all.toString().contains(sub), "help should mention " + sub + ":\n" + all);
        }
    }

    @Test
    void sizeWithoutNumberShowsUsageAndCurrentSize() {
        player.performCommand("graft size");
        String message = player.nextMessage();
        assertTrue(message.contains("1-10") && message.contains("now 1"), message);
    }

    @Test
    void sizeTabCompletesOneToTen() {
        List<String> options = server.getCommandTabComplete(player, "graft size ");
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), options);
        assertTrue(server.getCommandTabComplete(player, "graft s").contains("size"));
    }

    @Test
    void bundledConfigMatchesCodeDefaults() {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("config.yml")), StandardCharsets.UTF_8));
        GraftSettings fromFile = GraftSettings.from(bundled);
        GraftSettings fromDefaults = GraftSettings.from(new YamlConfiguration());
        assertEquals(fromDefaults, fromFile, "config.yml and code defaults disagree");
        assertEquals(new GraftSettings(48, 2.0f, 1.0f, 1.0f, 16), fromDefaults, "README documents these defaults");
    }
}
