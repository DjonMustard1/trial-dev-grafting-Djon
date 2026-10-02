package dev.djon.grafting.art;

/**
 * Who made a piece of fanart.
 *
 * @param artist display name, never blank
 * @param social handle such as "@artist" on a platform, may be empty
 * @param link   profile URL, may be empty
 */
public record Credit(String artist, String social, String link) {

    public static final Credit UNKNOWN = new Credit("", "", "");

    public Credit {
        artist = artist == null || artist.isBlank() ? "Unknown artist" : artist.strip();
        social = social == null ? "" : social.strip();
        link = link == null ? "" : link.strip();
    }

    public boolean hasSocial() {
        return !social.isEmpty();
    }

    /** Only http(s) links are made clickable. */
    public boolean hasLink() {
        return link.startsWith("https://") || link.startsWith("http://");
    }
}
