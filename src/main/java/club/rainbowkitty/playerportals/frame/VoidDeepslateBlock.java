package club.rainbowkitty.playerportals.frame;

import java.util.HashMap;
import java.util.Map;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The block a frame is built from: deepslate with an ender pearl in it, glowing a little dimmer
 * than crying obsidian. A client with the resource pack sees its own texture on a pool state that
 * sounds as deepslate but gives no light (see {@link FrameBlocks}), and holds the item as that
 * state's own (see {@link VoidDeepslateItem}). Any other client sees and holds crying obsidian,
 * three levels brighter. Neither client's own light is the server's, so Polymer resends the
 * server's light to both whenever the light around the block changes.
 *
 * <p>It weeps as crying obsidian does. A client without the pack gets that from crying obsidian
 * itself; one with the pack is shown a teal tear now and then from an exposed face, by a block
 * tick each block keeps scheduling for itself (random ticks start it on blocks placed before this
 * was so), which costs nothing between drops. No vanilla drip particle takes a colour, but a
 * sprite in text takes the text's, so each tear is one text display showing vanilla's three drip
 * sprites in turn, turned to the camera as the tear's particles are. It hangs on the face five
 * seconds, falls at a tear's pace to whatever floor is below, a glide the client animates on its
 * own, and lies there as the puddle a tear leaves, with a soft drip that crying obsidian's never
 * make, until the block's next try at a drop.
 */
public final class VoidDeepslateBlock extends Block implements PolymerTexturedBlock {
    // Three levels under crying obsidian's 10.
    private static final int LIGHT = 7;
    private static final BlockState FALLBACK = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    // The tear's three sprites are vanilla's own: a dot hanging, the dot falling and the bar it
    // leaves, each grey 213 with one white highlight. This tint puts the grey at 125 of 255.
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
    // The dot is at most a tenth of a block across, and hangs this far off the face, so however
    // it turns to the camera it never cuts into the block.
    private static final double HANG_OUT = 0.08;
    // A text display draws a one-glyph line with the glyph's box from three pixels left of its
    // position to five right, and from two above to ten, a pixel being 0.025 block. Each mark
    // lies a pixel right of the position and this far up: the hanging dot fills the sprite's top
    // two rows, the falling one the next two, the puddle's bar the fourth.
    private static final float TEXT_PIXEL = 0.025F;
    private static final Vector3f HANG_CENTRE = new Vector3f(1.0F, 9.0F, 0.0F).mul(TEXT_PIXEL);
    private static final Vector3f FALL_CENTRE = new Vector3f(1.0F, 8.0F, 0.0F).mul(TEXT_PIXEL);
    private static final Vector3f PUDDLE_CENTRE = new Vector3f(1.0F, 6.5F, 0.0F)
            .mul(TEXT_PIXEL);
    private static final float DOT_HEIGHT = 2.0F * TEXT_PIXEL;
    private static final float BAR_HEIGHT = TEXT_PIXEL;
    // The tear's own quad is 0.2 to 0.4 block, which is its sprite at one to two text pixels per
    // pixel: a dot 0.05 to 0.1 across, a bar 0.1 to 0.2 long. Each mark is centred on the entity,
    // so however the billboard turns no edge of it reaches further below the entity than half
    // its height; on the floor the entity sits that and this much above it.
    private static final float SCALE_MIN = 1.0F;
    private static final float SCALE_SPAN = 1.0F;
    private static final double FLOOR_LIFT = 0.01;
    private static final double DRIP_RANGE_SQUARED = 32.0 * 32.0;
    private static final float DRIP_VOLUME = 0.3F;
    private static final float DRIP_PITCH = 0.7F;
    // Each level's drops by block, from the tick one forms until the block's next try at a drop
    // or the block goes. Forgotten when the server stops; the displays go with their chunks.
    private static final Map<ResourceKey<Level>, Long2ObjectMap<Drop>> DROPS = new HashMap<>();

    private final BlockState textured;

    /**
     * @param textured the pool state the pack draws this block on, or null when the pool is
     *        spent, in which case every client sees the fallback
     */
    public VoidDeepslateBlock(Properties properties, @Nullable BlockState textured) {
        super(properties);
        this.textured = textured == null ? FALLBACK : textured;
    }

    /** Deepslate's properties under {@code key}, glowing a little dimmer than crying obsidian. */
    public static Properties properties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                .requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE)
                .lightLevel(state -> LIGHT).randomTicks().setId(key);
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

    // Polymer resends the server's light to clients near a block whose client state gives a
    // different light, as both of this block's do. It finds such a block by the state of a client
    // it knows nothing about, crying obsidian here; this says so whatever that state's light.
    @Override
    public boolean forceLightUpdates(BlockState state) {
        return true;
    }

    // Polymer swings from the server after any click on a block of its own that did something,
    // for clients that cannot predict it. Every client sees a vanilla block here and predicts its
    // own clicks; the catalyst, held as an item that predicts nothing, asks for its swing itself.
    @Override
    public boolean isPolymerBlockInteraction(BlockState state, ServerPlayer player,
            InteractionHand hand, ItemStack stack, ServerLevel level, BlockHitResult hit,
            InteractionResult result) {
        return false;
    }

    // A block gone mid-drop takes its drop or puddle with it, and leaves none for the next block
    // here to finish. A removal that skips this, such as a strict /setblock, still takes the
    // display, which is bound to the block, and the drop is forgotten at that spot's next tick.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        Long2ObjectMap<Drop> drops = DROPS.get(level.dimension());
        Drop drop = drops == null ? null : drops.remove(pos.asLong());
        if (drop != null) {
            drop.dry();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
            boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            scheduleDrip(serverLevel, pos, serverLevel.getRandom());
        }
    }

    // Starts the drips of a block placed before they existed; a block already ticking keeps the
    // tick it has.
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        scheduleDrip(level, pos, random);
    }

    // Three ticks per drop: one hangs it on a face, the next lets it fall, the last lands it and
    // schedules the block's next try, whose tick dries the puddle. A drop with no floor within
    // reach, or falling into a liquid, is gone when it would have landed.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Long2ObjectMap<Drop> drops = DROPS.computeIfAbsent(level.dimension(),
                added -> new Long2ObjectOpenHashMap<>());
        long key = pos.asLong();
        Drop drop = drops.get(key);
        // One whose display went with its chunk, or with a block here before, is forgotten.
        if (drop != null && drop.gone()) {
            drops.remove(key);
            drop = null;
        }
        if (drop != null && drop.phase() == Phase.HANGING) {
            level.scheduleTick(pos, this, drop.fall());
        } else if (drop != null && drop.phase() == Phase.FALLING) {
            if (drop.lands()) {
                drop.settle();
                drip(level, drop.landing(), random);
            } else {
                drops.remove(key);
                drop.dry();
            }
            scheduleDrip(level, pos, random);
        } else {
            if (drop != null) {
                drop.dry();
            }
            Drop formed = hang(level, pos, state, random);
            if (formed == null) {
                drops.remove(key);
                scheduleDrip(level, pos, random);
                return;
            }
            drops.put(key, formed);
            level.scheduleTick(pos, this, HANG_TICKS);
        }
    }

    // One pending tick per block: a random tick landing on a block already ticking must not
    // start a second chain.
    private void scheduleDrip(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, DRIP_DELAY_MIN + random.nextInt(DRIP_DELAY_SPAN));
        }
    }

    // A drop forms on a random exposed face other than the top, as crying obsidian picks them, a
    // tear's random size, and finds where it will come down: the first thing with a visual shape
    // below, within reach, a fence's post rather than its taller collision, or a liquid's surface,
    // in which it is lost. Null when the face drawn was covered or the top, or the spot drawn on
    // it is not clear of collision.
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
        double out = 0.5 + HANG_OUT;
        Vec3 from = new Vec3(
                pos.getX() + (face.getStepX() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepX() * out),
                pos.getY() + (face.getStepY() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepY() * out),
                pos.getZ() + (face.getStepZ() == 0 ? random.nextDouble()
                        : 0.5 + face.getStepZ() * out));
        float scale = SCALE_MIN + random.nextFloat() * SCALE_SPAN;
        double size = DOT_HEIGHT * scale;
        if (!level.noBlockCollision(null, AABB.ofSize(from, size, size, size))) {
            return null;
        }
        Vec3 reach = from.subtract(0.0, MAX_FALL, 0.0);
        BlockHitResult floor = level.clip(new ClipContext(from, reach, ClipContext.Block.VISUAL,
                ClipContext.Fluid.ANY, CollisionContext.empty()));
        if (floor.getType() != HitResult.Type.BLOCK) {
            return new Drop(level, pos, state, from, reach, false, scale);
        }
        return new Drop(level, pos, state, from, new Vec3(from.x, floor.getLocation().y, from.z),
                level.getFluidState(floor.getBlockPos()).isEmpty(), scale);
    }

    // The soft drip of a drop landing at `at`, for players with the pack within earshot.
    private static void drip(ServerLevel level, Vec3 at, RandomSource random) {
        ClientboundSoundPacket drip = new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                        SoundEvents.POINTED_DRIPSTONE_DRIP_WATER),
                SoundSource.BLOCKS, at.x, at.y, at.z, DRIP_VOLUME,
                DRIP_PITCH + random.nextFloat() * 0.2F, random.nextLong());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= DRIP_RANGE_SQUARED
                    && PolymerResourcePackUtils.hasMainPack(player.connection)) {
                player.connection.send(drip);
            }
        }
    }

    private static Component tear(String sprite) {
        return Component.object(new AtlasSprite(AtlasIds.PARTICLES,
                Identifier.withDefaultNamespace(sprite))).withColor(TEAR);
    }

    /** Where a drop is in its life. */
    private enum Phase {
        HANGING,
        FALLING,
        LYING
    }

    /**
     * One block's drop: a text display that hangs at its face, falls to the floor below and lies
     * there as a puddle, changing sprite at each step, for players with the pack. Its holder
     * never ticks; each step sends its own changes, and the client glides the fall. The holder is
     * bound to the block, so Polymer takes the display away whenever the block there changes,
     * however it changes.
     */
    private static final class Drop {
        private final ElementHolder holder = new ElementHolder();
        private final TextDisplayElement sprite = new TextDisplayElement(HANG);
        private final Vec3 from;
        private final Vec3 landing;
        private final boolean lands;
        private final float scale;
        private Phase phase = Phase.HANGING;

        // Forms at `from`, bound to the block at `block`, to come down at `landing` and leave a
        // puddle there if it `lands`.
        private Drop(ServerLevel level, BlockPos block, BlockState state, Vec3 from, Vec3 landing,
                boolean lands, float scale) {
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
            sprite.setVisibilityPredicate(
                    player -> PolymerResourcePackUtils.hasMainPack(player.connection));
            centre(HANG_CENTRE);
            holder.addElement(sprite);
            // Polymer marks this constructor internal: its public factory serves only blocks that
            // keep a holder of their own.
            new BlockBoundAttachment(holder, level.getChunkAt(block), state, block.immutable(),
                    from, false);
        }

        private Phase phase() {
            return phase;
        }

        private boolean lands() {
            return lands;
        }

        private Vec3 landing() {
            return landing;
        }

        // Whether its display is gone, with its chunk or with the block it was bound to.
        private boolean gone() {
            return holder.getAttachment() == null;
        }

        // Lets go: the client glides the falling dot to the floor over a tear's fall from this
        // height, and the ticks that takes are returned.
        private int fall() {
            double height = Math.max(0.0, from.y - over(DOT_HEIGHT).y);
            int ticks = Math.max(1, (int) Math.round(Math.sqrt(2.0 * height / FALL_GRAVITY)));
            sprite.setText(FALL);
            centre(FALL_CENTRE);
            sprite.setTeleportDuration(ticks);
            sprite.setOffset(over(DOT_HEIGHT).subtract(from));
            holder.tick();
            phase = Phase.FALLING;
            return ticks;
        }

        // Lands: the dot becomes the puddle, snapped down to sit as close over the floor.
        private void settle() {
            sprite.setText(PUDDLE);
            centre(PUDDLE_CENTRE);
            sprite.setTeleportDuration(0);
            sprite.setOffset(over(BAR_HEIGHT).subtract(from));
            holder.tick();
            phase = Phase.LYING;
        }

        private void dry() {
            holder.destroy();
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
