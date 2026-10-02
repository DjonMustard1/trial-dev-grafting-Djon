package dev.djon.grafting.art;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads fanart/credits.yml, which maps each image file name to its artist:
 * <pre>
 * "Fors art.jpg":
 *   artist: Jane Doe
 *   social: "@janedoe on X"
 *   link: https://x.com/janedoe
 * </pre>
 * File names are matched case-insensitively. Images without an entry are credited
 * as "Unknown artist" and a warning is logged so the owner can fill it in.
 */
public final class CreditsFile {

    public static final String FILE_NAME = "credits.yml";

    private CreditsFile() {
    }

    public static Map<String, Credit> load(File folder, Logger logger) {
        File file = new File(folder, FILE_NAME);
        Map<String, Credit> credits = new HashMap<>();
        if (!file.isFile()) {
            return credits;
        }
        // File names contain dots, which Bukkit YAML would treat as nested paths.
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('/');
        try {
            yaml.load(file);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            logger.warning("Could not read " + FILE_NAME + ": " + e.getMessage());
            return credits;
        }
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                logger.warning(FILE_NAME + ": entry '" + key + "' should have artist/social/link under it");
                continue;
            }
            credits.put(normalize(key), new Credit(
                    section.getString("artist"),
                    section.getString("social"),
                    section.getString("link")));
        }
        return credits;
    }

    static String normalize(String fileName) {
        return fileName.strip().toLowerCase(Locale.ROOT);
    }
}
