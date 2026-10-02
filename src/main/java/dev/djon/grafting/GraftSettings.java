package dev.djon.grafting;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Values read from config.yml.
 *
 * @param textArtResolution longest side, in pixels, of text art
 * @param mobSize           height in blocks of art on mobs
 * @param projectileSize    height in blocks of art on projectiles
 * @param textYStretch      vertical stretch for text art, tweak if pixels look too tall or flat
 * @param chargeTicks       ticks of purple potion swirl before a graft lands, 0 for instant
 */
public record GraftSettings(int textArtResolution, float mobSize, float projectileSize,
                            float textYStretch, int chargeTicks) {

    public static GraftSettings from(FileConfiguration config) {
        return new GraftSettings(
                clamp(config.getInt("text-art.resolution", 48), 8, 64),
                (float) config.getDouble("text-art.mob-size", 2.0),
                (float) config.getDouble("text-art.projectile-size", 1.0),
                (float) config.getDouble("text-art.y-stretch", 1.0),
                clamp(config.getInt("effects.charge-ticks", 16), 0, 100));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
