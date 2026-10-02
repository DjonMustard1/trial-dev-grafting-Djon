package dev.djon.grafting.art;

import net.kyori.adventure.text.Component;

import java.awt.image.BufferedImage;

/**
 * One loaded piece of fanart, preprocessed for every way it can be displayed.
 *
 * @param name       file name without extension
 * @param mapImage   128 x 128 image for single block map art
 * @param textArt    colored block text for text displays (mobs and projectiles)
 * @param textWidth  width of the text art in pixels
 * @param textHeight height of the text art in pixels
 * @param credit     the artist, shown in chat whenever this art is grafted
 * @param source     the original image, capped at 512 pixels, used to cut wide grafts into tiles
 */
public record Fanart(String name, BufferedImage mapImage, Component textArt, int textWidth, int textHeight,
                     Credit credit, BufferedImage source) {
}
