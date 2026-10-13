package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * was so), which costs nothing between drops. Each drop lands with a soft drip, which crying
 * obsidian's do not, sent to the same players.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    private static final int LIGHT = 10;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final int TEAR = 0x008888;
    // Ticks between a block's tries at a drop: 2 to 6 seconds, a third of which find a bare face.
    private static final int DRIP_DELAY_MIN = 40;
    private static final int DRIP_DELAY_SPAN = 80;
    private static final int DRIP_TICKS = 25;
    private static final double DRIP_FALL = 1.5;
    private static final float BEAD_SIZE = 0.7F;
    private static final double DRIP_RANGE_SQUARED = 32.0 * 32.0;
    private static final float DRIP_VOLUME = 0.3F;
    private static final float DRIP_PITCH = 0.7F;
    // Blocks whose drop is in the air: their next tick is its landing, not another drop. A
    // restart empties it, which turns a landing into a drop, and no more.
    private static final LongSet FALLING = new LongOpenHashSet();

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

    // Two ticks per drop: one releases it and comes back when it lands, which plays the drip
    // and schedules the next.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (FALLING.remove(pos.asLong())) {
            land(level, pos, random);
            scheduleDrip(level, pos, random);
        } else if (drip(level, pos, random)) {
            FALLING.add(pos.asLong());
            level.scheduleTick(pos, this, DRIP_TICKS);
        } else {
            scheduleDrip(level, pos, random);
        }
    }

    // One pending tick per block: a random tick landing on a block already ticking must not
    // start a second chain.
    private void scheduleDrip(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, DRIP_DELAY_MIN + random.nextInt(DRIP_DELAY_SPAN));
        }
    }

    // One drop from a random exposed face other than the top, as crying obsidian picks them,
    // sent to players with the pack within earshot: a bead of dust that clings to the face a
    // moment, and a trail that falls from it. No vanilla drip particle takes a colour. False
    // when the face drawn was covered or the top.
    private static boolean drip(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction face = Direction.getRandom(random);
        if (face == Direction.UP) {
            return false;
        }
        BlockPos beside = pos.relative(face);
        if (level.getBlockState(beside).isFaceSturdy(level, beside, face.getOpposite())) {
            return false;
        }
        double x = pos.getX() + (face.getStepX() == 0 ? random.nextDouble()
                : 0.5 + face.getStepX() * 0.55);
        double y = pos.getY() + (face.getStepY() == 0 ? random.nextDouble()
                : 0.5 + face.getStepY() * 0.55);
        double z = pos.getZ() + (face.getStepZ() == 0 ? random.nextDouble()
                : 0.5 + face.getStepZ() * 0.55);
        DustParticleOptions bead = new DustParticleOptions(TEAR, BEAD_SIZE);
        TrailParticleOption drop = new TrailParticleOption(new Vec3(x, y - DRIP_FALL, z), TEAR,
                DRIP_TICKS);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= DRIP_RANGE_SQUARED
                    && PolymerResourcePackUtils.hasMainPack(player.connection)) {
                level.sendParticles(player, bead, false, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(player, drop, false, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        return true;
    }

    // The drop that left this block a moment ago lands, about where it fell to.
    private static void land(ServerLevel level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() - DRIP_FALL + 0.5;
        double z = pos.getZ() + 0.5;
        ClientboundSoundPacket drip = new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                        SoundEvents.POINTED_DRIPSTONE_DRIP_WATER),
                SoundSource.BLOCKS, x, y, z, DRIP_VOLUME, DRIP_PITCH + random.nextFloat() * 0.2F,
                random.nextLong());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= DRIP_RANGE_SQUARED
                    && PolymerResourcePackUtils.hasMainPack(player.connection)) {
                player.connection.send(drip);
            }
        }
    }
}
