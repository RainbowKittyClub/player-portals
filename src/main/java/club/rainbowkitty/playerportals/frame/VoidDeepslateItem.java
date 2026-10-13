package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.common.api.ScopedOverride;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.kalic0re.common.item.PolymerModelBlockItem;

/**
 * The frame block's item. A client holding a block item predicts placing that block, so each
 * client is sent an item whose prediction is right or absent. One without the pack holds crying
 * obsidian, the block it is then shown. One with the pack holds a trial key drawn with this
 * block's model, as Polymer's own block items are: had it held crying obsidian, it would show
 * that until the server's answer replaced it. A trial key predicts nothing, so that client neither
 * swings nor hears the place sound of its own accord, and the server sends it both.
 */
public final class VoidDeepslateItem extends PolymerModelBlockItem {
    /**
     * @param block the block placed
     * @param model the item-model definition id shown to clients with the pack
     * @param properties standard item properties, already carrying this item's id
     */
    public VoidDeepslateItem(Block block, Identifier model, Properties properties) {
        super(block, Items.CRYING_OBSIDIAN, model, properties);
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        return PolymerResourcePackUtils.hasMainPack(context) ? Items.TRIAL_KEY
                : super.getPolymerItem(stack, context);
    }

    // For a client with the pack, the place sound reaches the placer too, and a placement swings
    // from the server. Done here, it follows only a block this item placed: Polymer's own hooks
    // answer for any click the server takes, a door opened as much as a block placed, and ask
    // nothing on the last of a stack, spent by then. Polymer's override is a no-op when one of its
    // own is already on, so the sound is never sent twice.
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !PolymerResourcePackUtils.hasMainPack(player.connection)) {
            return super.place(context);
        }
        InteractionResult result;
        try (ScopedOverride heard = PolymerUtils.ignorePlaySoundExclusion()) {
            result = super.place(context);
        }
        return result instanceof InteractionResult.Success success
                ? new InteractionResult.Success(InteractionResult.SwingSource.SERVER,
                        success.itemContext())
                : result;
    }
}
