package club.rainbowkitty.playerportals.portal;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.playerportals.frame.PortalFrame;
import club.rainbowkitty.playerportals.frame.PortalPlane;

/**
 * Where an entity comes out: the same place in the far frame it had in the near one, with its
 * velocity and facing kept, so whatever walked or fell in keeps going. The arrival stands inside
 * the far portal, which vanilla's portal cooldown keeps from sending it straight back.
 */
public final class PortalExit {
    // A quarter turn, as vanilla turns an entity between a nether portal of each axis.
    private static final float QUARTER_TURN = 90.0F;

    private PortalExit() {
    }

    /**
     * The transition carrying {@code entity} from {@code from} into {@code to}, which stands in
     * {@code toLevel}.
     */
    public static TeleportTransition transition(Entity entity, PortalFrame from,
            ServerLevel toLevel, PortalFrame to) {
        EntityDimensions size = entity.getDimensions(entity.getPose());
        Vec3 pos = entity.position();
        PortalPlane near = from.plane();
        PortalPlane far = to.plane();
        double along = fraction(pos, from, near.along(), from.width(), size);
        double up = fraction(pos, from, near.up(), from.height(), size);
        // The offset across the plane only carries between planes of a kind: feet across a flat
        // one and a centre across an upright one are different measures.
        double across = near.isFlat() == far.isFlat()
                ? pos.get(near.normal()) - (from.origin().get(near.normal()) + 0.5) : 0.0;
        Vec3 target = Vec3.ZERO
                .with(far.along(), place(to, far.along(), to.width(), along, size))
                .with(far.up(), place(to, far.up(), to.height(), up, size))
                .with(far.normal(), to.origin().get(far.normal()) + 0.5 + across);
        Vec3 free = PortalShape.findCollisionFreePosition(target, toLevel, entity, size);
        float turn = near != far && !near.isFlat() && !far.isFlat() ? QUARTER_TURN : 0.0F;
        return new TeleportTransition(toLevel, free, Vec3.ZERO, turn, 0.0F,
                Relative.union(Relative.DELTA, Relative.ROTATION),
                TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)
                        .then(PortalExit::coolPassengers));
    }

    /** Whether an entity of {@code size} has room inside {@code frame}. */
    public static boolean fits(EntityDimensions size, PortalFrame frame) {
        return extent(frame.plane().along(), size) <= frame.width()
                && extent(frame.plane().up(), size) <= frame.height();
    }

    // Vanilla gives the entity that crossed a cooldown but not its riders, who could not use the
    // portal while riding; one who dismounts inside the far portal would otherwise go straight
    // back alone.
    private static void coolPassengers(Entity entity) {
        for (Entity passenger : entity.getIndirectPassengers()) {
            passenger.setPortalCooldown();
        }
    }

    // Where on `axis` the entity sits within a frame `cells` long, as a fraction of the room it
    // has there: the middle (or the floor, going up) when it has none.
    private static double fraction(Vec3 pos, PortalFrame frame, Direction.Axis axis, int cells,
            EntityDimensions size) {
        double extent = extent(axis, size);
        double room = cells - extent;
        if (room <= 0.0) {
            return axis == Direction.Axis.Y ? 0.0 : 0.5;
        }
        double lowEdge = pos.get(axis) - (axis == Direction.Axis.Y ? 0.0 : extent / 2.0);
        return Mth.clamp((lowEdge - frame.origin().get(axis)) / room, 0.0, 1.0);
    }

    // The coordinate on `axis` that puts the entity at `fraction` of its room in a frame `cells`
    // long: its centre on a horizontal axis, its feet on the vertical one.
    private static double place(PortalFrame frame, Direction.Axis axis, int cells,
            double fraction, EntityDimensions size) {
        double extent = extent(axis, size);
        double lowEdge = frame.origin().get(axis) + (cells - extent) * fraction;
        return lowEdge + (axis == Direction.Axis.Y ? 0.0 : extent / 2.0);
    }

    private static double extent(Direction.Axis axis, EntityDimensions size) {
        return axis == Direction.Axis.Y ? size.height() : size.width();
    }
}
