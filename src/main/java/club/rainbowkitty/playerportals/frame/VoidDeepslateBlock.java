package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block a frame is built from: deepslate with an ender pearl in it. A client with the
 * resource pack sees its own texture on a pool block; any other sees crying obsidian, which has
 * the same light, so neither client's own light recalculation disagrees with the server's.
 *
 * <p>It weeps as crying obsidian does. A client without the pack gets that from crying obsidian
 * itself; one with the pack is sent a teal drop now and then from an exposed face, by a block
 * tick each block keeps scheduling for itself (random ticks start it on blocks placed before this
 * was so), which costs nothing between drops. No vanilla drip particle takes a colour, so the
 * drop is vanilla's {@code trail}, which does, living an obsidian tear's life: it hangs on the
 * face five seconds, falls to whatever floor is below at a tear's pace, and lands with a soft
 * drip that crying obsidian's never make. The puddle it leaves is not a particle at all, since
 * every particle faces the camera: it is a text display's background, a teal rectangle lying
 * flat on the floor along the face the drop hung from, until the block's next drop forms.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    private static final int LIGHT = 10;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final int TEAR = 0x008888;
    // Ticks between a block's tries at a drop: 2 to 6 seconds, a third of which find a bare face.
    private static final int DRIP_DELAY_MIN = 40;
    private static final int DRIP_DELAY_SPAN = 80;
    // An obsidian tear's: it hangs 100 ticks, then falls under a gravity of 0.01 a tick.
    private static final int HANG_TICKS = 100;
    private static final double FALL_GRAVITY = 0.01;
    private static final int MAX_FALL = 16;
    // The puddle: a text display's background, which with no text is one text pixel wide and
    // ten tall, 0.025 block per pixel, standing on the entity's position and a half pixel off
    // its centre; scaled to the bar below, laid flat, and lifted clear of the floor.
    private static final double PUDDLE_LENGTH = 0.35;
    private static final double PUDDLE_WIDTH = 0.12;
    private static final float PUDDLE_PIXEL = 0.025F;
    private static final int PUDDLE_PIXELS_TALL = 10;
    private static final int PUDDLE_COLOUR = 0xB0008888;
    private static final double SPLASH_LIFT = 0.01;
    private static final double DRIP_RANGE_SQUARED = 32.0 * 32.0;
    private static final float DRIP_VOLUME = 0.3F;
    private static final float DRIP_PITCH = 0.7F;
    // Each block's drop while it has one. The next tick lets a hanging drop fall, and lands a
    // falling one. A restart empties this, which costs one drop its ending.
    private static final Long2ObjectMap<Drop> DROPS = new Long2ObjectOpenHashMap<>();
    // Each block's puddle while it has one, gone when its next drop forms or the block does.
    private static final Long2ObjectMap<ElementHolder> PUDDLES = new Long2ObjectOpenHashMap<>();

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
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        DROPS.remove(pos.asLong());
        dryPuddle(pos.asLong());
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
    // and schedules the next drop. A drop with no floor within reach fades in the air instead.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        long key = pos.asLong();
        Drop drop = DROPS.remove(key);
        if (drop == null) {
            dryPuddle(key);
            drop = hang(level, pos, random);
            if (drop == null) {
                scheduleDrip(level, pos, random);
                return;
            }
            DROPS.put(key, drop);
            level.scheduleTick(pos, this, HANG_TICKS);
        } else if (!drop.falling()) {
            send(level, drop.from(),
                    new TrailParticleOption(drop.landing(), TEAR, drop.fallTicks()));
            if (drop.lands()) {
                DROPS.put(key, drop.fall());
                level.scheduleTick(pos, this, drop.fallTicks());
            } else {
                scheduleDrip(level, pos, random);
            }
        } else {
            land(level, drop, random);
            PUDDLES.put(key, puddle(level, drop));
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
    // a trail that stays put for the hang. Null when the face drawn was covered or the top.
    private static @Nullable Drop hang(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction face = Direction.getRandom(random);
        if (face == Direction.UP) {
            return null;
        }
        BlockPos beside = pos.relative(face);
        if (level.getBlockState(beside).isFaceSturdy(level, beside, face.getOpposite())) {
            return null;
        }
        Vec3 from = new Vec3(
                pos.getX() + (face.getStepX() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepX() * 0.55),
                pos.getY() + (face.getStepY() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepY() * 0.55),
                pos.getZ() + (face.getStepZ() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepZ() * 0.55));
        send(level, from, new TrailParticleOption(from, TEAR, HANG_TICKS));
        return Drop.hanging(from, floorBelow(level, from), face);
    }

    // Just above the top of the first block with collision under `from`, within reach, else
    // null.
    private static @Nullable Vec3 floorBelow(ServerLevel level, Vec3 from) {
        BlockPos.MutableBlockPos below = BlockPos.containing(from).mutable();
        for (int i = 0; i < MAX_FALL; i++) {
            below.move(Direction.DOWN);
            VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) {
                return new Vec3(from.x, below.getY() + shape.max(Direction.Axis.Y) + SPLASH_LIFT,
                        from.z);
            }
        }
        return null;
    }

    private static void send(ServerLevel level, Vec3 at, ParticleOptions particle) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= DRIP_RANGE_SQUARED
                    && PolymerResourcePackUtils.hasMainPack(player.connection)) {
                level.sendParticles(player, particle, false, false, at.x, at.y, at.z, 1, 0.0,
                        0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * One block's drop, from where it hangs to where it lands.
     *
     * @param from where it hangs
     * @param landing the floor it falls to, or the air {@link #MAX_FALL} below when there is none
     * @param lands whether there was a floor
     * @param falling whether it has let go
     * @param face the face it hangs from
     */
    private record Drop(Vec3 from, Vec3 landing, boolean lands, boolean falling, Direction face) {
        // A drop that has just formed at `from` on `face`, over `floor` if any.
        static Drop hanging(Vec3 from, @Nullable Vec3 floor, Direction face) {
            return new Drop(from, floor != null ? floor : from.subtract(0.0, MAX_FALL, 0.0),
                    floor != null, false, face);
        }

        // The same drop, let go.
        Drop fall() {
            return new Drop(from, landing, lands, true, face);
        }

        // How long the fall takes under a tear's gravity, at least one tick.
        int fallTicks() {
            double height = from.y - landing.y;
            return Math.max(1, (int) Math.round(Math.sqrt(2.0 * height / FALL_GRAVITY)));
        }
    }

    // The puddle a drop leaves: a flat teal rectangle on the floor, its length along the face the
    // drop hung from (either way for one from under the block), shown to players with the pack.
    private static ElementHolder puddle(ServerLevel level, Drop drop) {
        Direction.Axis along = drop.face().getAxis() == Direction.Axis.X
                ? Direction.Axis.Z : Direction.Axis.X;
        float yaw = along == Direction.Axis.X ? 0.0F : (float) Math.toRadians(90.0);
        // The pitch lays the upright background flat, face up; the yaw turns it along the face.
        Quaternionf turn = new Quaternionf().rotationY(yaw).rotateX((float) Math.toRadians(-90.0));
        Vector3f scale = new Vector3f((float) PUDDLE_LENGTH / PUDDLE_PIXEL,
                (float) PUDDLE_WIDTH / (PUDDLE_PIXEL * PUDDLE_PIXELS_TALL), 1.0F);
        Vector3f centre = new Vector3f(-PUDDLE_PIXEL / 2.0F * scale.x,
                PUDDLE_PIXEL * PUDDLE_PIXELS_TALL / 2.0F * scale.y, 0.0F);
        TextDisplayElement sheet = new TextDisplayElement(Component.empty());
        sheet.setBackground(PUDDLE_COLOUR);
        sheet.setShadow(false);
        sheet.setBillboardMode(Display.BillboardConstraints.FIXED);
        sheet.setBrightness(Brightness.FULL_BRIGHT);
        sheet.setScale(scale);
        sheet.setLeftRotation(turn);
        sheet.setTranslation(turn.transform(centre).negate());
        sheet.setVisibilityPredicate(
                player -> PolymerResourcePackUtils.hasMainPack(player.connection));
        ElementHolder holder = new ElementHolder();
        holder.addElement(sheet);
        ChunkAttachment.of(holder, level, drop.landing());
        return holder;
    }

    private static void dryPuddle(long key) {
        ElementHolder puddle = PUDDLES.remove(key);
        if (puddle != null) {
            puddle.destroy();
        }
    }

    // The drop lands, with a soft drip.
    private static void land(ServerLevel level, Drop drop, RandomSource random) {
        Vec3 at = drop.landing();
        double x = at.x;
        double y = at.y;
        double z = at.z;
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
