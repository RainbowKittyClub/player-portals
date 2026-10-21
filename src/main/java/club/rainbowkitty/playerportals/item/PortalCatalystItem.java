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
import club.rainbowkitty.playerportals.PortalPermissions;
import club.rainbowkitty.playerportals.portal.PortalLinks;

/**
 * Links two unlit frames of any shape, each at most {@link #MAX_SIZE} across, into a two-way
 * pair, and is used up doing it: a sneaking click attunes to the first, a plain click on the
 * second links them.
 */
public final class PortalCatalystItem extends CatalystItem {
    /** The most interior blocks across, on either axis, a frame this links may have. */
    public static final int MAX_SIZE = 10;

    public PortalCatalystItem(ResourceKey<Item> key, Properties properties) {
        super(key, properties, "item.playerportals.catalyst", ChatFormatting.GRAY);
    }

    @Override
    protected boolean permitted(ServerPlayer player) {
        return PortalPermissions.mayLink(player);
    }

    @Override
    protected int maxSize() {
        return MAX_SIZE;
    }

    // A lit frame is no use to link, so it fizzles here too.
    @Override
    protected InteractionResult onAttune(ServerLevel level, ServerPlayer player, ItemStack stack,
            PortalFrame frame, BlockPos clicked) {
        return hasPortal(level, frame) ? refuse(level, clicked) : attuneTo(level, stack, frame);
    }

    @Override
    protected InteractionResult onLink(ServerLevel level, ServerPlayer player, ItemStack stack,
            PortalFrame frame, BlockPos clicked) {
        PortalFrame first = stack.get(CatalystComponents.ATTUNED);
        if (first == null || first.equals(frame) || hasPortal(level, frame)) {
            return refuse(level, clicked);
        }
        // A frame attuned before the size limit, or one rebuilt larger since, starts over.
        ServerLevel firstLevel = standingLevel(level.getServer(), first);
        if (firstLevel == null || hasPortal(firstLevel, first) || first.width() > MAX_SIZE
                || first.height() > MAX_SIZE) {
            attune(stack, null);
            return refuse(level, clicked);
        }
        if (!PortalLinks.link(level.getServer(), first, frame)) {
            return refuse(level, clicked);
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }
}
