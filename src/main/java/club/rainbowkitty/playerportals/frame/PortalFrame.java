package club.rainbowkitty.playerportals.frame;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Where a portal stands: a rectangle of interior blocks and the ring of frame blocks one block
 * out around it. The four corners are not part of the ring, as they are not of a nether portal's:
 * they touch no interior block, so no shape update from them could ever reach the portal.
 *
 * @param dimension the level the frame is in
 * @param origin the interior block with the smallest coordinate on both in-plane axes
 * @param plane the plane the frame lies in
 * @param width interior blocks along {@link PortalPlane#along}, 1 to {@link #MAX_SIZE}
 * @param height interior blocks along {@link PortalPlane#up}, 1 to {@link #MAX_SIZE}
 */
public record PortalFrame(ResourceKey<Level> dimension, BlockPos origin, PortalPlane plane,
        int width, int height) {
    public static final int MAX_SIZE = 21;
    public static final Codec<PortalFrame> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(PortalFrame::dimension),
                    BlockPos.CODEC.fieldOf("origin").forGetter(PortalFrame::origin),
                    PortalPlane.CODEC.fieldOf("plane").forGetter(PortalFrame::plane),
                    Codec.intRange(1, MAX_SIZE).fieldOf("width").forGetter(PortalFrame::width),
                    Codec.intRange(1, MAX_SIZE).fieldOf("height").forGetter(PortalFrame::height))
            .apply(instance, PortalFrame::new));

    public PortalFrame {
        if (width < 1 || width > MAX_SIZE || height < 1 || height > MAX_SIZE) {
            throw new IllegalArgumentException("PortalFrame sides must be 1.." + MAX_SIZE
                    + ", got width=" + width + " height=" + height);
        }
    }

    /**
     * The block {@code u} along and {@code v} up from the origin; -1 or the size on either lands
     * on the ring.
     */
    public BlockPos at(int u, int v) {
        return origin.relative(plane.along(), u).relative(plane.up(), v);
    }

    /** Every interior block, as reused mutable positions. */
    public Iterable<BlockPos> interior() {
        return BlockPos.betweenClosed(origin, at(width - 1, height - 1));
    }

    /**
     * The ring in signature order: along the near side from the origin, up the far side, back
     * along the top and down the near side.
     */
    public List<BlockPos> ring() {
        List<BlockPos> ring = new ArrayList<>(ringSize());
        for (int u = 0; u < width; u++) {
            ring.add(at(u, -1));
        }
        for (int v = 0; v < height; v++) {
            ring.add(at(width, v));
        }
        for (int u = width - 1; u >= 0; u--) {
            ring.add(at(u, height));
        }
        for (int v = height - 1; v >= 0; v--) {
            ring.add(at(-1, v));
        }
        return ring;
    }

    /** How many blocks the ring has. */
    public int ringSize() {
        return 2 * (width + height);
    }

    /** Whether {@code pos} is one of the interior blocks. */
    public boolean interiorContains(BlockPos pos) {
        return inPlane(pos) && inside(along(pos), width) && inside(up(pos), height);
    }

    /** Whether {@code pos} is one of the ring blocks. */
    public boolean ringContains(BlockPos pos) {
        int u = along(pos);
        int v = up(pos);
        return inPlane(pos) && (inside(u, width) && (v == -1 || v == height)
                || inside(v, height) && (u == -1 || u == width));
    }

    /** Where {@code pos} falls in {@link #ring} order, or -1 when it is not on the ring. */
    public int ringIndex(BlockPos pos) {
        if (!ringContains(pos)) {
            return -1;
        }
        int u = along(pos);
        int v = up(pos);
        if (v == -1) {
            return u;
        }
        if (u == width) {
            return width + v;
        }
        if (v == height) {
            return width + height + (width - 1 - u);
        }
        return 2 * width + height + (height - 1 - v);
    }

    /** Whether this frame's plane, width and height equal {@code other}'s. */
    public boolean sameShape(PortalFrame other) {
        return plane == other.plane && width == other.width && height == other.height;
    }

    /** The chunks the ring and interior touch, as {@link ChunkPos#pack(int, int)} keys. */
    public long[] chunks() {
        BlockPos low = at(-1, -1);
        BlockPos high = at(width, height);
        int x0 = SectionPos.blockToSectionCoord(Math.min(low.getX(), high.getX()));
        int x1 = SectionPos.blockToSectionCoord(Math.max(low.getX(), high.getX()));
        int z0 = SectionPos.blockToSectionCoord(Math.min(low.getZ(), high.getZ()));
        int z1 = SectionPos.blockToSectionCoord(Math.max(low.getZ(), high.getZ()));
        long[] chunks = new long[(x1 - x0 + 1) * (z1 - z0 + 1)];
        int i = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                chunks[i++] = ChunkPos.pack(x, z);
            }
        }
        return chunks;
    }

    private boolean inPlane(BlockPos pos) {
        return pos.get(plane.normal()) == origin.get(plane.normal());
    }

    private int along(BlockPos pos) {
        return pos.get(plane.along()) - origin.get(plane.along());
    }

    private int up(BlockPos pos) {
        return pos.get(plane.up()) - origin.get(plane.up());
    }

    private static boolean inside(int offset, int size) {
        return offset >= 0 && offset < size;
    }
}
