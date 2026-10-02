package dev.djon.grafting;

import dev.djon.grafting.art.FanartLibrary;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Grafting: shift + right-click blocks and mobs to graft random fanart onto them,
 * or shift + right-click the air to graft fanart onto everything you shoot.
 */
public class GraftingPlugin extends JavaPlugin {

    private GraftManager grafts;
    private GraftListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "fanart/credits.yml").exists()) {
            saveResource("fanart/credits.yml", false);
        }
        GraftSettings settings = GraftSettings.from(getConfig());

        FanartLibrary library = new FanartLibrary(new File(getDataFolder(), "fanart"), getLogger(),
                settings.textArtResolution());
        grafts = new GraftManager(library, new NamespacedKey(this, "graft"), settings);
        listener = new GraftListener(grafts, this);

        int count = library.load();
        getLogger().info("Loaded " + count + " fanart from " + library.folder().getPath());
        if (count == 0) {
            getLogger().warning("The fanart folder is empty. Add images and run /graft reload.");
        }

        getServer().getPluginManager().registerEvents(listener, this);
        // Every second, drop grafts whose block is gone and restore frames after chunk reloads.
        getServer().getScheduler().runTaskTimer(this, () -> grafts.validateBlockGrafts(Bukkit.getWorlds()), 20L, 20L);
        PluginCommand command = getCommand("graft");
        if (command != null) {
            GraftCommand executor = new GraftCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (grafts != null) {
            grafts.clearAll(Bukkit.getWorlds());
        }
    }

    /** Reloads images from the fanart folder. Existing grafts keep their current art. */
    public int reloadFanart() {
        int count = grafts.library().load();
        grafts.onLibraryReloaded();
        return count;
    }

    public GraftManager grafts() {
        return grafts;
    }

    public GraftListener listener() {
        return listener;
    }
}
