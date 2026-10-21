package club.rainbowkitty.playerportals.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import club.rainbowkitty.kalic0re.common.portal.PortalFrame;
import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.PortalPermissions;
import club.rainbowkitty.playerportals.portal.PortalLinks;

/**
 * Makes one-way portals: a sneaking click picks the destination, lit or not; each plain click
 * lights an unlit frame as a portal that leads there. Frames of any shape, each at most
 * {@link #MAX_SIZE} across. Never used up.
 */
public final class AdminCatalystItem extends CatalystItem {
    /** The most interior blocks across, on either axis, a frame this uses may have. */
    public static final int MAX_SIZE = 32;

    public AdminCatalystItem(ResourceKey<Item> key, Properties properties) {
        super(key, properties, "item.playerportals.admin_portal_catalyst", ChatFormatting.YELLOW);
    }

    @Override
    protected boolean permitted(ServerPlayer player) {
        return PortalPermissions.isAdmin(player);
    }

    @Override
    protected int maxSize() {
        return MAX_SIZE;
    }

    @Override
    protected InteractionResult onAttune(ServerLevel level, ServerPlayer player, ItemStack stack,
            PortalFrame frame, BlockPos clicked) {
        return attuneTo(level, stack, frame);
    }

    @Override
    protected InteractionResult onLink(ServerLevel level, ServerPlayer player, ItemStack stack,
            PortalFrame frame, BlockPos clicked) {
        PortalFrame destination = stack.get(CatalystComponents.ATTUNED);
        if (destination == null || destination.equals(frame) || hasPortal(level, frame)) {
            return refuse(level, clicked);
        }
        if (standingLevel(level.getServer(), destination) == null) {
            attune(stack, null);
            return refuse(level, clicked);
        }
        if (!PortalLinks.linkOneWay(level.getServer(), frame, destination)) {
            return refuse(level, clicked);
        }
        PlayerPortals.say(player, "admin.linked", origin(destination));
        return InteractionResult.SUCCESS_SERVER;
    }
}
