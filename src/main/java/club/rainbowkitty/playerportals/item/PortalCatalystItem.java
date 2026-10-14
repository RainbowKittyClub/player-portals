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

/** Links two unlit frames of any shape into a two-way pair, and is used up doing it. */
public final class PortalCatalystItem extends CatalystItem {
    public PortalCatalystItem(ResourceKey<Item> key, Properties properties) {
        super(key, properties);
    }

    @Override
    protected boolean permitted(ServerPlayer player) {
        return PortalPermissions.mayLink(player);
    }

    @Override
    protected String deniedKey() {
        return "catalyst.no_permission";
    }

    @Override
    protected InteractionResult onFrame(ServerLevel level, ServerPlayer player, ItemStack stack,
            PortalFrame frame) {
        if (hasPortal(level, frame)) {
            return refuse(player, "catalyst.already_lit");
        }
        PortalFrame first = stack.get(CatalystComponents.ATTUNED);
        if (first == null) {
            return attuneTo(level, player, stack, frame, "catalyst.attuned");
        }
        if (first.equals(frame)) {
            return refuse(player, "catalyst.same_frame");
        }
        ServerLevel firstLevel = standingLevel(level.getServer(), first);
        if (firstLevel == null || hasPortal(firstLevel, first)) {
            attune(stack, null);
            return refuse(player, "catalyst.first_gone");
        }
        if (!PortalLinks.link(level.getServer(), first, frame)) {
            return refuse(player, "catalyst.crossed");
        }
        stack.consume(1, player);
        PlayerPortals.say(player, "catalyst.linked", describe(first));
        return InteractionResult.SUCCESS_SERVER;
    }
}
