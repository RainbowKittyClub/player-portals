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

/** Links two unlit frames of any shape into a two-way pair, and is used up doing it. */
public final class PortalCatalystItem extends CatalystItem {
    public PortalCatalystItem(ResourceKey<Item> key, Properties properties) {
        super(key, "portal_catalyst", properties);
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
        if (!level.getBlockState(frame.origin()).isAir()) {
            say(player, "catalyst.already_lit");
            return InteractionResult.CONSUME;
        }
        PortalFrame first = stack.get(CatalystComponents.ATTUNED);
        if (first == null) {
            attune(stack, frame);
            PortalEffects.attuned(level, frame);
            say(player, "catalyst.attuned", describe(frame));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (first.equals(frame)) {
            say(player, "catalyst.same_frame");
            return InteractionResult.CONSUME;
        }
        ServerLevel firstLevel = level.getServer().getLevel(first.dimension());
        if (firstLevel == null || !PlayerPortals.portals().finder().isComplete(firstLevel, first)
                || !firstLevel.getBlockState(first.origin()).isAir()) {
            attune(stack, null);
            say(player, "catalyst.first_gone");
            return InteractionResult.CONSUME;
        }
        if (!PortalLinks.of(level.getServer()).link(level.getServer(), first, frame)) {
            say(player, "catalyst.crossed");
            return InteractionResult.CONSUME;
        }
        stack.consume(1, player);
        say(player, "catalyst.linked", describe(first));
        return InteractionResult.SUCCESS_SERVER;
    }
}
