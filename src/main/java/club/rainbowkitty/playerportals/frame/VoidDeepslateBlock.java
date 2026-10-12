package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The block a frame is built from: deepslate with an ender pearl in it. A client with the
 * resource pack sees its own texture on a pool block; any other sees crying obsidian, which has
 * the same light, so neither client's own light recalculation disagrees with the server's.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    private static final int LIGHT = 10;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();

    private final BlockState textured;

    /**
     * @param textured the pool state the pack retextures, or null when the pool is exhausted, in
     *        which case every client sees the fallback
     */
    public VoidDeepslateBlock(Properties properties, @Nullable BlockState textured) {
        super(properties);
        this.textured = textured == null ? FALLBACK : textured;
    }

    /** Deepslate's properties under {@code key}, glowing like crying obsidian. */
    public static Properties properties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                .requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE)
                .lightLevel(state -> LIGHT).setId(key);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return context != null && PolymerResourcePackUtils.hasMainPack(context)
                ? textured : FALLBACK;
    }
}
