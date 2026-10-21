package club.rainbowkitty.playerportals.portal;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import club.rainbowkitty.kalic0re.common.portal.Portals;
import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.PortalPermissions;

/**
 * Keeps a lit portal's interior from every player, creative ones too, and its ring from players
 * without {@code playerportals.break}: only a frame block taken away ends a portal. Anything else
 * that breaks a frame block (a piston, an explosion, an enderman) collapses the portal as it
 * would a nether portal.
 */
public final class PortalProtection {
    private PortalProtection() {
    }

    /** Registers the break gate. */
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)
                    || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            Portals portals = PlayerPortals.portals();
            // The client takes the light or barrier it sees for a block it may break, as one
            // holding a light block does; it gets it back unbroken.
            if (state.is(portals.block())) {
                return false;
            }
            if (portals.portalOwning(serverLevel, pos) == null
                    || PortalPermissions.mayBreak(serverPlayer)) {
                return true;
            }
            PlayerPortals.say(serverPlayer, "break.no_permission");
            return false;
        });
    }
}
