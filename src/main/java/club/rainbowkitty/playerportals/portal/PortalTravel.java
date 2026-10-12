package club.rainbowkitty.playerportals.portal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.frame.FrameFinder;
import club.rainbowkitty.playerportals.frame.PortalFrame;

/**
 * Answers a crossing: which portal the entity stands in, whether both ends still stand as they
 * were lit, whether it fits and may go, and where it comes out. Each answer reads two frames,
 * once per crossing.
 */
public final class PortalTravel {
    private PortalTravel() {
    }

    /**
     * The transition for {@code entity}, standing in the portal block at {@code entryPos}, or
     * null when the portal leads nowhere, its ring has changed, or the far end is gone. A dead
     * end is removed through the registry, and a player is told why in chat.
     */
    public static @Nullable TeleportTransition destination(ServerLevel level, Entity entity,
            BlockPos entryPos) {
        MinecraftServer server = level.getServer();
        PortalRegistry registry = PortalRegistry.of(server);
        PortalFrame here = registry.portalAt(level.dimension(), entryPos);
        if (here == null) {
            // A portal block no portal owns, say placed by command: clear it.
            level.destroyBlock(entryPos, false);
            return refuse(entity, "portal.unlinked");
        }
        if (!FrameFinder.isLit(level, here)) {
            registry.remove(server, here);
            return refuse(entity, "portal.frame_changed");
        }
        PortalFrame to = registry.destinationOf(here);
        if (to == null) {
            return refuse(entity, "portal.unlinked");
        }
        ServerLevel toLevel = server.getLevel(to.dimension());
        if (toLevel == null || !registry.has(to) || !FrameFinder.isLit(toLevel, to)) {
            registry.remove(server, to);
            return refuse(entity, "portal.broken");
        }
        if (!PortalExit.fits(entity.getDimensions(entity.getPose()), to)) {
            return refuse(entity, "portal.too_small");
        }
        // Vanilla asks these after the destination and then drops the trip without a word.
        if (!level.isAllowedToEnterPortal(toLevel) || !entity.canTeleport(level, toLevel)) {
            return refuse(entity, "portal.cannot_travel");
        }
        return PortalExit.transition(entity, here, toLevel, to);
    }

    private static @Nullable TeleportTransition refuse(Entity entity, String key) {
        if (entity instanceof ServerPlayer player) {
            player.sendSystemMessage(PlayerPortals.text().of(PlayerPortals.MOD_ID + "." + key));
        }
        return null;
    }
}
