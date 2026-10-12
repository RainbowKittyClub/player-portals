package club.rainbowkitty.playerportals.portal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.frame.FrameFinder;
import club.rainbowkitty.playerportals.frame.PortalFrame;

/**
 * Every lit portal and where each one leads, saved with the overworld. A two-way pair is two
 * links; a one-way portal is one. A portal stays lit while any link enters or leaves it, so the
 * far end of one-way portals stays as a destination with no way out.
 *
 * <p>Lookups by block go through an index from chunk to the portals whose ring or interior
 * touches it, rebuilt on load, so neither a crossing nor a block break reads the world to find
 * its portal. A portal that died while its chunks were unloaded is cleared when they load.
 */
public final class PortalRegistry extends SavedData {
    private static final Codec<PortalRegistry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(PortalFrame.CODEC.listOf().fieldOf("portals")
                            .forGetter(registry -> List.copyOf(registry.portals)),
                    Link.CODEC.listOf().fieldOf("links").forGetter(PortalRegistry::links),
                    PortalFrame.CODEC.listOf().fieldOf("pending")
                            .forGetter(registry -> List.copyOf(registry.pending)))
            .apply(instance, PortalRegistry::new));
    // Command storage is the vanilla saved-data type with no fixer that reads into its contents.
    private static final SavedDataType<PortalRegistry> TYPE = new SavedDataType<>(
            PlayerPortals.id("portals"), PortalRegistry::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Set<PortalFrame> portals = new HashSet<>();
    private final Map<PortalFrame, PortalFrame> destinations = new HashMap<>();
    private final Map<ResourceKey<Level>, Long2ObjectMap<List<PortalFrame>>> byChunk =
            new HashMap<>();
    // Dead portals whose blocks still stand in chunks that were not loaded when they died.
    private final Set<PortalFrame> pending = new HashSet<>();

    private PortalRegistry() {
    }

    private PortalRegistry(List<PortalFrame> lit, List<Link> links, List<PortalFrame> pending) {
        lit.forEach(this::add);
        for (Link link : links) {
            destinations.put(link.from(), link.to());
        }
        this.pending.addAll(pending);
    }

    /** The server's registry, created empty on first use. */
    public static PortalRegistry of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Clears dead portals as their chunks load. */
    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newChunk) ->
                of(level.getServer()).onChunkLoad(level, chunk.getPos()));
    }

    /** Whether a lit portal stands in {@code frame}. */
    public boolean has(PortalFrame frame) {
        return portals.contains(frame);
    }

    /** The lit portal whose interior holds {@code pos}, if any. */
    public @Nullable PortalFrame portalAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (PortalFrame frame : candidates(dimension, pos)) {
            if (frame.interiorContains(pos)) {
                return frame;
            }
        }
        return null;
    }

    /** The lit portal whose interior or ring holds {@code pos}, if any. */
    public @Nullable PortalFrame portalOwning(ResourceKey<Level> dimension, BlockPos pos) {
        for (PortalFrame frame : candidates(dimension, pos)) {
            if (frame.interiorContains(pos) || frame.ringContains(pos)) {
                return frame;
            }
        }
        return null;
    }

    /** Where a portal in {@code frame} leads, if anywhere. */
    public @Nullable PortalFrame destinationOf(PortalFrame frame) {
        return destinations.get(frame);
    }

    /** Lights {@code a} and {@code b}, both unlit, as a two-way pair. */
    public void link(MinecraftServer server, PortalFrame a, PortalFrame b) {
        forgetStale(server, a);
        forgetStale(server, b);
        add(a);
        add(b);
        destinations.put(a, b);
        destinations.put(b, a);
        setDirty();
        light(server, a);
        light(server, b);
    }

    /**
     * Lights the unlit {@code source} as a one-way portal to {@code destination}, lighting the
     * destination too when it is not a portal yet.
     */
    public void linkOneWay(MinecraftServer server, PortalFrame source, PortalFrame destination) {
        ServerLevel destinationLevel = server.getLevel(destination.dimension());
        if (destinationLevel == null) {
            return;
        }
        forgetStale(server, source);
        if (portals.contains(destination) && !FrameFinder.isLit(destinationLevel, destination)) {
            remove(server, destination);
        }
        add(source);
        if (!portals.contains(destination)) {
            add(destination);
            light(server, destination);
        }
        destinations.put(source, destination);
        setDirty();
        light(server, source);
    }

    /**
     * Forgets the portal in {@code frame} and every link into or out of it, then every portal
     * left without a link, and clears all their blocks on a following tick, or when their chunks
     * next load. Deferred so a removal that starts inside a block update touches no other chunk.
     * Each dead portal in loaded chunks fades audibly now, whichever path clears its blocks.
     */
    public void remove(MinecraftServer server, PortalFrame frame) {
        List<PortalFrame> dead = new ArrayList<>();
        Deque<PortalFrame> queue = new ArrayDeque<>();
        queue.add(frame);
        while (!queue.isEmpty()) {
            PortalFrame gone = queue.poll();
            if (portals.remove(gone)) {
                unindex(gone);
                dead.add(gone);
            }
            PortalFrame onward = destinations.remove(gone);
            Iterator<Map.Entry<PortalFrame, PortalFrame>> incoming =
                    destinations.entrySet().iterator();
            while (incoming.hasNext()) {
                Map.Entry<PortalFrame, PortalFrame> link = incoming.next();
                if (link.getValue().equals(gone)) {
                    incoming.remove();
                    if (!isLinked(link.getKey())) {
                        queue.add(link.getKey());
                    }
                }
            }
            if (onward != null && !isLinked(onward)) {
                queue.add(onward);
            }
        }
        if (dead.isEmpty()) {
            return;
        }
        // Pending from now, so a stop before the task runs still clears them on the next load.
        pending.addAll(dead);
        setDirty();
        for (PortalFrame deadFrame : dead) {
            ServerLevel level = server.getLevel(deadFrame.dimension());
            if (level != null && isLoaded(level, deadFrame)) {
                PortalEffects.faded(level, deadFrame);
            }
        }
        server.schedule(new TickTask(server.getTickCount(),
                () -> dead.forEach(deadFrame -> clearIfDead(server, deadFrame))));
    }

    /** Tells the registry one block of a portal at {@code pos} is gone, so the portal is. */
    public void onPortalBlockRemoved(ServerLevel level, BlockPos pos) {
        PortalFrame frame = portalAt(level.dimension(), pos);
        if (frame != null) {
            remove(level.getServer(), frame);
        }
    }

    // A record whose blocks a command cleared without the removal hook, or that a crash left
    // behind, must not keep its old links once the frame is lit again.
    private void forgetStale(MinecraftServer server, PortalFrame frame) {
        if (portals.contains(frame)) {
            remove(server, frame);
        }
    }

    // Dead portals in this chunk are cleared on a following tick, once the chunk is in the world.
    private void onChunkLoad(ServerLevel level, ChunkPos chunk) {
        if (pending.isEmpty()) {
            return;
        }
        long key = chunk.pack();
        for (PortalFrame frame : pending) {
            if (frame.dimension().equals(level.dimension()) && touches(frame, key)) {
                level.getServer().schedule(new TickTask(level.getServer().getTickCount(),
                        () -> clearIfDead(level.getServer(), frame)));
            }
        }
    }

    // Clears the blocks of a dead portal whose chunks are loaded; one relit since stays, one in
    // unloaded chunks waits for them.
    private void clearIfDead(MinecraftServer server, PortalFrame frame) {
        if (portals.contains(frame)) {
            pending.remove(frame);
            return;
        }
        ServerLevel level = server.getLevel(frame.dimension());
        if (level == null || isLoaded(level, frame)) {
            if (pending.remove(frame)) {
                setDirty();
            }
            if (level != null) {
                unlight(level, frame);
            }
        }
    }

    private boolean isLinked(PortalFrame frame) {
        return destinations.containsKey(frame) || destinations.containsValue(frame);
    }

    private List<Link> links() {
        List<Link> links = new ArrayList<>(destinations.size());
        destinations.forEach((from, to) -> links.add(new Link(from, to)));
        return links;
    }

    private void add(PortalFrame frame) {
        portals.add(frame);
        Long2ObjectMap<List<PortalFrame>> chunks = byChunk.computeIfAbsent(frame.dimension(),
                dimension -> new Long2ObjectOpenHashMap<>());
        for (long chunk : frame.chunks()) {
            chunks.computeIfAbsent(chunk, key -> new ArrayList<>(1)).add(frame);
        }
    }

    private void unindex(PortalFrame frame) {
        Long2ObjectMap<List<PortalFrame>> chunks = byChunk.get(frame.dimension());
        if (chunks == null) {
            return;
        }
        for (long chunk : frame.chunks()) {
            List<PortalFrame> inChunk = chunks.get(chunk);
            if (inChunk != null && inChunk.remove(frame) && inChunk.isEmpty()) {
                chunks.remove(chunk);
            }
        }
    }

    private List<PortalFrame> candidates(ResourceKey<Level> dimension, BlockPos pos) {
        Long2ObjectMap<List<PortalFrame>> chunks = byChunk.get(dimension);
        List<PortalFrame> inChunk = chunks == null ? null : chunks.get(ChunkPos.pack(pos));
        return inChunk == null ? List.of() : inChunk;
    }

    private static boolean touches(PortalFrame frame, long chunk) {
        for (long candidate : frame.chunks()) {
            if (candidate == chunk) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoaded(ServerLevel level, PortalFrame frame) {
        for (long chunk : frame.chunks()) {
            if (!level.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
                return false;
            }
        }
        return true;
    }

    // Set without shape updates, as vanilla lights a nether portal: a half-filled interior would
    // otherwise clear itself.
    private static void light(MinecraftServer server, PortalFrame frame) {
        ServerLevel level = server.getLevel(frame.dimension());
        if (level == null) {
            return;
        }
        BlockState state = PortalBlock.stateFor(frame.plane());
        for (BlockPos pos : frame.interior()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        PortalEffects.lit(level, frame);
    }

    // Destroyed, not set to air, so every block shatters as the ones the cascade takes do.
    private static void unlight(ServerLevel level, PortalFrame frame) {
        for (BlockPos pos : frame.interior()) {
            if (PortalBlock.isPortal(level.getBlockState(pos), frame.plane())) {
                level.destroyBlock(pos, false);
            }
        }
    }

    // One direction of travel, as saved.
    private record Link(PortalFrame from, PortalFrame to) {
        private static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(PortalFrame.CODEC.fieldOf("from").forGetter(Link::from),
                        PortalFrame.CODEC.fieldOf("to").forGetter(Link::to))
                .apply(instance, Link::new));
    }
}
