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
 * The frame block's item. Each client holds the item of the block it is shown, so its own
 * prediction of a place is right: crying obsidian without the pack, the pool state's item with
 * it, drawn with this block's model and its {@code block_state} pinned to that exact state.
 */
public final class VoidDeepslateItem extends PolymerModelBlockItem {
    private final BlockState packState;
    // Every property of packState, which infested deepslate would otherwise take from the face.
    private final BlockItemStateProperties pinned;

    /**
     * @param block the block placed
     * @param model the item-model definition id shown to clients with the pack
     * @param properties standard item properties, already carrying this item's id
     */
    public VoidDeepslateItem(VoidDeepslateBlock block, Identifier model, Properties properties) {
        super(block, Items.CRYING_OBSIDIAN, model, properties);
        this.packState = block.packState();
        BlockItemStateProperties pinned = BlockItemStateProperties.EMPTY;
        for (Property<?> property : packState.getProperties()) {
            pinned = pinned.with(property, packState);
        }
        this.pinned = pinned;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        Item shown = packState.getBlock().asItem();
        return PolymerResourcePackUtils.hasMainPack(context) && shown != Items.AIR ? shown
                : super.getPolymerItem(stack, context);
    }

    @Override
    public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context,
            HolderLookup.Provider lookup) {
        super.modifyBasePolymerItemStack(out, stack, context, lookup);
        if (PolymerResourcePackUtils.hasMainPack(context)) {
            out.set(DataComponents.BLOCK_STATE, pinned);
        }
    }
}
