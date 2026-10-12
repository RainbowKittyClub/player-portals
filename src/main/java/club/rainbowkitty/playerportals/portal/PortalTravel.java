package club.rainbowkitty.playerportals.portal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;

import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.frame.PortalFrame;

/**
 * Answers a crossing: which portal the entity stands in, whether both ends still stand as they
 * were lit, whether it fits and may go, and where it comes out. Each answer reads at most one
 * ring and one interior, once per crossing.
 */
public final class PortalTravel {
    private PortalTravel() {
    }

    /**
     * The transition for {@code entity}, standing in the portal block at {@code entryPos}, or
     * null when the portal leads nowhere, its ring has changed, or the far end is gone. A dead
     * end is removed through the registry, and a player is told why in the action bar.
     */
    public static @Nullable TeleportTransition destination(ServerLevel level, Entity entity,
            BlockPos entryPos) {
        MinecraftServer server = level.getServer();
        PortalRegistry registry = PortalRegistry.of(server);
        PortalRegistry.LitPortal here = registry.portalAt(level.dimension(), entryPos);
        if (here == null) {
            // A portal block no portal owns, say placed by command: clear it.
            level.setBlock(entryPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return refuse(entity, "portal.unlinked");
        }
        if (!here.signature().matches(level, here.frame())) {
            registry.remove(server, here.frame());
            return refuse(entity, "portal.frame_changed");
        }
        PortalFrame to = registry.destinationOf(here.frame());
        if (to == null) {
            return refuse(entity, "portal.unlinked");
        }
        ServerLevel toLevel = server.getLevel(to.dimension());
        PortalRegistry.LitPortal there = registry.get(to);
        if (toLevel == null || there == null || !PortalRegistry.isLit(toLevel, to)
                || !there.signature().matches(toLevel, to)) {
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
        return PortalExit.transition(entity, here.frame(), toLevel, to);
    }

    private static @Nullable TeleportTransition refuse(Entity entity, String key) {
        if (entity instanceof ServerPlayer player) {
            player.sendOverlayMessage(PlayerPortals.text().of(PlayerPortals.MOD_ID + "." + key));
        }
        return null;
    }
}
