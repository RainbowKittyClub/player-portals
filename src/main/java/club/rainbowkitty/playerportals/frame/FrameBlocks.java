package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.kalic0re.common.item.ItemNames;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelBlockItem;
import club.rainbowkitty.playerportals.PlayerPortals;

/** The frame block and its item. */
public final class FrameBlocks {
    public static final VoidDeepslateBlock VOID_DEEPSLATE = register("void_infused_deepslate");

    private FrameBlocks() {
    }

    /** Forces registration; the field above does the work. */
    public static void init() {
    }

    private static VoidDeepslateBlock register(String path) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PlayerPortals.id(path));
        VoidDeepslateBlock block = Registry.register(BuiltInRegistries.BLOCK, key,
                new VoidDeepslateBlock(VoidDeepslateBlock.properties(key),
                        PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
                                PolymerBlockModel.of(PlayerPortals.id("block/" + path)))));
        ItemRegistration.register(PlayerPortals.MOD_ID, path, itemKey -> ItemNames.named(itemKey,
                PlayerPortals.text().of("block." + PlayerPortals.MOD_ID + "." + path),
                new PolymerModelBlockItem(block, Items.CRYING_OBSIDIAN, PlayerPortals.id(path),
                        new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        return block;
    }
}
