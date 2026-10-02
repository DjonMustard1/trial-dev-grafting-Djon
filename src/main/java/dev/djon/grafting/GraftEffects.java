package dev.djon.grafting;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Subtle fog and whispered resonance for grafting. */
public final class GraftEffects {
    private GraftEffects() {}

    /** A graft settles into the target. */
    public static void graftApplied(Location where) {
        World world = where.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.WHITE_SMOKE, where, 12, 0.22, 0.18, 0.22, 0.005);
        world.spawnParticle(Particle.ASH, where, 8, 0.28, 0.24, 0.28, 0.0);
        world.spawnParticle(Particle.REVERSE_PORTAL, where, 4, 0.16, 0.16, 0.16, 0.015);
        world.playSound(where, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 0.55f);
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

    /** A launched graft leaves only a faint trace. */
    public static void projectileGrafted(Location where) {
        World world = where.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.WHITE_SMOKE, where, 2, 0.06, 0.06, 0.06, 0.002);
        world.spawnParticle(Particle.ASH, where, 2, 0.08, 0.08, 0.08, 0.0);
    }
}
