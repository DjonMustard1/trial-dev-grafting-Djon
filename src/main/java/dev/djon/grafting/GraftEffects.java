package dev.djon.grafting;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Particle and sound cues for grafting. */
public final class GraftEffects {
    private static final Particle.DustOptions VIOLET =
            new Particle.DustOptions(Color.fromRGB(155, 77, 238), 1.1f);
    private static final Particle.DustOptions LILAC =
            new Particle.DustOptions(Color.fromRGB(218, 166, 255), 0.8f);

    private GraftEffects() {}

    /** A graft settles into the target. */
    public static void graftApplied(Location where) {
        World world = where.getWorld();
        if (world == null) {
            return;
        }

        purpleBurst(world, where, false);
        world.playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.35f);
    }

    /** A broken graft dissolves into gray motes. */
    public static void graftBroken(Location where) {
        World world = where.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.SMOKE, where, 8, 0.18, 0.18, 0.18, 0.008);
        world.spawnParticle(Particle.ASH, where, 6, 0.26, 0.22, 0.26, 0.0);
        world.playSound(where, Sound.BLOCK_CANDLE_EXTINGUISH, 0.3f, 0.65f);
    }

    /** Projectile mode gathers or releases a veil of fog. */
    public static void projectileModeToggled(Player player, boolean enabled) {
        Location where = player.getLocation().add(0.0, 1.0, 0.0);
        World world = where.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.WHITE_SMOKE, where, enabled ? 8 : 4,
                0.24, 0.3, 0.24, 0.004);
        world.spawnParticle(Particle.ASH, where, 4, 0.28, 0.32, 0.28, 0.0);
        if (enabled) {
            world.playSound(where, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.25f, 0.6f);
        } else {
            world.playSound(where, Sound.BLOCK_CANDLE_EXTINGUISH, 0.2f, 0.8f);
        }
    }

    /** A launched graft flashes briefly in purple. */
    public static void projectileGrafted(Location where) {
        World world = where.getWorld();
        if (world == null) {
            return;
        }

        purpleBurst(world, where, true);
    }

    /** A compact radial spray with bright tips, scaled down for projectiles. */
    private static void purpleBurst(World world, Location center, boolean small) {
        int rays = small ? 6 : 10;
        double radius = small ? 0.3 : 0.55;
        world.spawnParticle(Particle.DUST, center, small ? 5 : 12,
                0.08, 0.08, 0.08, 0.0, VIOLET);
        for (int i = 0; i < rays; i++) {
            double angle = 2.0 * Math.PI * i / rays;
            double y = (i % 3 - 1) * radius * 0.55;
            Location tip = center.clone().add(radius * Math.cos(angle), y,
                    radius * Math.sin(angle));
            world.spawnParticle(Particle.DUST, tip, 1, 0, 0, 0, 0, LILAC);
        }
        world.spawnParticle(Particle.FIREWORK, center, small ? 2 : 4,
                radius * 0.35, radius * 0.3, radius * 0.35, 0.01);
    }
}
