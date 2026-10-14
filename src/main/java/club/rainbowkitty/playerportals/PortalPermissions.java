package club.rainbowkitty.playerportals;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import club.rainbowkitty.kalic0re.common.command.CommandPermissions;

/**
 * The permission nodes, {@code playerportals.<what>}. Linking, breaking and crafting are open
 * until a permission mod says otherwise; the admin catalyst falls back to operator level 2.
 */
public final class PortalPermissions {
    private static final String LINK = PlayerPortals.MOD_ID + ".link";
    private static final String BREAK = PlayerPortals.MOD_ID + ".break";
    private static final String CRAFT = PlayerPortals.MOD_ID + ".craft";
    private static final String ADMIN = PlayerPortals.MOD_ID + ".admin";

    private PortalPermissions() {
    }

    /** Whether {@code player} may attune and link portals with a catalyst. */
    public static boolean mayLink(ServerPlayer player) {
        return CommandPermissions.holds(player, LINK, true);
    }

    /** Whether {@code player} may break a lit portal's frame or interior. */
    public static boolean mayBreak(ServerPlayer player) {
        return CommandPermissions.holds(player, BREAK, true);
    }

    /** Whether {@code player} may craft a catalyst. */
    public static boolean mayCraft(ServerPlayer player) {
        return CommandPermissions.holds(player, CRAFT, true);
    }

    /** Whether {@code player} may use the admin catalyst. */
    public static boolean isAdmin(ServerPlayer player) {
        return CommandPermissions.holds(player, ADMIN, PermissionLevel.GAMEMASTERS);
    }
}
