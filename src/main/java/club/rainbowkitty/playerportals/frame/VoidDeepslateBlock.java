package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * The block a frame is built from: deepslate with an ender pearl in it. A client with the
 * resource pack sees its own texture on a pool block; any other sees crying obsidian, which has
 * the same light, so neither client's own light recalculation disagrees with the server's.
 *
 * <p>It weeps as crying obsidian does. A client without the pack gets that from crying obsidian
 * itself; one with the pack is sent a teal drop now and then from an exposed face, by a block
 * tick each block keeps scheduling for itself (random ticks start it on blocks placed before this
 * was so), which costs nothing between drops.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    private static final int LIGHT = 10;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final int TEAR = 0x008888;
    // Ticks between a block's drops: 5 to 15 seconds, about crying obsidian's pace.
    private static final int DRIP_DELAY_MIN = 100;
    private static final int DRIP_DELAY_SPAN = 200;
    private static final int DRIP_TICKS = 20;
    private static final double DRIP_FALL = 1.2;
    private static final double DRIP_RANGE_SQUARED = 32.0 * 32.0;

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
                .lightLevel(state -> LIGHT).randomTicks().setId(key);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return context != null && PolymerResourcePackUtils.hasMainPack(context)
                ? textured : FALLBACK;
    }

    // Every client sees deepslate shatter: the pool block is a note block and would sound of wood.
    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
        return Blocks.DEEPSLATE.defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
            boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            scheduleDrip(serverLevel, pos, serverLevel.getRandom());
        }
    }

    // Starts the drips of a block placed before they existed; a block already ticking is
    // rescheduled harmlessly early.
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        scheduleDrip(level, pos, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        drip(level, pos, random);
        scheduleDrip(level, pos, random);
    }

    // One pending tick per block: a random tick landing on a block already ticking must not
    // start a second chain.
    private void scheduleDrip(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, DRIP_DELAY_MIN + random.nextInt(DRIP_DELAY_SPAN));
        }
    }

    // One drop from a random exposed face other than the top, as crying obsidian picks them,
    // sent to players with the pack within earshot: a trail particle falling from the face.
    private static void drip(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction face = Direction.getRandom(random);
        if (face == Direction.UP) {
            return;
        }
        BlockPos beside = pos.relative(face);
        if (level.getBlockState(beside).isFaceSturdy(level, beside, face.getOpposite())) {
            return;
        }
        double x = pos.getX() + (face.getStepX() == 0 ? random.nextDouble()
                : 0.5 + face.getStepX() * 0.55);
        double y = pos.getY() + (face.getStepY() == 0 ? random.nextDouble()
                : 0.5 + face.getStepY() * 0.55);
        double z = pos.getZ() + (face.getStepZ() == 0 ? random.nextDouble()
                : 0.5 + face.getStepZ() * 0.55);
        TrailParticleOption drop = new TrailParticleOption(new Vec3(x, y - DRIP_FALL, z), TEAR,
                DRIP_TICKS);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= DRIP_RANGE_SQUARED
                    && PolymerResourcePackUtils.hasMainPack(player.connection)) {
                level.sendParticles(player, drop, false, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
