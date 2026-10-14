package club.rainbowkitty.playerportals.frame;

import java.util.HashMap;
import java.util.Map;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The block a frame is built from: deepslate with an ender pearl in it, light 7. A client with
 * the resource pack sees its texture on a pool state ({@link FrameBlocks}) and holds that state's
 * item ({@link VoidDeepslateItem}); any other sees and holds crying obsidian. It weeps as crying
 * obsidian does: a pack client is shown a teal tear now and then, one text display per drop.
 * {@code docs/plan/player-portals.md} has the reasoning and the measurements.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    // Three levels under crying obsidian's 10.
    private static final int LIGHT = 7;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    // Puts the grey 213 of vanilla's three drip sprites at 125 of 255.
    private static final int TEAR = 0x009696;
    private static final Component HANG = tear("drip_hang");
    private static final Component FALL = tear("drip_fall");
    private static final Component PUDDLE = tear("drip_land");
    // Ticks between a block's tries at a drop: 2 to 6 seconds, a third of which find a bare face.
    private static final int DRIP_DELAY_MIN = 40;
    private static final int DRIP_DELAY_SPAN = 80;
    // An obsidian tear's: it hangs 100 ticks, then falls under a gravity of 0.01 a tick.
    private static final int HANG_TICKS = 100;
    private static final double FALL_GRAVITY = 0.01;
    // How far below where it hangs a drop looks for a floor.
    private static final int MAX_FALL = 16;
    // The widest dot is 0.1 across, so this far out no turn of its billboard cuts into the block.
    private static final double HANG_OUT = 0.08;
    // A one-glyph line's box spans x -3 to 5 and y 2 to 10 text pixels from the display; each mark
    // sits a pixel right, centred on its sprite rows: 1-2 hanging, 2-3 falling, 4 the bar.
    private static final float TEXT_PIXEL = 0.025F;
    private static final Vector3f HANG_CENTRE = new Vector3f(1.0F, 9.0F, 0.0F).mul(TEXT_PIXEL);
    private static final Vector3f FALL_CENTRE = new Vector3f(1.0F, 8.0F, 0.0F).mul(TEXT_PIXEL);
    private static final Vector3f PUDDLE_CENTRE = new Vector3f(1.0F, 6.5F, 0.0F)
            .mul(TEXT_PIXEL);
    private static final float DOT_HEIGHT = 2.0F * TEXT_PIXEL;
    private static final float BAR_HEIGHT = TEXT_PIXEL;
    // Text pixels per sprite pixel, as the tear's own 0.2 to 0.4 block quad.
    private static final float SCALE_MIN = 1.0F;
    private static final float SCALE_SPAN = 1.0F;
    // A centred mark reaches half its height below the display; this much more clears the floor.
    private static final double FLOOR_LIFT = 0.01;
    private static final double DRIP_RANGE_SQUARED = 32.0 * 32.0;
    private static final Holder<SoundEvent> DRIP_SOUND =
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.POINTED_DRIPSTONE_DRIP_WATER);
    private static final float DRIP_VOLUME = 0.3F;
    private static final float DRIP_PITCH = 0.7F;
    // Each level's drops by block, until the block's next try at a drop or the display goes.
    private static final Map<ResourceKey<Level>, Long2ObjectMap<Drop>> DROPS = new HashMap<>();

    private final BlockState textured;

    /**
     * @param key the block's id
     * @param textured the pool state the pack draws this block on, or null when the pool is
     *        spent, in which case every client sees the fallback
     */
    public VoidDeepslateBlock(ResourceKey<Block> key, @Nullable BlockState textured) {
        super(properties(key));
        this.textured = textured == null ? FALLBACK : textured;
    }

    /** The state a client with the pack is shown this block as. */
    public BlockState packState() {
        return textured;
    }

    /** Forgets every drop, for when the server stops. */
    public static void forgetDrops() {
        DROPS.clear();
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return context != null && PolymerResourcePackUtils.hasMainPack(context)
                ? textured : FALLBACK;
    }

    // Neither client state gives the server's light, and Polymer asks only the fallback's.
    @Override
    public boolean forceLightUpdates(BlockState state) {
        return true;
    }

    // Every client predicts its clicks on the vanilla block it sees, so a server swing would
    // double them; the catalyst asks for its own.
    @Override
    public boolean isPolymerBlockInteraction(BlockState state, ServerPlayer player,
            InteractionHand hand, ItemStack stack, ServerLevel level, BlockHitResult hit,
            InteractionResult result) {
        return false;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
            boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            scheduleDrip(serverLevel, pos, serverLevel.getRandom());
        }
    }

    // Starts the drips of a block placed before they existed; one already ticking keeps its tick.
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        scheduleDrip(level, pos, random);
    }

    // Three ticks per drop: it hangs, falls, then lands and schedules the block's next try, which
    // dries the puddle. A drop with no floor in reach, or over a liquid, is gone on landing.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Long2ObjectMap<Drop> drops = DROPS.computeIfAbsent(level.dimension(),
                added -> new Long2ObjectOpenHashMap<>());
        long key = pos.asLong();
        Drop drop = drops.get(key);
        if (drop != null && drop.phase == Phase.HANGING) {
            level.scheduleTick(pos, this, drop.fall());
        } else if (drop != null && drop.phase == Phase.FALLING) {
            if (drop.lands) {
                drop.settle();
                drip(level, drop.landing, random);
            } else {
                drop.destroy();
            }
            scheduleDrip(level, pos, random);
        } else {
            if (drop != null) {
                drop.destroy();
            }
            Drop formed = hang(level, pos, state, random);
            if (formed == null) {
                scheduleDrip(level, pos, random);
                return;
            }
            drops.put(key, formed);
            level.scheduleTick(pos, this, HANG_TICKS);
        }
    }

    // Deepslate's properties under key, glowing a little dimmer than crying obsidian.
    private static Properties properties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                .requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE)
                .lightLevel(state -> LIGHT).randomTicks().setId(key);
    }

    // The block's next try at a drop; vanilla keeps one pending tick per block and position, so a
    // random tick on a block already ticking starts no second chain.
    private void scheduleDrip(ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, DRIP_DELAY_MIN + random.nextInt(DRIP_DELAY_SPAN));
    }

    // A drop on a random exposed face other than the top, at a spot clear of collision, landing on
    // the first visual shape below or lost in a liquid; null when the face or spot drawn fails.
    private static @Nullable Drop hang(ServerLevel level, BlockPos pos, BlockState state,
            RandomSource random) {
        Direction face = Direction.getRandom(random);
        if (face == Direction.UP) {
            return null;
        }
        BlockPos beside = pos.relative(face);
        if (level.getBlockState(beside).isFaceSturdy(level, beside, face.getOpposite())) {
            return null;
        }
        Vec3 from = new Vec3(pos.getX() + onFace(face.getStepX(), random),
                pos.getY() + onFace(face.getStepY(), random),
                pos.getZ() + onFace(face.getStepZ(), random));
        float scale = SCALE_MIN + random.nextFloat() * SCALE_SPAN;
        double size = DOT_HEIGHT * scale;
        if (!level.noBlockCollision(null, AABB.ofSize(from, size, size, size))) {
            return null;
        }
        // A missed clip ends at its target, MAX_FALL below.
        BlockHitResult floor = level.clip(new ClipContext(from, from.subtract(0.0, MAX_FALL, 0.0),
                ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, CollisionContext.empty()));
        boolean lands = floor.getType() == HitResult.Type.BLOCK
                && level.getFluidState(floor.getBlockPos()).isEmpty();
        return new Drop(level, pos, state, from, new Vec3(from.x, floor.getLocation().y, from.z),
                lands, scale);
    }

    // One coordinate of a drop's spot, within the block: anywhere across the face, or HANG_OUT
    // off it along the face's own axis.
    private static double onFace(int step, RandomSource random) {
        return step == 0 ? random.nextDouble() : 0.5 + step * (0.5 + HANG_OUT);
    }

    // The soft drip of a drop landing at `at`, for players with the pack within earshot.
    private static void drip(ServerLevel level, Vec3 at, RandomSource random) {
        float pitch = DRIP_PITCH + random.nextFloat() * 0.2F;
        long seed = random.nextLong();
        ClientboundSoundPacket packet = null;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= DRIP_RANGE_SQUARED
                    && PolymerResourcePackUtils.hasMainPack(player.connection)) {
                if (packet == null) {
                    packet = new ClientboundSoundPacket(DRIP_SOUND, SoundSource.BLOCKS, at.x, at.y,
                            at.z, DRIP_VOLUME, pitch, seed);
                }
                player.connection.send(packet);
            }
        }
    }

    // One of vanilla's drip sprites as text, in the tear's colour.
    private static Component tear(String sprite) {
        return Component.object(new AtlasSprite(AtlasIds.PARTICLES,
                Identifier.withDefaultNamespace(sprite))).withColor(TEAR);
    }

    // Where a drop is in its life.
    private enum Phase {
        HANGING,
        FALLING,
        LYING
    }

    // One block's drop, a text display shown to pack players only: it hangs, falls and lies.
    private static final class Drop extends ElementHolder {
        private final TextDisplayElement sprite = new TextDisplayElement(HANG);
        private final ResourceKey<Level> dimension;
        private final long blockKey;
        private final Vec3 from;
        private final Vec3 landing;
        private final boolean lands;
        private final float scale;
        private Phase phase = Phase.HANGING;

        // Forms at `from`, bound to the block at `block`, to come down at `landing` and leave a
        // puddle there if it `lands`.
        private Drop(ServerLevel level, BlockPos block, BlockState state, Vec3 from, Vec3 landing,
                boolean lands, float scale) {
            this.dimension = level.dimension();
            this.blockKey = block.asLong();
            this.from = from;
            this.landing = landing;
            this.lands = lands;
            this.scale = scale;
            sprite.setBackground(0);
            sprite.setShadow(false);
            sprite.setBillboardMode(Display.BillboardConstraints.CENTER);
            // A tear's particles are all full bright, as is this.
            sprite.setBrightness(Brightness.FULL_BRIGHT);
            sprite.setScale(new Vector3f(scale));
            centre(HANG_CENTRE);
            addElement(sprite);
            // Polymer marks this constructor internal: its public factory serves only blocks that
            // keep a holder of their own.
            new BlockBoundAttachment(this, level.getChunkAt(block), state, block.immutable(),
                    from, false);
        }

        // Pack players only, so no update of a display they never had reaches anyone else.
        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            return PolymerResourcePackUtils.hasMainPack(player) && super.startWatching(player);
        }

        // Polymer takes the display with any change to its block, a strict /setblock included,
        // or with its chunk: the drop is gone from every watcher and forgotten.
        @Override
        protected void onAttachmentRemoved(HolderAttachment oldAttachment) {
            Long2ObjectMap<Drop> drops = DROPS.get(dimension);
            if (drops != null) {
                drops.remove(blockKey, this);
            }
            destroy();
        }

        // Lets go: the client glides the falling dot to the floor over a tear's fall from this
        // height, and the ticks that takes are returned.
        private int fall() {
            Vec3 rest = over(DOT_HEIGHT);
            double height = Math.max(0.0, from.y - rest.y);
            int ticks = Math.max(1, (int) Math.round(Math.sqrt(2.0 * height / FALL_GRAVITY)));
            sprite.setText(FALL);
            centre(FALL_CENTRE);
            sprite.setTeleportDuration(ticks);
            sprite.setOffset(rest.subtract(from));
            tick();
            phase = Phase.FALLING;
            return ticks;
        }

        // Lands: the dot becomes the puddle, snapped down to sit as close over the floor.
        private void settle() {
            sprite.setText(PUDDLE);
            centre(PUDDLE_CENTRE);
            sprite.setTeleportDuration(0);
            sprite.setOffset(over(BAR_HEIGHT).subtract(from));
            tick();
            phase = Phase.LYING;
        }

        // Brings the mark's centre, drawn off the entity's position, onto it. The translation is
        // applied after the billboard's turn, so it holds whichever way the sprite faces.
        private void centre(Vector3f mark) {
            sprite.setTranslation(new Vector3f(mark).mul(-scale));
        }

        // Where a mark `height` tall at scale one sits centred on the landing, its lower edge
        // FLOOR_LIFT clear of the floor.
        private Vec3 over(float height) {
            return landing.add(0.0, FLOOR_LIFT + height * scale / 2.0, 0.0);
        }
    }
}
