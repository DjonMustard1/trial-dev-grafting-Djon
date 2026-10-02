package dev.djon.grafting.art;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * The library of fanart that can be grafted. Images are loaded from the plugin's
 * fanart folder and one is chosen at random for every graft.
 */
public final class FanartLibrary {

    /** Map art is always 128 x 128, the size of one Minecraft map. */
    public static final int MAP_SIZE = 128;

    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    private final File folder;
    private final Logger logger;
    private final int textArtSize;
    private List<Fanart> entries = List.of();
    private Fanart last;

    public FanartLibrary(File folder, Logger logger, int textArtSize) {
        this.folder = folder;
        this.logger = logger;
        this.textArtSize = textArtSize;
    }

    /** Loads (or reloads) every supported image in the folder. Returns the count loaded. */
    public int load() {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            logger.warning("Could not create fanart folder: " + folder);
        }
        File[] files = folder.listFiles(file -> file.isFile() && isSupported(file.getName()));
        Map<String, Credit> credits = CreditsFile.load(folder, logger);
        List<Fanart> loaded = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                try {
                    BufferedImage image = ImageIO.read(file);
                    if (image == null) {
                        logger.warning("Skipping unreadable image: " + file.getName());
                        continue;
                    }
                    Credit credit = credits.get(CreditsFile.normalize(file.getName()));
                    if (credit == null) {
                        logger.warning("No credit for " + file.getName() + " in " + CreditsFile.FILE_NAME);
                        credit = Credit.UNKNOWN;
                    }
                    loaded.add(process(stripExtension(file.getName()), image, textArtSize, credit));
                } catch (IOException e) {
                    logger.warning("Failed to load " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        entries = Collections.unmodifiableList(loaded);
        last = null;
        return entries.size();
    }

    public static Fanart process(String name, BufferedImage image, int textArtSize) {
        return process(name, image, textArtSize, Credit.UNKNOWN);
    }

    public static Fanart process(String name, BufferedImage image, int textArtSize, Credit credit) {
        BufferedImage mapImage = ArtProcessor.fitToSquare(image, MAP_SIZE);
        int[][] grid = ArtProcessor.toPixelGrid(image, textArtSize);
        return new Fanart(name, mapImage, PixelArtText.fromGrid(grid), grid[0].length, grid.length, credit);
    }

    /** Picks a random fanart, avoiding the same one twice in a row when possible. */
    public Fanart random() {
        if (entries.isEmpty()) {
            return null;
        }
        Fanart pick;
        do {
            pick = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        } while (entries.size() > 1 && pick == last);
        last = pick;
        return pick;
    }

    public List<Fanart> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public File folder() {
        return folder;
    }

    static boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
