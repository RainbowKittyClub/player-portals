package club.rainbowkitty.playerportals.frame;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
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
 * drip that crying obsidian's never make, leaving the puddle a tear leaves. That is vanilla's own
 * landing sprite, which no particle could show in this colour, as the one glyph of a text
 * display: a sprite in text takes the text's colour, and the display turns it to the camera as
 * the tear's own is turned, just above the floor where the drop fell, until the block's next drop
 * forms.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    private static final int LIGHT = 10;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    // The tear's colour is #008888, rendered at 125 of 255 by drop and puddle alike: the trail's
    // sprite is one white pixel, so the drop shows its tint as given, where the landing sprite's
    // bar is grey 213 (with one white highlight) and shows 213/255 of its tint. Each is tinted
    // for 125, halfway between where #008888 put the two (136 and 114).
    private static final int TEAR = 0x007D7D;
    private static final int PUDDLE_TINT = 0x009696;
    // Ticks between a block's tries at a drop: 2 to 6 seconds, a third of which find a bare face.
    private static final int DRIP_DELAY_MIN = 40;
    private static final int DRIP_DELAY_SPAN = 80;
    // An obsidian tear's: it hangs 100 ticks, then falls under a gravity of 0.01 a tick.
    private static final int HANG_TICKS = 100;
    private static final double FALL_GRAVITY = 0.01;
    private static final int MAX_FALL = 16;
    // The drop is a quarter-block quad facing the camera: it hangs just off the face, and keeps
    // this far above the floor it lands on, or the floor swallows half of it.
    private static final double HANG_OUT = 0.08;
    private static final double DROP_CLEARANCE = 0.14;
    // The puddle: the tear's landing sprite, a bar four pixels long and one wide in its eight by
    // eight, tinted. A text display draws a one-glyph line with the glyph's box from three pixels
    // left of its position to five right, and from two above to ten, a pixel being 0.025 block;
    // the bar is the box's fourth row, so its centre lies a pixel right and six and a half up.
    private static final Component PUDDLE = Component.object(new AtlasSprite(AtlasIds.PARTICLES,
            Identifier.withDefaultNamespace("drip_land"))).withColor(PUDDLE_TINT);
    private static final float TEXT_PIXEL = 0.025F;
    private static final Vector3f PUDDLE_BAR_CENTRE = new Vector3f(1.0F, 6.5F, 0.0F)
            .mul(TEXT_PIXEL);
    // The tear's own quad is 0.2 to 0.4 block, which is the sprite at one to two text pixels per
    // pixel: a bar 0.1 to 0.2 long. The bar is centred on the entity, so however the billboard
    // turns no edge of it reaches further below the entity than half the bar's thickness; the
    // entity sits that and this much above the floor.
    private static final float PUDDLE_SCALE_MIN = 1.0F;
    private static final float PUDDLE_SCALE_SPAN = 1.0F;
    private static final double PUDDLE_LIFT = 0.01;
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

    // A block gone mid-drop keeps no drop for the next block here to finish, and no puddle.
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
                    new TrailParticleOption(drop.rest(), TEAR, drop.fallTicks()));
            if (drop.lands()) {
                DROPS.put(key, drop.fall());
                level.scheduleTick(pos, this, drop.fallTicks());
            } else {
                scheduleDrip(level, pos, random);
            }
        } else {
            land(level, drop, random);
            PUDDLES.put(key, puddle(level, drop, random));
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
        double out = 0.5 + HANG_OUT;
        Vec3 from = new Vec3(
                pos.getX() + (face.getStepX() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepX() * out),
                pos.getY() + (face.getStepY() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepY() * out),
                pos.getZ() + (face.getStepZ() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepZ() * out));
        send(level, from, new TrailParticleOption(from, TEAR, HANG_TICKS));
        return Drop.hanging(from, floorBelow(level, from));
    }

    // The top of the first block with collision under `from`, within reach, else null.
    private static @Nullable Vec3 floorBelow(ServerLevel level, Vec3 from) {
        BlockPos.MutableBlockPos below = BlockPos.containing(from).mutable();
        for (int i = 0; i < MAX_FALL; i++) {
            below.move(Direction.DOWN);
            VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
            if (!shape.isEmpty()) {
                return new Vec3(from.x, below.getY() + shape.max(Direction.Axis.Y), from.z);
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
     */
    private record Drop(Vec3 from, Vec3 landing, boolean lands, boolean falling) {
        // A drop that has just formed at `from`, over `floor` if any.
        static Drop hanging(Vec3 from, @Nullable Vec3 floor) {
            return new Drop(from, floor != null ? floor : from.subtract(0.0, MAX_FALL, 0.0),
                    floor != null, false);
        }

        // The same drop, let go.
        Drop fall() {
            return new Drop(from, landing, lands, true);
        }

        // Where the falling dot comes to rest: its clearance above the floor.
        Vec3 rest() {
            return landing.add(0.0, DROP_CLEARANCE, 0.0);
        }

        // Where the centre of a puddle whose bar is `thickness` thick sits: over the floor where
        // the drop fell, high enough that the bar's lower edge clears it.
        Vec3 puddleAt(double thickness) {
            return landing.add(0.0, PUDDLE_LIFT + thickness / 2.0, 0.0);
        }

        // How long the fall takes under a tear's gravity, at least one tick.
        int fallTicks() {
            double height = from.y - rest().y;
            return Math.max(1, (int) Math.round(Math.sqrt(2.0 * height / FALL_GRAVITY)));
        }
    }

    // The puddle a drop leaves: the landing sprite turned to the camera as the tear's own is,
    // just above the floor where the drop fell, a tear's random size, shown to players with the
    // pack.
    private static ElementHolder puddle(ServerLevel level, Drop drop, RandomSource random) {
        float scale = PUDDLE_SCALE_MIN + random.nextFloat() * PUDDLE_SCALE_SPAN;
        TextDisplayElement sheet = new TextDisplayElement(PUDDLE);
        sheet.setBackground(0);
        sheet.setShadow(false);
        sheet.setBillboardMode(Display.BillboardConstraints.CENTER);
        // The drop's trail particle is always full bright; the puddle matches it.
        sheet.setBrightness(Brightness.FULL_BRIGHT);
        sheet.setScale(new Vector3f(scale));
        // Brings the bar's centre, drawn off the entity's position, onto it. The translation is
        // applied after the billboard's turn, so it holds whichever way the sprite faces.
        sheet.setTranslation(new Vector3f(PUDDLE_BAR_CENTRE).mul(-scale));
        sheet.setVisibilityPredicate(
                player -> PolymerResourcePackUtils.hasMainPack(player.connection));
        ElementHolder holder = new ElementHolder();
        holder.addElement(sheet);
        ChunkAttachment.of(holder, level, drop.puddleAt(TEXT_PIXEL * scale));
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
        Vec3 at = drop.rest();
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
