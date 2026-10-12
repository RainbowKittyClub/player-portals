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
import club.rainbowkitty.playerportals.portal.PortalEffects;
import club.rainbowkitty.playerportals.portal.PortalLinks;

/**
 * Makes one-way portals: a sneaking click picks the destination, lit or not; each plain click
 * lights an unlit frame of any shape as a portal that leads there. Never used up.
 */
public final class AdminCatalystItem extends CatalystItem {
    public AdminCatalystItem(ResourceKey<Item> key, Properties properties) {
        super(key, "admin_portal_catalyst", properties);
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
            attune(stack, frame);
            PortalEffects.attuned(level, frame);
            say(player, "admin.destination_set", describe(frame));
            return InteractionResult.SUCCESS;
        }
        PortalFrame destination = stack.get(CatalystComponents.ATTUNED);
        if (destination == null) {
            say(player, "admin.no_destination");
            return InteractionResult.CONSUME;
        }
        if (destination.equals(frame)) {
            say(player, "catalyst.same_frame");
            return InteractionResult.CONSUME;
        }
        if (!level.getBlockState(frame.origin()).isAir()) {
            say(player, "admin.already_lit");
            return InteractionResult.CONSUME;
        }
        ServerLevel destinationLevel = level.getServer().getLevel(destination.dimension());
        if (destinationLevel == null
                || !PlayerPortals.portals().finder().isComplete(destinationLevel, destination)) {
            attune(stack, null);
            say(player, "catalyst.first_gone");
            return InteractionResult.CONSUME;
        }
        PortalLinks.of(level.getServer()).linkOneWay(level.getServer(), frame, destination);
        say(player, "admin.linked", describe(destination));
        return InteractionResult.SUCCESS;
    }
}
