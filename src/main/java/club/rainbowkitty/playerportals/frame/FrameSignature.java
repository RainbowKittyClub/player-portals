package club.rainbowkitty.playerportals.frame;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pattern a frame is built in: its ring's block ids in {@link PortalFrame#ring} order. Two
 * frames of one shape match when their signatures are equal, so a pattern that is not symmetric
 * has to face the same way at both ends.
 *
 * @param ring one block id per ring block, in ring order
 */
public record FrameSignature(List<Identifier> ring) {
    public static final Codec<FrameSignature> CODEC =
            Identifier.CODEC.listOf().xmap(FrameSignature::new, FrameSignature::ring);

    public FrameSignature {
        ring = List.copyOf(ring);
    }

    /** The signature of {@code frame} as its ring stands now. */
    public static FrameSignature read(BlockGetter level, PortalFrame frame) {
        List<Identifier> ids = new ArrayList<>(frame.ringSize());
        for (BlockPos pos : frame.ring()) {
            ids.add(idOf(level.getBlockState(pos)));
        }
        return new FrameSignature(ids);
    }

    /** Whether the ring of {@code frame} still reads as this signature. */
    public boolean matches(BlockGetter level, PortalFrame frame) {
        if (frame.ringSize() != ring.size()) {
            return false;
        }
        int i = 0;
        for (BlockPos pos : frame.ring()) {
            if (!ring.get(i++).equals(idOf(level.getBlockState(pos)))) {
                return false;
            }
        }
        return true;
    }

    /** The block id this signature holds for the ring block at {@code pos}; null off the ring. */
    public @Nullable Identifier at(PortalFrame frame, BlockPos pos) {
        int index = frame.ringIndex(pos);
        return index < 0 || index >= ring.size() ? null : ring.get(index);
    }

    /** The block id {@code state} reads as in a signature. */
    public static Identifier idOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }
}
