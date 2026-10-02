package dev.djon.grafting;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Values read from config.yml.
 *
 * @param textArtResolution longest side, in pixels, of text art
 * @param mobSize           height in blocks of art on mobs
 * @param projectileSize    height in blocks of art on projectiles
 * @param textYStretch      vertical stretch for text art, tweak if pixels look too tall or flat
 * @param mobMapArt         true to try sharp map art on mobs, false to always use text art
 */
public record GraftSettings(int textArtResolution, float mobSize, float projectileSize,
                            float textYStretch, boolean mobMapArt) {

    public static GraftSettings from(FileConfiguration config) {
        return new GraftSettings(
                clamp(config.getInt("text-art.resolution", 32), 8, 64),
                (float) config.getDouble("text-art.mob-size", 1.5),
                (float) config.getDouble("text-art.projectile-size", 1.0),
                (float) config.getDouble("text-art.y-stretch", 1.0),
                !"text".equalsIgnoreCase(config.getString("mob-display", "map")));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
