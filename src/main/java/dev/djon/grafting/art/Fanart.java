package dev.djon.grafting.art;

import net.kyori.adventure.text.Component;

import java.awt.image.BufferedImage;

/**
 * One loaded piece of fanart, preprocessed for every way it can be displayed.
 *
 * @param name      file name without extension
 * @param mapImage  128 x 128 image for map art (blocks and mobs)
 * @param textArt   colored block text for text displays (projectiles and mob fallback)
 * @param textWidth width of the text art in pixels
 * @param textHeight height of the text art in pixels
 */
public record Fanart(String name, BufferedImage mapImage, Component textArt, int textWidth, int textHeight) {
}
