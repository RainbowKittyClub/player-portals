package club.rainbowkitty.playerportals.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
 * Where each lit portal leads, saved with the server. A two-way pair is two links; a one-way
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

    /** Registers the start-up sweep that brings links and lit frames back in step. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(PortalLinks::reconcile);
    }

    /** Where a portal in {@code frame} leads, if anywhere. */
    public static @Nullable PortalFrame destinationOf(MinecraftServer server, PortalFrame frame) {
        return of(server).destinations.get(frame);
    }

    /**
     * Lights {@code a} and {@code b}, both unlit and complete, as a two-way pair. False, with
     * neither lit, when one is no longer complete by its turn, as when the two cross.
     */
    public static boolean link(MinecraftServer server, PortalFrame a, PortalFrame b) {
        PortalLinks links = of(server);
        Portals portals = PlayerPortals.portals();
        // Lit first: lighting fades a stale record, which drops its old links, before the new.
        if (!portals.light(server, a)) {
            return false;
        }
        if (!portals.light(server, b)) {
            portals.fade(server, a);
            return false;
        }
        links.destinations.put(a, b);
        links.destinations.put(b, a);
        links.setDirty();
        return true;
    }

    /**
     * Lights the unlit, complete {@code source} as a one-way portal to {@code destination},
     * lighting the destination too unless it already stands lit. False, with nothing lit, when
     * either frame is no longer complete by its turn.
     */
    public static boolean linkOneWay(MinecraftServer server, PortalFrame source,
            PortalFrame destination) {
        PortalLinks links = of(server);
        ServerLevel destinationLevel = server.getLevel(destination.dimension());
        if (destinationLevel == null) {
            return false;
        }
        Portals portals = PlayerPortals.portals();
        if (!portals.light(server, source)) {
            return false;
        }
        boolean standing = portals.has(server, destination)
                && portals.finder().isLit(destinationLevel, destination);
        if (!standing && !portals.light(server, destination)) {
            portals.fade(server, source);
            return false;
        }
        links.destinations.put(source, destination);
        links.setDirty();
        return true;
    }

    /**
     * Drops every link into or out of {@code frame}, then fades each portal that was linked to
     * it and now has no link left.
     */
    public static void unlink(MinecraftServer server, PortalFrame frame) {
        PortalLinks links = of(server);
        PortalFrame onward = links.destinations.remove(frame);
        List<PortalFrame> sources = new ArrayList<>();
        links.destinations.entrySet().removeIf(link -> {
            if (link.getValue().equals(frame)) {
                sources.add(link.getKey());
                return true;
            }
            return false;
        });
        links.setDirty();
        Portals portals = PlayerPortals.portals();
        for (PortalFrame source : sources) {
            if (!links.isLinked(source)) {
                portals.fade(server, source);
            }
        }
        if (onward != null && !links.isLinked(onward)) {
            portals.fade(server, onward);
        }
    }

    // The server's links, created empty on first use.
    private static PortalLinks of(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // The start-up sweep: a link from or to an unlit frame goes; a lit frame no link reaches fades.
    private static void reconcile(MinecraftServer server) {
        PortalLinks links = of(server);
        Portals portals = PlayerPortals.portals();
        Set<PortalFrame> lit = portals.lit(server);
        if (links.destinations.entrySet().removeIf(
                link -> !lit.contains(link.getKey()) || !lit.contains(link.getValue()))) {
            links.setDirty();
        }
        for (PortalFrame frame : lit) {
            if (!links.isLinked(frame)) {
                portals.fade(server, frame);
            }
        }
    }

    // Whether any link enters or leaves frame.
    private boolean isLinked(PortalFrame frame) {
        return destinations.containsKey(frame) || destinations.containsValue(frame);
    }

    // The links as saved.
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
