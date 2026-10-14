package club.rainbowkitty.playerportals.item;

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
 * lights an unlit frame of any shape as a portal that leads there. Never used up.
 */
public final class AdminCatalystItem extends CatalystItem {
    public AdminCatalystItem(ResourceKey<Item> key, Properties properties) {
        super(key, properties);
    }

    @Override
    protected boolean permitted(ServerPlayer player) {
        return PortalPermissions.isAdmin(player);
    }

    @Override
    protected String deniedKey() {
        return "admin.no_permission";
    }

    @Override
    protected InteractionResult onFrame(ServerLevel level, ServerPlayer player, ItemStack stack,
            PortalFrame frame) {
        if (player.isShiftKeyDown()) {
            return attuneTo(level, player, stack, frame, "admin.destination_set");
        }
        PortalFrame destination = stack.get(CatalystComponents.ATTUNED);
        if (destination == null) {
            return refuse(player, "admin.no_destination");
        }
        if (destination.equals(frame)) {
            return refuse(player, "catalyst.same_frame");
        }
        if (hasPortal(level, frame)) {
            return refuse(player, "admin.already_lit");
        }
        if (standingLevel(level.getServer(), destination) == null) {
            attune(stack, null);
            return refuse(player, "catalyst.first_gone");
        }
        if (!PortalLinks.linkOneWay(level.getServer(), frame, destination)) {
            return refuse(player, "catalyst.crossed");
        }
        PlayerPortals.say(player, "admin.linked", describe(destination));
        return InteractionResult.SUCCESS_SERVER;
    }
}
