package club.rainbowkitty.playerportals.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import club.rainbowkitty.kalic0re.common.portal.PortalFrame;
import club.rainbowkitty.kalic0re.common.portal.Portals;
import club.rainbowkitty.playerportals.PlayerPortals;

/**
 * Where each lit portal leads, saved with the overworld. A two-way pair is two links; a one-way
 * portal is one. A portal stays lit while any link enters or leaves it, so the far end of
 * one-way portals stays as a destination with no way out; one left without a link is faded
 * through the framework, which comes back here to drop its links in turn.
 */
public final class PortalLinks extends SavedData {
    private static final Codec<PortalLinks> CODEC = Link.CODEC.listOf()
            .xmap(PortalLinks::new, PortalLinks::links);
    // Command storage is the vanilla saved-data type with no fixer that reads into its contents.
    private static final SavedDataType<PortalLinks> TYPE = new SavedDataType<>(
            PlayerPortals.id("links"), PortalLinks::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<PortalFrame, PortalFrame> destinations = new HashMap<>();

    private PortalLinks() {
    }

    private PortalLinks(List<Link> links) {
        for (Link link : links) {
            destinations.put(link.from(), link.to());
        }
    }

    /** The server's links, created empty on first use. */
    public static PortalLinks of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Where a portal in {@code frame} leads, if anywhere. */
    public @Nullable PortalFrame destinationOf(PortalFrame frame) {
        return destinations.get(frame);
    }

    /** Lights {@code a} and {@code b}, both unlit and complete, as a two-way pair. */
    public void link(MinecraftServer server, PortalFrame a, PortalFrame b) {
        Portals portals = PlayerPortals.portals();
        // Lit first: lighting fades a stale record, which drops its old links, before the new.
        portals.light(server, a);
        portals.light(server, b);
        destinations.put(a, b);
        destinations.put(b, a);
        setDirty();
    }

    /**
     * Lights the unlit, complete {@code source} as a one-way portal to {@code destination},
     * lighting the destination too unless it already stands lit.
     */
    public void linkOneWay(MinecraftServer server, PortalFrame source, PortalFrame destination) {
        ServerLevel destinationLevel = server.getLevel(destination.dimension());
        if (destinationLevel == null) {
            return;
        }
        Portals portals = PlayerPortals.portals();
        portals.light(server, source);
        if (!portals.has(server, destination)
                || !portals.finder().isLit(destinationLevel, destination)) {
            portals.light(server, destination);
        }
        destinations.put(source, destination);
        setDirty();
    }

    /**
     * Drops every link into or out of {@code frame}, then fades each portal that was linked to
     * it and now has no link left.
     */
    public void unlink(MinecraftServer server, PortalFrame frame) {
        PortalFrame onward = destinations.remove(frame);
        List<PortalFrame> sources = new ArrayList<>();
        destinations.entrySet().removeIf(link -> {
            if (link.getValue().equals(frame)) {
                sources.add(link.getKey());
                return true;
            }
            return false;
        });
        setDirty();
        Portals portals = PlayerPortals.portals();
        for (PortalFrame source : sources) {
            if (!isLinked(source)) {
                portals.fade(server, source);
            }
        }
        if (onward != null && !isLinked(onward)) {
            portals.fade(server, onward);
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

    // One direction of travel, as saved.
    private record Link(PortalFrame from, PortalFrame to) {
        private static final Codec<Link> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(PortalFrame.CODEC.fieldOf("from").forGetter(Link::from),
                        PortalFrame.CODEC.fieldOf("to").forGetter(Link::to))
                .apply(instance, Link::new));
    }
}
