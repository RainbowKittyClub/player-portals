package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.BlockResourceCreator;
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

import club.rainbowkitty.kalic0re.common.item.ItemNames;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
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

    private static VoidDeepslateBlock register(String path) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PlayerPortals.id(path));
        VoidDeepslateBlock block = Registry.register(BuiltInRegistries.BLOCK, key,
                new VoidDeepslateBlock(key,
                        textured(PolymerBlockModel.of(PlayerPortals.id("block/" + path)))));
        ItemRegistration.register(PlayerPortals.MOD_ID, path, itemKey -> ItemNames.named(itemKey,
                PlayerPortals.text().of("block." + PlayerPortals.MOD_ID + "." + path),
                new VoidDeepslateItem(block, PlayerPortals.id(path),
                        new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
        return block;
    }

    // The state the pack draws the block on. Polymer hands out note blocks first, which a client
    // hears as wood in its own steps, hits and placing, as do client mods that pick sounds by the
    // block they see. Its pool also holds infested deepslate, which sounds as deepslate; once a
    // state of it is taken, Polymer shows a real one in that state as chiseled deepslate. Only
    // a sideways one is asked for: infested deepslate generates upright, and only a creative
    // player placing one sideways would predict this state. (Cobbled deepslate's double slab
    // also sounds right, but a client would predict it whenever it doubles a real slab.) Any
    // full-block state if none is free, keeping back the last as Polymer's own requests do; null
    // when the pool is spent.
    private static @Nullable BlockState textured(PolymerBlockModel model) {
        synchronized (PolymerBlockResourceUtils.class) {
            BlockResourceCreator pool = PolymerBlockResourceUtilsAccessor.playerportals$creator();
            BlockState state = pool.hasRequestedEmpty(BlockModelType.FULL_BLOCK)
                    || pool.getBlocksLeft(BlockModelType.FULL_BLOCK) > 1
                    ? pool.requestBlock(BlockModelType.FULL_BLOCK,
                            pooled -> pooled.is(Blocks.INFESTED_DEEPSLATE)
                                    && pooled.getValue(RotatedPillarBlock.AXIS)
                                            != Direction.Axis.Y,
                            model)
                    : null;
            return state != null ? state
                    : PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, model);
        }
    }
}
