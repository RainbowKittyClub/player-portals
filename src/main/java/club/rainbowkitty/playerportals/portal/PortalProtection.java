package club.rainbowkitty.playerportals.portal;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import club.rainbowkitty.kalic0re.common.portal.Portals;
import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.PortalPermissions;

/**
 * Keeps a lit portal's blocks, interior and ring, from players without
 * {@code playerportals.break}. Anything else that breaks a frame block (a piston, an explosion,
 * an enderman) collapses the portal as it would a nether portal.
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
            boolean portal = state.is(portals.block())
                    || portals.portalOwning(serverLevel, pos) != null;
            if (!portal || PortalPermissions.mayBreak(serverPlayer)) {
                return true;
            }
            serverPlayer.sendSystemMessage(
                    PlayerPortals.text().of(PlayerPortals.MOD_ID + ".break.no_permission"));
            return false;
        });
    }
}
