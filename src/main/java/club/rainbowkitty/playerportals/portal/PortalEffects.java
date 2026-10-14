package club.rainbowkitty.playerportals.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.portal.PortalFrame;

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

    // A burst over the whole interior, half its extent each way in the plane, and the sound.
    private static void play(ServerLevel level, PortalFrame frame, SimpleParticleType particle,
            SoundEvent sound, float pitch) {
        Vec3 centre = frame.centre();
        Vec3 spread = frame.plane().toWorld(
                new Vec3(frame.width() / 2.0, frame.height() / 2.0, ACROSS_SPREAD));
        level.sendParticles(particle, centre.x, centre.y, centre.z,
                Math.min(MAX_PARTICLES, PARTICLES_PER_BLOCK * frame.width() * frame.height()),
                spread.x, spread.y, spread.z, 0.2);
        level.playSound(null, BlockPos.containing(centre), sound, SoundSource.BLOCKS, 1.0F, pitch);
    }
}
