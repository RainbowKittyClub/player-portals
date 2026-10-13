package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * was so), which costs nothing between drops. No vanilla drip particle takes a colour, so the
 * drop is vanilla's {@code trail}, which does, wearing the pack's droplet sprite: it hangs on the
 * face, falls, and lands with a soft drip that crying obsidian's never make.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    private static final int LIGHT = 10;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final int TEAR = 0x008888;
    // Ticks between a block's tries at a drop: 2 to 6 seconds, a third of which find a bare face.
    private static final int DRIP_DELAY_MIN = 40;
    private static final int DRIP_DELAY_SPAN = 80;
    private static final int HANG_TICKS = 15;
    private static final int FALL_TICKS = 25;
    private static final double DRIP_FALL = 1.5;
    private static final double DRIP_RANGE_SQUARED = 32.0 * 32.0;
    private static final float DRIP_VOLUME = 0.3F;
    private static final float DRIP_PITCH = 0.7F;
    private static final byte HANGING = 1;
    private static final byte FALLING = 2;
    // Where each block's drop is, when it has one: hanging on the face or in the air. The next
    // tick lets it fall, or lands it. A restart empties this, which costs one drop its ending.
    private static final Long2ByteMap DROPS = new Long2ByteOpenHashMap();

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

    // Three ticks per drop: one hangs it on a face, the next lets it fall, the last lands it
    // and schedules the next drop.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        long key = pos.asLong();
        byte phase = DROPS.remove(key);
        if (phase == HANGING) {
            fall(level, pos, random);
            DROPS.put(key, FALLING);
            level.scheduleTick(pos, this, FALL_TICKS);
        } else if (phase == FALLING) {
            land(level, pos, random);
            scheduleDrip(level, pos, random);
        } else if (hang(level, pos, random)) {
            DROPS.put(key, HANGING);
            level.scheduleTick(pos, this, HANG_TICKS);
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

    // A drop forms on a random exposed face other than the top, as crying obsidian picks them:
    // a trail that stays put for the hang. False when the face drawn was covered or the top.
    private static boolean hang(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction face = Direction.getRandom(random);
        if (face == Direction.UP) {
            return false;
        }
        BlockPos beside = pos.relative(face);
        if (level.getBlockState(beside).isFaceSturdy(level, beside, face.getOpposite())) {
            return false;
        }
        Vec3 at = new Vec3(
                pos.getX() + (face.getStepX() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepX() * 0.55),
                pos.getY() + (face.getStepY() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepY() * 0.55),
                pos.getZ() + (face.getStepZ() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepZ() * 0.55));
        send(level, at, new TrailParticleOption(at, TEAR, HANG_TICKS));
        return true;
    }

    // The drop lets go and falls below the block; the face it hung on is not remembered, so it
    // falls from under the block's middle.
    private static void fall(ServerLevel level, BlockPos pos, RandomSource random) {
        Vec3 at = new Vec3(pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() - 0.05,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4);
        send(level, at, new TrailParticleOption(at.subtract(0.0, DRIP_FALL, 0.0), TEAR,
                FALL_TICKS));
    }

    private static void send(ServerLevel level, Vec3 at, TrailParticleOption drop) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= DRIP_RANGE_SQUARED
                    && PolymerResourcePackUtils.hasMainPack(player.connection)) {
                level.sendParticles(player, drop, false, false, at.x, at.y, at.z, 1, 0.0, 0.0,
                        0.0, 0.0);
            }
        }
    }

    // The drop lands, about where it fell to.
    private static void land(ServerLevel level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() - DRIP_FALL;
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
