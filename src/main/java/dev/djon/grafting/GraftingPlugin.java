package dev.djon.grafting;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the Grafting plugin.
 * The Grafting mechanic itself is added once the design is decided.
 */
public final class GraftingPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Grafting enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Grafting disabled.");
    }
}
