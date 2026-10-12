package club.rainbowkitty.playerportals.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.playerportals.frame.PortalFrame;

/** The sound and particles each change to a portal makes, at its frame. */
public final class PortalEffects {
    private static final int PARTICLES_PER_BLOCK = 8;
    private static final int MAX_PARTICLES = 160;
    // The spread across the plane, so a burst reads as a sheet, not a slab.
    private static final double ACROSS_SPREAD = 0.15;

    private PortalEffects() {
    }

    /** A catalyst took note of {@code frame}. */
    public static void attuned(ServerLevel level, PortalFrame frame) {
        play(level, frame, ParticleTypes.REVERSE_PORTAL, SoundEvents.BEACON_POWER_SELECT, 1.2F);
    }

    /** {@code frame} just lit. */
    public static void lit(ServerLevel level, PortalFrame frame) {
        play(level, frame, ParticleTypes.PORTAL, SoundEvents.BEACON_ACTIVATE, 1.0F);
    }

    /** {@code frame} just went dark. */
    public static void faded(ServerLevel level, PortalFrame frame) {
        play(level, frame, ParticleTypes.REVERSE_PORTAL, SoundEvents.BEACON_DEACTIVATE, 0.8F);
    }

    private static void play(ServerLevel level, PortalFrame frame, SimpleParticleType particle,
            SoundEvent sound, float pitch) {
        Vec3 centre = Vec3.atLowerCornerOf(frame.origin())
                .add(Vec3.atLowerCornerOf(frame.at(frame.width() - 1, frame.height() - 1)))
                .scale(0.5).add(0.5, 0.5, 0.5);
        level.sendParticles(particle, centre.x, centre.y, centre.z,
                Math.min(MAX_PARTICLES, PARTICLES_PER_BLOCK * frame.width() * frame.height()),
                spread(frame, Direction.Axis.X), spread(frame, Direction.Axis.Y),
                spread(frame, Direction.Axis.Z), 0.2);
        level.playSound(null, BlockPos.containing(centre), sound, SoundSource.BLOCKS, 1.0F, pitch);
    }

    // Half the frame's extent on `axis`, or a sliver across the plane.
    private static double spread(PortalFrame frame, Direction.Axis axis) {
        if (axis == frame.plane().along()) {
            return frame.width() / 2.0;
        }
        if (axis == frame.plane().up()) {
            return frame.height() / 2.0;
        }
        return ACROSS_SPREAD;
    }
}
