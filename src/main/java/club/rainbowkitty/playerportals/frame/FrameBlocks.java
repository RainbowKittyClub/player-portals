package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.mixin.PolymerBlockResourceUtilsAccessor;

/** The frame block and its item. */
public final class FrameBlocks {
    public static final VoidDeepslateBlock VOID_DEEPSLATE = register("void_infused_deepslate");

    private FrameBlocks() {
    }

    /** Forces registration, which the field above does, and forgets drops at server stop. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VoidDeepslateBlock.forgetDrops());
    }

    // Registers the block and its item under path.
    private static VoidDeepslateBlock register(String path) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PlayerPortals.id(path));
        VoidDeepslateBlock block = Registry.register(BuiltInRegistries.BLOCK, key,
                new VoidDeepslateBlock(key,
                        textured(PolymerBlockModel.of(PlayerPortals.id("block/" + path)))));
        PlayerPortals.registerItem(path, itemKey -> new VoidDeepslateItem(block,
                itemKey.identifier(), new Item.Properties().setId(itemKey)
                        .useBlockDescriptionPrefix()));
        return block;
    }

    // Sideways infested deepslate from Polymer's pool: it sounds as deepslate and generates
    // upright. Else any full-block state; null when the pool is spent.
    private static @Nullable BlockState textured(PolymerBlockModel model) {
        synchronized (PolymerBlockResourceUtils.class) {
            if (PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.FULL_BLOCK) > 0) {
                BlockState state = PolymerBlockResourceUtilsAccessor.playerportals$creator()
                        .requestBlock(BlockModelType.FULL_BLOCK,
                                pooled -> pooled.is(Blocks.INFESTED_DEEPSLATE)
                                        && pooled.getValue(RotatedPillarBlock.AXIS)
                                                != Direction.Axis.Y,
                                model);
                if (state != null) {
                    return state;
                }
            }
            return PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, model);
        }
    }
}
