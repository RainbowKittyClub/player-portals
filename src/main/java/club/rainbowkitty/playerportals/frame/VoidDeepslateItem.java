package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import club.rainbowkitty.kalic0re.common.item.PolymerModelBlockItem;

/**
 * The frame block's item. A client holding a block item predicts placing that block: it puts the
 * block into its own world, plays its place sound and swings, and the server's answer replaces
 * whatever it guessed. So each client holds the item of the block it is shown, placing exactly
 * that. One without the pack holds crying obsidian. One with the pack holds the pool block's own
 * item, drawn with this block's model, its {@code block_state} pinning every property of the state
 * the pack draws this block on: infested deepslate would take its axis from the face clicked.
 * Held as a block item, it is also lit and heard by client mods as that block.
 */
public final class VoidDeepslateItem extends PolymerModelBlockItem {
    private final VoidDeepslateBlock frame;

    /**
     * @param block the block placed
     * @param model the item-model definition id shown to clients with the pack
     * @param properties standard item properties, already carrying this item's id
     */
    public VoidDeepslateItem(VoidDeepslateBlock block, Identifier model, Properties properties) {
        super(block, Items.CRYING_OBSIDIAN, model, properties);
        this.frame = block;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        Item shown = frame.packState().getBlock().asItem();
        return PolymerResourcePackUtils.hasMainPack(context) && shown != Items.AIR ? shown
                : super.getPolymerItem(stack, context);
    }

    @Override
    public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context,
            HolderLookup.Provider lookup) {
        super.modifyBasePolymerItemStack(out, stack, context, lookup);
        if (PolymerResourcePackUtils.hasMainPack(context)) {
            BlockState state = frame.packState();
            BlockItemStateProperties pinned = BlockItemStateProperties.EMPTY;
            for (Property<?> property : state.getProperties()) {
                pinned = pin(pinned, property, state);
            }
            out.set(DataComponents.BLOCK_STATE, pinned);
        }
    }

    private static <T extends Comparable<T>> BlockItemStateProperties pin(
            BlockItemStateProperties pinned, Property<T> property, BlockState state) {
        return pinned.with(property, state);
    }
}
