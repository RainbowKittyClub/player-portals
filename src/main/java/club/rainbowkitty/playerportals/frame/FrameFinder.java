package club.rainbowkitty.playerportals.frame;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.playerportals.portal.PortalBlock;

/** Finds the frame around an interior block, or beside a frame block a player clicked. */
public final class FrameFinder {
    private FrameFinder() {
    }

    /**
     * Whether {@code state} may be part of a frame: any block whose collision is the full
     * cube. A slab, a fence, a sapling and a fluid are not; glass, a furnace and bedrock are.
     */
    public static boolean isFrameBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.isAir() && state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * The frame in {@code plane} whose interior holds {@code interiorPos}: the rectangle of air
     * (or portal of that plane) around it, at most {@link PortalFrame#MAX_SIZE} each way, with a
     * complete ring of frame blocks.
     */
    public static Optional<PortalFrame> find(Level level, BlockPos interiorPos, PortalPlane plane) {
        if (!isInterior(level.getBlockState(interiorPos), plane)) {
            return Optional.empty();
        }
        int back = run(level, interiorPos, plane, plane.along(), -1);
        int forward = run(level, interiorPos, plane, plane.along(), 1);
        int down = run(level, interiorPos, plane, plane.up(), -1);
        int upward = run(level, interiorPos, plane, plane.up(), 1);
        int width = back + 1 + forward;
        int height = down + 1 + upward;
        if (width > PortalFrame.MAX_SIZE || height > PortalFrame.MAX_SIZE) {
            return Optional.empty();
        }
        BlockPos origin = interiorPos.relative(plane.along(), -back).relative(plane.up(), -down);
        PortalFrame frame = new PortalFrame(level.dimension(), origin, plane, width, height);
        return isComplete(level, frame) ? Optional.of(frame) : Optional.empty();
    }

    /** Whether {@code frame} stands: every interior block interior, every ring block frame. */
    public static boolean isComplete(LevelReader level, PortalFrame frame) {
        for (BlockPos pos : frame.interior()) {
            if (!isInterior(level.getBlockState(pos), frame.plane())) {
                return false;
            }
        }
        for (BlockPos pos : frame.ring()) {
            if (!isFrameBlock(level.getBlockState(pos), level, pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The frame beside the frame block at {@code framePos}: the block the clicked face looks at
     * is tried first, then the other five neighbours, each in the planes that span that direction,
     * upright before flat. The first complete frame wins.
     */
    public static Optional<PortalFrame> findAround(Level level, BlockPos framePos,
            Direction clickedFace) {
        Optional<PortalFrame> found = findBeside(level, framePos, clickedFace);
        for (Direction direction : Direction.values()) {
            if (found.isEmpty() && direction != clickedFace) {
                found = findBeside(level, framePos, direction);
            }
        }
        return found;
    }

    // The frame whose interior starts one block from framePos toward toInterior, in any plane.
    private static Optional<PortalFrame> findBeside(Level level, BlockPos framePos,
            Direction toInterior) {
        BlockPos pos = framePos.relative(toInterior);
        for (PortalPlane plane : PortalPlane.spanning(toInterior.getAxis())) {
            Optional<PortalFrame> frame = find(level, pos, plane);
            if (frame.isPresent()) {
                return frame;
            }
        }
        return Optional.empty();
    }

    // How many interior blocks follow `from` along `axis` in the direction of `sign`, at most
    // MAX_SIZE.
    private static int run(Level level, BlockPos from, PortalPlane plane, Direction.Axis axis,
            int sign) {
        Direction.AxisDirection toward =
                sign < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE;
        Direction step = Direction.get(toward, axis);
        BlockPos.MutableBlockPos pos = from.mutable();
        int count = 0;
        while (count < PortalFrame.MAX_SIZE
                && isInterior(level.getBlockState(pos.move(step)), plane)) {
            count++;
        }
        return count;
    }

    // Air, or an existing portal block of the same plane, so a lit frame is found again.
    private static boolean isInterior(BlockState state, PortalPlane plane) {
        return state.isAir() || PortalBlock.isPortal(state, plane);
    }
}
